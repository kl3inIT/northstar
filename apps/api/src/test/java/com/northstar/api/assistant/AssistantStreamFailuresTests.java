package com.northstar.api.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

class AssistantStreamFailuresTests {

    @Test
    void aModelThatCannotServeThisChatIsActionableWithoutQuotingTheProvider() {
        // The wording OpenAI returns for a reasoning tier asked to call tools on
        // /v1/chat/completions, as the streaming client wraps it.
        CompletionException failure = new CompletionException(new IllegalStateException(
                "400: Function tools with reasoning_effort are not supported for gpt-5.6-terra"
                        + " in /v1/chat/completions."));

        String description = AssistantStreamFailures.describe(failure);

        assertThat(description).isEqualTo("The selected model rejected this request. "
                + "Pick a different chat model and send it again.");
        assertThat(description).doesNotContain("reasoning_effort").doesNotContain("gpt-5.6-terra");
    }

    @Test
    void gatewayCredentialAndAvailabilityFailuresNameTheirOwnFix() {
        assertThat(AssistantStreamFailures.describe(new IllegalStateException("401: invalid api key")))
                .isEqualTo("The AI gateway rejected its credentials. Update its key in Settings.");
        assertThat(AssistantStreamFailures.describe(new IllegalStateException("404 Not Found: no model")))
                .isEqualTo("The selected model is no longer available on this gateway. Pick another model.");
        assertThat(AssistantStreamFailures.describe(new IllegalStateException("429: slow down")))
                .contains("rate limiting");
        assertThat(AssistantStreamFailures.describe(new IllegalStateException("503: upstream down")))
                .isEqualTo("The AI gateway failed while answering. Send the message again.");
    }

    @Test
    void failuresWithoutAGatewayStatusStayGeneric() {
        assertThat(AssistantStreamFailures.describe(new IllegalStateException("provider secret")))
                .isEqualTo(AssistantStreamFailures.GENERIC);
        // A status has to lead the message: a stray number inside prose is not one.
        assertThat(AssistantStreamFailures.describe(
                new IllegalStateException("indexed 404 notes for this turn")))
                .isEqualTo(AssistantStreamFailures.GENERIC);
        assertThat(AssistantStreamFailures.describe(new IllegalStateException()))
                .isEqualTo(AssistantStreamFailures.GENERIC);
    }

    @Test
    void aSelfReferencingCauseChainTerminates() {
        RuntimeException loop = new RuntimeException("no status here") {
            @Override
            public synchronized Throwable getCause() {
                return this;
            }
        };

        assertThat(AssistantStreamFailures.describe(loop)).isEqualTo(AssistantStreamFailures.GENERIC);
    }
}
