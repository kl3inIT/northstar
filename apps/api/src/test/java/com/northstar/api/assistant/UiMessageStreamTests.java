package com.northstar.api.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import tools.jackson.databind.ObjectMapper;

class UiMessageStreamTests {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void emitsVercelUiMessageFramesInOrder() {
        List<ServerSentEvent<String>> events = UiMessageStream.encode(
                        Flux.just(
                                new Part.StartStep(),
                                new Part.ToolInputStart("call-1", "today_tasks"),
                                new Part.ToolInputAvailable("call-1", "today_tasks", java.util.Map.of()),
                                new Part.ToolOutputAvailable("call-1", List.of("task")),
                                new Part.FinishStep(),
                                new Part.StartStep(),
                                new Part.TextStart("text-1"),
                                new Part.TextDelta("text-1", "Done"),
                                new Part.TextEnd("text-1"),
                                new Part.FinishStep()),
                        json, Duration.ofHours(1), Duration.ofMinutes(1))
                .collectList()
                .block();

        assertThat(events).isNotNull();
        List<String> data = events.stream().map(ServerSentEvent::data).toList();
        assertThat(data).hasSize(13);
        assertThat(data.getFirst()).contains("\"type\":\"start\"").contains("\"messageId\":");
        assertThat(data.subList(1, data.size())).containsExactly(
                "{\"type\":\"start-step\"}",
                "{\"type\":\"tool-input-start\",\"toolCallId\":\"call-1\",\"toolName\":\"today_tasks\"}",
                "{\"type\":\"tool-input-available\",\"toolCallId\":\"call-1\",\"toolName\":\"today_tasks\",\"input\":{}}",
                "{\"type\":\"tool-output-available\",\"toolCallId\":\"call-1\",\"output\":[\"task\"]}",
                "{\"type\":\"finish-step\"}",
                "{\"type\":\"start-step\"}",
                "{\"type\":\"text-start\",\"id\":\"text-1\"}",
                "{\"type\":\"text-delta\",\"id\":\"text-1\",\"delta\":\"Done\"}",
                "{\"type\":\"text-end\",\"id\":\"text-1\"}",
                "{\"type\":\"finish-step\"}",
                "{\"type\":\"finish\",\"finishReason\":\"stop\"}",
                "[DONE]");
    }

    @Test
    void heartbeatIsAnSseCommentAndNotAUiMessagePart() {
        StepVerifier.withVirtualTime(() -> UiMessageStream.encode(
                        Flux.never(), json, Duration.ofSeconds(15), Duration.ofMinutes(1)))
                .assertNext(event -> assertThat(event.data()).contains("\"type\":\"start\""))
                .thenAwait(Duration.ofSeconds(15))
                .assertNext(event -> {
                    assertThat(event.data()).isNull();
                    assertThat(event.comment()).isEqualTo("ping");
                })
                .thenCancel()
                .verify();
    }

    @Test
    void timeoutEmitsAbortAndDoneWithoutFinish() {
        Flux<ServerSentEvent<String>> dataEvents = UiMessageStream.encode(
                        Flux.<Part>just(new Part.StartStep()).concatWith(Flux.never()),
                        json, Duration.ofSeconds(5), Duration.ofSeconds(20))
                .filter(event -> event.data() != null);

        StepVerifier.withVirtualTime(() -> dataEvents)
                .assertNext(event -> assertThat(event.data()).contains("\"type\":\"start\""))
                .assertNext(event -> assertThat(event.data()).isEqualTo("{\"type\":\"start-step\"}"))
                .thenAwait(Duration.ofSeconds(20))
                .assertNext(event -> assertThat(event.data())
                        .isEqualTo("{\"type\":\"abort\",\"reason\":\"Assistant turn timed out.\"}"))
                .assertNext(event -> assertThat(event.data()).isEqualTo("[DONE]"))
                .verifyComplete();
    }

    @Test
    void failureEmitsSafeProtocolErrorAndDone() {
        List<String> data = UiMessageStream.encode(
                        Flux.error(new IllegalStateException("provider secret")),
                        json, Duration.ofHours(1), Duration.ofMinutes(1))
                .map(ServerSentEvent::data)
                .collectList()
                .block();

        assertThat(data).hasSize(3);
        assertThat(data.getFirst()).contains("\"type\":\"start\"");
        assertThat(data.get(1))
                .isEqualTo("{\"type\":\"error\",\"errorText\":\"The assistant stream failed.\"}")
                .doesNotContain("provider secret");
        assertThat(data.getLast()).isEqualTo("[DONE]");
    }
}
