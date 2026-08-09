package com.northstar.integration.ai.openai;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import reactor.core.publisher.Flux;

/**
 * Keeps tool-calling turns alive on models that refuse to reason and call
 * functions in the same request.
 *
 * <p>Spring AI's OpenAI model speaks one transport, {@code /v1/chat/completions}.
 * OpenAI's reasoning tiers reject function tools there unless
 * {@code reasoning_effort} is {@code none}, and every Northstar Assistant turn
 * attaches tools — so selecting such a model in Chat failed the turn with a raw
 * provider 400. This decorator retries the one rejected call with reasoning
 * turned off and remembers the model, so later turns pay no failed round trip.
 *
 * <p>The retry sits at the model rather than the {@code ChatClient} on purpose:
 * {@code MessageChatMemoryAdvisor} writes the user message to chat memory before
 * the model is called and the tool-calling loop runs above the model, so
 * retrying higher up would duplicate that memory entry and could re-run tool
 * calls whose side effects already committed.
 */
class ToolReasoningFallbackChatModel implements ChatModel {

    static final String NO_REASONING = "none";

    private static final int MAX_CAUSE_DEPTH = 10;

    private final ChatModel delegate;
    /** Models observed to reject tools while reasoning, keyed by model id. */
    private final Set<String> reasoningIncompatible = ConcurrentHashMap.newKeySet();

    ToolReasoningFallbackChatModel(ChatModel delegate) {
        this.delegate = delegate;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        Prompt attempt = prepared(prompt);
        try {
            return delegate.call(attempt);
        } catch (RuntimeException rejection) {
            Prompt retry = fallback(attempt, rejection);
            if (retry == null) {
                throw rejection;
            }
            return delegate.call(retry);
        }
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return Flux.defer(() -> {
            Prompt attempt = prepared(prompt);
            AtomicBoolean started = new AtomicBoolean();
            return delegate.stream(attempt)
                    .doOnNext(_ -> started.set(true))
                    .onErrorResume(rejection -> {
                        // A rejected request emits nothing. Once any response or
                        // tool call exists, replaying the call would duplicate it.
                        Prompt retry = started.get() ? null : fallback(attempt, rejection);
                        return retry == null ? Flux.error(rejection) : delegate.stream(retry);
                    });
        });
    }

    @Override
    public ChatOptions getOptions() {
        return delegate.getOptions();
    }

    /** Applies what an earlier rejection taught us before spending a call. */
    private Prompt prepared(Prompt prompt) {
        if (!(prompt.getOptions() instanceof OpenAiChatOptions options)) {
            return prompt;
        }
        String model = options.getModel();
        // An explicit effort is a caller's choice; attempt it as asked and let
        // the provider, not this decorator, decide it cannot be served.
        if (model == null || !reasoningIncompatible.contains(model)
                || options.getReasoningEffort() != null) {
            return prompt;
        }
        return withoutReasoning(prompt, options);
    }

    private Prompt fallback(Prompt attempt, Throwable rejection) {
        if (!(attempt.getOptions() instanceof OpenAiChatOptions options)
                || NO_REASONING.equalsIgnoreCase(options.getReasoningEffort())
                || !rejectsToolsWhileReasoning(rejection)) {
            return null;
        }
        String model = options.getModel();
        if (model != null) {
            reasoningIncompatible.add(model);
        }
        return withoutReasoning(attempt, options);
    }

    private static Prompt withoutReasoning(Prompt prompt, OpenAiChatOptions options) {
        return prompt.mutate()
                .chatOptions(options.mutate().reasoningEffort(NO_REASONING).build())
                .build();
    }

    /**
     * Matches the provider's own wording instead of a model-name allowlist: the
     * gateway catalogs are runtime data, so a list of reasoning tiers would go
     * stale the next time a provider ships a model.
     */
    static boolean rejectsToolsWhileReasoning(Throwable error) {
        Throwable cause = error;
        for (int depth = 0; cause != null && depth < MAX_CAUSE_DEPTH; depth++) {
            String message = cause.getMessage();
            if (message != null) {
                String lower = message.toLowerCase(Locale.ROOT);
                if (lower.contains("reasoning_effort") && lower.contains("tool")) {
                    return true;
                }
            }
            cause = cause.getCause() == cause ? null : cause.getCause();
        }
        return false;
    }
}
