package com.northstar.api.assistant;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.ObjectMapper;

/**
 * Decorates the default {@link ToolCallingManager} so each tool call surfaces
 * in the UI stream as it happens: the streaming turn drops a {@code Consumer<Part>}
 * into the tool context and this manager emits tool-input/tool-output parts
 * around the delegate's execution. Calls without that context key (capture,
 * alignment) pass straight through.
 */
@NullMarked
class EventEmittingToolManager implements ToolCallingManager {

    static final String EVENTS_KEY = "toolEvents";

    private final ToolCallingManager delegate;
    private final ObjectMapper json;

    EventEmittingToolManager(ToolCallingManager delegate, ObjectMapper json) {
        this.delegate = delegate;
        this.json = json;
    }

    @Override
    public List<ToolDefinition> resolveToolDefinitions(ToolCallingChatOptions chatOptions) {
        return delegate.resolveToolDefinitions(chatOptions);
    }

    @Override
    public ToolExecutionResult executeToolCalls(Prompt prompt, ChatResponse chatResponse) {
        Consumer<Part> emit = sink(prompt);

        var generation = chatResponse.getResult();
        List<AssistantMessage.ToolCall> calls = generation == null
                ? List.of()
                : generation.getOutput().getToolCalls();
        if (emit != null && generation != null) {
            for (AssistantMessage.ToolCall call : calls) {
                emit.accept(new Part.ToolInputStart(call.id(), call.name()));
                emit.accept(new Part.ToolInputAvailable(call.id(), call.name(), parse(call.arguments())));
            }
        }

        ToolExecutionResult result;
        try {
            result = delegate.executeToolCalls(prompt, chatResponse);
        } catch (RuntimeException failure) {
            if (emit != null) {
                for (AssistantMessage.ToolCall call : calls) {
                    emit.accept(new Part.ToolOutputError(call.id(), "Tool execution failed."));
                }
                emit.accept(new Part.FinishStep());
            }
            throw failure;
        }

        if (emit != null && !calls.isEmpty()) {
            List<Message> history = result.conversationHistory();
            if (!history.isEmpty() && history.getLast() instanceof ToolResponseMessage toolResponse) {
                for (ToolResponseMessage.ToolResponse r : toolResponse.getResponses()) {
                    Object output = parse(r.responseData());
                    emit.accept(new Part.ToolOutputAvailable(r.id(), output));
                    String toolName = calls.stream()
                            .filter(call -> call.id().equals(r.id()))
                            .map(AssistantMessage.ToolCall::name)
                            .findFirst()
                            .orElse("");
                    sources(toolName, r.id(), output).forEach(emit);
                }
            }
            // The model call and its server-side tool executions form one AI SDK
            // step. The advisor will call the model again with the tool results.
            emit.accept(new Part.FinishStep());
            emit.accept(new Part.StartStep());
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static @Nullable Consumer<Part> sink(Prompt prompt) {
        if (prompt.getOptions() instanceof ToolCallingChatOptions opts) {
            Map<String, Object> toolContext = opts.getToolContext();
            if (toolContext != null && toolContext.get(EVENTS_KEY) instanceof Consumer<?> consumer) {
                return (Consumer<Part>) consumer;
            }
        }
        return null;
    }

    /** Tool args/results are JSON strings; the UI wants objects — fall back to the raw string. */
    private @Nullable Object parse(@Nullable String json) {
        if (json == null || json.isBlank()) {
            return json;
        }
        try {
            return this.json.readValue(json, Object.class);
        } catch (RuntimeException e) {
            return json;
        }
    }

    static List<Part> sources(String toolName, String toolCallId, @Nullable Object output) {
        if ("search_knowledge".equals(toolName)) {
            List<?> results = output instanceof List<?> list
                    ? list
                    : output instanceof Map<?, ?> map && map.get("results") instanceof List<?> list
                            ? list
                            : List.of();
            return indexedMaps(results).stream()
                    .<Part>map(entry -> knowledgeSource(entry.value(), toolCallId + "-" + entry.index()))
                    .toList();
        }
        if (!(output instanceof Map<?, ?> result)) {
            return List.of();
        }
        if ("search_web".equals(toolName) && result.get("sources") instanceof List<?> sources) {
            java.util.concurrent.atomic.AtomicInteger index = new java.util.concurrent.atomic.AtomicInteger();
            return sources.stream()
                    .filter(Map.class::isInstance)
                    .map(Map.class::cast)
                    .<Part>map(source -> source(source, toolCallId + "-" + index.getAndIncrement()))
                    .filter(Objects::nonNull)
                    .toList();
        }
        if ("read_web_page".equals(toolName)) {
            String url = text(result.get("finalUrl"));
            if (!url.isBlank()) {
                return List.of(new Part.SourceUrl(toolCallId + "-0", url,
                        fallback(text(result.get("title")), url)));
            }
        }
        return List.of();
    }

    private static List<IndexedMap> indexedMaps(List<?> values) {
        java.util.concurrent.atomic.AtomicInteger index = new java.util.concurrent.atomic.AtomicInteger();
        return values.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(value -> new IndexedMap(index.getAndIncrement(), value))
                .toList();
    }

    private static Part.SourceDocument knowledgeSource(Map<?, ?> source, String fallbackId) {
        String url = text(source.get("url"));
        String sourceId = url.isBlank() ? fallbackId : url;
        String title = fallback(text(source.get("title")), sourceId);
        String kind = text(source.get("source"));
        String slug = text(source.get("slug"));
        String mediaType = "note".equals(kind) ? "text/markdown" : "application/octet-stream";
        String filename = "note".equals(kind) && !slug.isBlank() ? slug + ".md" : title;
        return new Part.SourceDocument(sourceId, mediaType, title, filename);
    }

    private static Part.@Nullable SourceUrl source(Map<?, ?> source, String sourceId) {
        String url = text(source.get("url"));
        if (url.isBlank()) {
            return null;
        }
        return new Part.SourceUrl(sourceId, url, fallback(text(source.get("title")), url));
    }

    private static String text(Object value) {
        return value instanceof String text ? text.strip() : "";
    }

    private static String fallback(String value, String fallback) {
        return value.isBlank() ? fallback : value;
    }

    private record IndexedMap(int index, Map<?, ?> value) {
    }
}
