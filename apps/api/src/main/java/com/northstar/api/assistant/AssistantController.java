package com.northstar.api.assistant;

import com.northstar.core.assistant.MemoryTools;
import com.northstar.core.assistant.NorthstarTool;
import com.northstar.core.attachment.AttachmentContent;
import com.northstar.core.attachment.AttachmentService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.function.Consumer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.util.MimeTypeUtils;
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
            - When an answer draws on search_knowledge results, cite each source used
              inline as a markdown link — [title](url) with the hit's url — so the user
              can open the note or file you are quoting.
            - Resolve relative dates ("tomorrow", "thứ 6") yourself before calling a tool.
            - Before booking an event, check the calendar for that day (upcoming_events
              or find_free_slots) and mention a conflict instead of double-booking.
            - After a write (created, updated, completed), confirm in one short sentence
              what changed and when.
            - Before a delete/cancel/archive: resolve exactly ONE target with a read tool
              first; if several items match what the user said, list them and ask which —
              never guess, never delete more than the user named. Prefer archiving a note
              over deleting it, and marking a task done over deleting it.
            - Answer in English; quote the user's task/note titles verbatim in whatever
              language they are in. Keep answers short: a few sentences, or a compact
              list when listing items. No headings.
            </behavior>""";

    private final ChatClient chat;
    private final ChatMemory memory;
    private final List<NorthstarTool> tools;
    private final ObjectMapper json;
    private final JdbcClient jdbc;
    private final AttachmentService attachments;
    private final MemoryTools longTermMemory;

    AssistantController(@Qualifier(AssistantConfig.ASSISTANT_CHAT_CLIENT) ChatClient chat,
            ChatMemory memory, List<NorthstarTool> tools, ObjectMapper json, JdbcClient jdbc,
            AttachmentService attachments, MemoryTools longTermMemory) {
        this.chat = chat;
        this.memory = memory;
        this.tools = tools;
        this.json = json;
        this.jdbc = jdbc;
        this.attachments = attachments;
        this.longTermMemory = longTermMemory;
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter chat(@Valid @RequestBody ChatRequest request, HttpServletResponse response) {
        response.setHeader(UI_MESSAGE_STREAM_HEADER, "v1");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform");

        List<AttachmentContent> images = request.attachmentIds() == null ? List.of()
                : request.attachmentIds().stream().map(this::imageById).toList();
        String message = request.message() == null ? "" : request.message().strip();
        if (message.isBlank() && images.isEmpty()) {
            throw new IllegalArgumentException("Send a message, an image, or both.");
        }
        // Images ride into memory as markdown links to their stored file, so
        // /history re-renders them on reload (the bytes live in attachment, V16).
        // Fixed alt text — an attacker-chosen filename must not be able to close
        // the bracket and inject its own markdown/links into the transcript.
        String markers = images.stream()
                .map(a -> "![image](/api/files/%s)".formatted(a.meta().id()))
                .collect(Collectors.joining("\n"));
        String stored = message.isBlank() ? markers
                : markers.isBlank() ? message : message + "\n\n" + markers;

        SseEmitter emitter = new SseEmitter(TURN_TIMEOUT_MILLIS);
        UiMessageStream.pipe(streamTurn(stored, toMedia(images), conversationId(request.conversationId())),
                emitter, json);
        return emitter;
    }

    /**
     * Uploaded attachment → the model's eyes. Images only: the OpenAI adapter
     * sends byte[] image media as a data-URL content part the model can see;
     * other mime types silently degrade to base64 text, so reject them.
     */
    private AttachmentContent imageById(UUID id) {
        AttachmentContent content = attachments.load(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown attachment " + id + " — upload it to /api/files first"));
        if (!content.meta().mimeType().toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new IllegalArgumentException(
                    "Only image attachments can be sent to chat (got " + content.meta().mimeType() + ")");
        }
        return content;
    }

    private static List<Media> toMedia(List<AttachmentContent> images) {
        return images.stream()
                .map(a -> Media.builder()
                        .mimeType(MimeTypeUtils.parseMimeType(a.meta().mimeType()))
                        .data(a.data())
                        .build())
                .toList();
    }

    /** The stored transcript (user/assistant text only) for rehydrating the UI on load. */
    @GetMapping("/history")
    List<HistoryMessage> history(@RequestParam(required = false) String conversationId) {
        return memory.get(conversationId(conversationId)).stream()
                .flatMap(message -> switch (message) {
                    case UserMessage user -> textOf(user.getText(), "user");
                    case AssistantMessage assistant -> textOf(assistant.getText(), "assistant");
                    default -> Stream.empty();
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
                .query((rs, _) -> new ConversationSummary(
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

    private Flux<Part> streamTurn(String userMessage, List<Media> images, String conversationId) {
        Sinks.Many<Part> toolEvents = Sinks.many().unicast().onBackpressureBuffer();
        Consumer<Part> emit = toolEvents::tryEmitNext;

        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        Flux<Part> deltas = chat.prompt()
                // Memory rules + the LIVE index ride the system prompt every
                // turn, so the model knows what it remembers without a tool call.
                .system(SYSTEM_PROMPT.formatted(today,
                        today.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH))
                        + "\n\n" + longTermMemory.promptSection())
                // Never blank: an image-only turn's text is its markdown markers.
                .user(u -> u.text(userMessage).media(images.toArray(Media[]::new)))
                .tools(tools.toArray())
                .toolContext(Map.of(EventEmittingToolManager.EVENTS_KEY, emit))
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .content()
                .<Part>map(token -> new Part.TextDelta(TEXT_PART_ID, token))
                .doFinally(_ -> toolEvents.tryEmitComplete());

        Flux<Part> text = Flux.concat(
                Flux.just(new Part.TextStart(TEXT_PART_ID)),
                deltas,
                Flux.just(new Part.TextEnd(TEXT_PART_ID)));
        return Flux.merge(text, toolEvents.asFlux());
    }

    private static String conversationId(String requested) {
        return StringUtils.hasText(requested) ? requested : DEFAULT_CONVERSATION_ID;
    }

    private static Stream<HistoryMessage> textOf(String text, String role) {
        return text == null || text.isBlank()
                ? Stream.empty()
                : Stream.of(new HistoryMessage(role, text));
    }

    record ChatRequest(@Size(max = 20_000) String message, String conversationId,
            @Size(max = 3) List<UUID> attachmentIds) {
    }

    record HistoryMessage(String role, String text) {
    }

    record ConversationSummary(String id, String title, Instant lastAt, long messages) {
    }
}
