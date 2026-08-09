package com.northstar.integration.ai.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import reactor.core.publisher.Flux;

class ToolReasoningFallbackChatModelTests {

    /** The wording OpenAI returns for a reasoning tier asked to call tools. */
    private static final RuntimeException TOOLS_REJECTED = new CompletionException(
            new IllegalStateException("400: Function tools with reasoning_effort are not supported"
                    + " for gpt-5.6-terra in /v1/chat/completions. To use function tools, use"
                    + " /v1/responses or set reasoning_effort to 'none'."));

    @Test
    void streamRetriesTheRejectedCallWithReasoningDisabled() {
        RecordingChatModel delegate = new RecordingChatModel("gpt-5.6-terra", TOOLS_REJECTED, false);
        ToolReasoningFallbackChatModel model = new ToolReasoningFallbackChatModel(delegate);

        List<ChatResponse> responses = model.stream(prompt("gpt-5.6-terra")).collectList().block();

        assertEquals(1, responses.size());
        assertEquals("answered", responses.getFirst().getResult().getOutput().getText());
        assertEquals(2, delegate.attempts.size());
        assertNull(delegate.attempts.get(0).getReasoningEffort());
        assertEquals("none", delegate.attempts.get(1).getReasoningEffort());
        // The retry keeps the routed model and the gateway credential.
        assertEquals("gpt-5.6-terra", delegate.attempts.get(1).getModel());
        assertEquals("gateway-key", delegate.attempts.get(1).getApiKey());
    }

    @Test
    void aLearnedModelSpendsNoFailedCallOnTheNextTurn() {
        RecordingChatModel delegate = new RecordingChatModel("gpt-5.6-terra", TOOLS_REJECTED, false);
        ToolReasoningFallbackChatModel model = new ToolReasoningFallbackChatModel(delegate);

        model.stream(prompt("gpt-5.6-terra")).collectList().block();
        model.stream(prompt("gpt-5.6-terra")).collectList().block();

        assertEquals(3, delegate.attempts.size());
        assertEquals("none", delegate.attempts.get(2).getReasoningEffort());
    }

    @Test
    void anotherModelOnTheSameGatewayIsUnaffected() {
        RecordingChatModel delegate = new RecordingChatModel("gpt-5.6-terra", TOOLS_REJECTED, false);
        ToolReasoningFallbackChatModel model = new ToolReasoningFallbackChatModel(delegate);

        model.stream(prompt("gpt-5.6-terra")).collectList().block();
        model.stream(prompt("gpt-5.6-luna")).collectList().block();

        assertEquals(3, delegate.attempts.size());
        assertNull(delegate.attempts.get(2).getReasoningEffort());
    }

    @Test
    void unrelatedGatewayFailuresPropagateWithoutARetry() {
        RecordingChatModel delegate = new RecordingChatModel(
                "gpt-5.6-terra", new IllegalStateException("429: rate limited"), false);
        ToolReasoningFallbackChatModel model = new ToolReasoningFallbackChatModel(delegate);

        assertThrows(IllegalStateException.class,
                () -> model.stream(prompt("gpt-5.6-terra")).collectList().block());
        assertEquals(1, delegate.attempts.size());
    }

    @Test
    void aFailureAfterOutputIsNotReplayed() {
        RecordingChatModel delegate = new RecordingChatModel("gpt-5.6-terra", TOOLS_REJECTED, true);
        ToolReasoningFallbackChatModel model = new ToolReasoningFallbackChatModel(delegate);

        assertThrows(CompletionException.class,
                () -> model.stream(prompt("gpt-5.6-terra")).collectList().block());
        assertEquals(1, delegate.attempts.size());
    }

    @Test
    void blockingCallsRetryTheSameWay() {
        RecordingChatModel delegate = new RecordingChatModel("gpt-5.6-terra", TOOLS_REJECTED, false);
        ToolReasoningFallbackChatModel model = new ToolReasoningFallbackChatModel(delegate);

        ChatResponse response = model.call(prompt("gpt-5.6-terra"));

        assertEquals("answered", response.getResult().getOutput().getText());
        assertEquals(2, delegate.attempts.size());
        assertEquals("none", delegate.attempts.get(1).getReasoningEffort());
    }

    @Test
    void providerWordingIsMatchedOnNestedCauses() {
        assertTrue(ToolReasoningFallbackChatModel.rejectsToolsWhileReasoning(
                new RuntimeException("wrapped", TOOLS_REJECTED)));
        assertFalse(ToolReasoningFallbackChatModel.rejectsToolsWhileReasoning(
                new RuntimeException("400: unsupported parameter: temperature")));
    }

    private static Prompt prompt(String modelId) {
        return new Prompt(List.of(new UserMessage("what is on today?")),
                OpenAiChatOptions.builder()
                        .baseUrl("https://api.openai.com/v1")
                        .apiKey("gateway-key")
                        .model(modelId)
                        .build());
    }

    /**
     * One model of the gateway fails while reasoning is left at the provider
     * default and answers once the caller disables it — the observable contract
     * of a reasoning tier asked to call tools on the chat-completions transport.
     * Every other model answers either way.
     */
    private static final class RecordingChatModel implements ChatModel {

        private final List<OpenAiChatOptions> attempts = new ArrayList<>();
        private final String rejectingModel;
        private final RuntimeException rejection;
        private final boolean emitBeforeFailing;

        private RecordingChatModel(String rejectingModel, RuntimeException rejection,
                boolean emitBeforeFailing) {
            this.rejectingModel = rejectingModel;
            this.rejection = rejection;
            this.emitBeforeFailing = emitBeforeFailing;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            OpenAiChatOptions options = (OpenAiChatOptions) prompt.getOptions();
            attempts.add(options);
            if (rejects(options)) {
                throw rejection;
            }
            return answer();
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.defer(() -> {
                OpenAiChatOptions options = (OpenAiChatOptions) prompt.getOptions();
                attempts.add(options);
                if (!rejects(options)) {
                    return Flux.just(answer());
                }
                return emitBeforeFailing
                        ? Flux.concat(Flux.just(answer()), Flux.error(rejection))
                        : Flux.error(rejection);
            });
        }

        private boolean rejects(OpenAiChatOptions options) {
            return rejectingModel.equals(options.getModel())
                    && !ToolReasoningFallbackChatModel.NO_REASONING.equals(options.getReasoningEffort());
        }

        private static ChatResponse answer() {
            return new ChatResponse(List.of(new Generation(new AssistantMessage("answered"))));
        }
    }
}
