package com.northstar.api.assistant;

import java.util.List;
import java.util.Map;
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
                    emit.accept(new Part.ToolOutputAvailable(r.id(), parse(r.responseData())));
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
    private static Consumer<Part> sink(Prompt prompt) {
        if (prompt.getOptions() instanceof ToolCallingChatOptions opts) {
            Map<String, Object> toolContext = opts.getToolContext();
            if (toolContext != null && toolContext.get(EVENTS_KEY) instanceof Consumer<?> consumer) {
                return (Consumer<Part>) consumer;
            }
        }
        return null;
    }

    /** Tool args/results are JSON strings; the UI wants objects — fall back to the raw string. */
    private Object parse(String json) {
        if (json == null || json.isBlank()) {
            return json;
        }
        try {
            return this.json.readValue(json, Object.class);
        } catch (RuntimeException e) {
            return json;
        }
    }
}
