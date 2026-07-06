package com.northstar.api.assistant;

import com.northstar.core.assistant.NorthstarTool;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import tools.jackson.databind.ObjectMapper;

/**
 * The in-app assistant. One POST = one streamed turn: text deltas merge with
 * live tool-call events into an AI SDK UI Message Stream (v1) over SSE, which
 * the web app's useChat + ai-elements render directly. Memory is keyed by the
 * client-supplied conversation id; /history rehydrates the transcript so a
 * page reload does not show an empty chat.
 */
@RestController
@RequestMapping("/api/assistant")
class AssistantController {

    private static final String UI_MESSAGE_STREAM_HEADER = "x-vercel-ai-ui-message-stream";
    private static final String TEXT_PART_ID = "0";
    private static final String DEFAULT_CONVERSATION_ID = "default";
    private static final long TURN_TIMEOUT_MILLIS = 120_000L;

    // Follows the project prompt rubric: role, explicit output language, date
    // injection, tool guidance as behavior (not keyword lists), length anchor.
    private static final String SYSTEM_PROMPT = """
            You are Northstar's assistant. Northstar is the user's personal-growth OS: \
            a Markdown knowledge base with [[wiki links]], a task manager and a calendar \
            covering IELTS/HSK study, scholarship applications, projects and a journal.

            Today is %s (%s). Times you receive and mention are the user's local wall clock.

            <behavior>
            - Ground every answer about the user's plans, studies or knowledge in tool
              results — call the tools instead of guessing; search notes before claiming
              something is not written down.
            - Resolve relative dates ("tomorrow", "thứ 6") yourself before calling a tool.
            - Before booking an event, check the calendar for that day (upcoming_events
              or find_free_slots) and mention a conflict instead of double-booking.
            - After a write (task/note/event created, task completed), confirm in one
              short sentence what was created and when.
            - Answer in English; quote the user's task/note titles verbatim in whatever
              language they are in. Keep answers short: a few sentences, or a compact
              list when listing items. No headings.
            </behavior>""";

    private final ChatClient chat;
    private final ChatMemory memory;
    private final List<NorthstarTool> tools;
    private final ObjectMapper json;
    private final JdbcClient jdbc;

    AssistantController(@Qualifier(AssistantConfig.ASSISTANT_CHAT_CLIENT) ChatClient chat,
            ChatMemory memory, List<NorthstarTool> tools, ObjectMapper json, JdbcClient jdbc) {
        this.chat = chat;
        this.memory = memory;
        this.tools = tools;
        this.json = json;
        this.jdbc = jdbc;
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter chat(@Valid @RequestBody ChatRequest request, HttpServletResponse response) {
        response.setHeader(UI_MESSAGE_STREAM_HEADER, "v1");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform");

        SseEmitter emitter = new SseEmitter(TURN_TIMEOUT_MILLIS);
        UiMessageStream.pipe(streamTurn(request.message().strip(), conversationId(request.conversationId())),
                emitter, json);
        return emitter;
    }

    /** The stored transcript (user/assistant text only) for rehydrating the UI on load. */
    @GetMapping("/history")
    List<HistoryMessage> history(@RequestParam(required = false) String conversationId) {
        return memory.get(conversationId(conversationId)).stream()
                .flatMap(message -> switch (message) {
                    case UserMessage user -> textOf(user.getText(), "user");
                    case AssistantMessage assistant -> textOf(assistant.getText(), "assistant");
                    default -> java.util.stream.Stream.<HistoryMessage>empty();
                })
                .toList();
    }

    /**
     * All stored conversations, newest activity first, titled by their first
     * user message. Reads the memory table directly — ChatMemory's API has no
     * listing, and this is a read model of Spring AI's own schema, not domain.
     */
    @GetMapping("/conversations")
    List<ConversationSummary> conversations() {
        return jdbc.sql("""
                SELECT m.conversation_id,
                       (SELECT u.content FROM spring_ai_chat_memory u
                         WHERE u.conversation_id = m.conversation_id AND u.type = 'USER'
                         ORDER BY u.sequence_id LIMIT 1) AS title,
                       MAX(m."timestamp") AS last_at,
                       COUNT(*) FILTER (WHERE m.type IN ('USER', 'ASSISTANT')) AS messages
                  FROM spring_ai_chat_memory m
                 GROUP BY m.conversation_id
                 ORDER BY last_at DESC
                """)
                .query((rs, i) -> new ConversationSummary(
                        rs.getString("conversation_id"),
                        truncate(rs.getString("title")),
                        rs.getTimestamp("last_at").toInstant(),
                        rs.getLong("messages")))
                .list();
    }

    @DeleteMapping("/conversations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteConversation(@PathVariable String id) {
        memory.clear(id);
    }

    private static String truncate(String title) {
        if (title == null || title.isBlank()) {
            return "New conversation";
        }
        String stripped = title.strip();
        return stripped.length() <= 80 ? stripped : stripped.substring(0, 77) + "…";
    }

    private Flux<Part> streamTurn(String userMessage, String conversationId) {
        Sinks.Many<Part> toolEvents = Sinks.many().unicast().onBackpressureBuffer();
        Consumer<Part> emit = toolEvents::tryEmitNext;

        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        Flux<Part> deltas = chat.prompt()
                .system(SYSTEM_PROMPT.formatted(today,
                        today.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH)))
                .user(userMessage)
                .tools(tools.toArray())
                .toolContext(Map.of(EventEmittingToolManager.EVENTS_KEY, emit))
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .content()
                .<Part>map(token -> new Part.TextDelta(TEXT_PART_ID, token))
                .doFinally(signal -> toolEvents.tryEmitComplete());

        Flux<Part> text = Flux.concat(
                Flux.just(new Part.TextStart(TEXT_PART_ID)),
                deltas,
                Flux.just(new Part.TextEnd(TEXT_PART_ID)));
        return Flux.merge(text, toolEvents.asFlux());
    }

    private static String conversationId(String requested) {
        return StringUtils.hasText(requested) ? requested : DEFAULT_CONVERSATION_ID;
    }

    private static java.util.stream.Stream<HistoryMessage> textOf(String text, String role) {
        return text == null || text.isBlank()
                ? java.util.stream.Stream.empty()
                : java.util.stream.Stream.of(new HistoryMessage(role, text));
    }

    record ChatRequest(@NotBlank @Size(max = 20_000) String message, String conversationId) {
    }

    record HistoryMessage(String role, String text) {
    }

    record ConversationSummary(String id, String title, Instant lastAt, long messages) {
    }
}
