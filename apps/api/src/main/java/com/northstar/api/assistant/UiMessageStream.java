package com.northstar.api.assistant;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import tools.jackson.databind.ObjectMapper;

/**
 * Encodes assistant parts as AI SDK UI Message Stream v1 frames. Heartbeats are
 * SSE comments, so strict UI-message parsers ignore them while proxies still
 * see traffic during a quiet model or tool call.
 */
final class UiMessageStream {

    static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(15);

    private UiMessageStream() {
    }

    static Flux<ServerSentEvent<String>> encode(Flux<Part> source, ObjectMapper json,
            Duration turnTimeout) {
        return encode(source, json, HEARTBEAT_INTERVAL, turnTimeout);
    }

    static Flux<ServerSentEvent<String>> encode(Flux<Part> source, ObjectMapper json,
            Duration heartbeatInterval, Duration turnTimeout) {
        if (heartbeatInterval.isNegative() || heartbeatInterval.isZero()) {
            throw new IllegalArgumentException("heartbeatInterval must be positive");
        }
        if (turnTimeout.isNegative() || turnTimeout.isZero()) {
            throw new IllegalArgumentException("turnTimeout must be positive");
        }
        return Flux.defer(() -> {
            Encoder encoder = new Encoder(json);
            Flux<ServerSentEvent<String>> live = withHeartbeat(
                    limitDuration(source, turnTimeout).map(encoder::part),
                    heartbeatInterval);
            return Flux.concat(
                    Flux.just(encoder.start()),
                    live,
                    Flux.just(encoder.finish(), encoder.done()))
                    .onErrorResume(AssistantStreamAbortedException.class,
                            error -> Flux.just(encoder.abort(error.getMessage()), encoder.done()))
                    .onErrorResume(_ -> Flux.just(encoder.error(), encoder.done()));
        });
    }

    private static Flux<Part> limitDuration(Flux<Part> source, Duration timeout) {
        AtomicBoolean completed = new AtomicBoolean();
        return source
                .doOnComplete(() -> completed.set(true))
                .take(timeout)
                .concatWith(Flux.defer(() -> completed.get()
                        ? Flux.empty()
                        : Flux.error(new AssistantStreamAbortedException("Assistant turn timed out."))));
    }

    private static Flux<ServerSentEvent<String>> withHeartbeat(
            Flux<ServerSentEvent<String>> source, Duration interval) {
        return source.publish(shared -> {
            Flux<ServerSentEvent<String>> heartbeat = Flux.interval(interval)
                    .map(_ -> ServerSentEvent.<String>builder().comment("ping").build())
                    .takeUntilOther(shared.ignoreElements());
            return Flux.merge(shared, heartbeat);
        });
    }

    private static final class Encoder {

        private final ObjectMapper json;
        private final String messageId = UUID.randomUUID().toString();

        private Encoder(ObjectMapper json) {
            this.json = json;
        }

        ServerSentEvent<String> start() {
            return event(json.writeValueAsString(fields("type", "start", "messageId", messageId)));
        }

        ServerSentEvent<String> part(Part part) {
            return event(json.writeValueAsString(payload(part)));
        }

        ServerSentEvent<String> finish() {
            return event(json.writeValueAsString(fields(
                    "type", "finish", "finishReason", "stop")));
        }

        ServerSentEvent<String> error() {
            return event(json.writeValueAsString(
                    fields("type", "error", "errorText", "The assistant stream failed.")));
        }

        ServerSentEvent<String> abort(String reason) {
            return event(json.writeValueAsString(fields("type", "abort", "reason", reason)));
        }

        ServerSentEvent<String> done() {
            return event("[DONE]");
        }

        private static Map<String, Object> payload(Part part) {
            return switch (part) {
                case Part.StartStep _ -> fields("type", "start-step");
                case Part.FinishStep _ -> fields("type", "finish-step");
                case Part.TextStart p -> fields("type", "text-start", "id", p.id());
                case Part.TextDelta p -> fields("type", "text-delta", "id", p.id(),
                        "delta", p.delta() == null ? "" : p.delta());
                case Part.TextEnd p -> fields("type", "text-end", "id", p.id());
                case Part.ToolInputStart p -> fields("type", "tool-input-start",
                        "toolCallId", p.toolCallId(), "toolName", p.toolName());
                case Part.ToolInputAvailable p -> fields("type", "tool-input-available",
                        "toolCallId", p.toolCallId(), "toolName", p.toolName(), "input", p.input());
                case Part.ToolOutputAvailable p -> fields("type", "tool-output-available",
                        "toolCallId", p.toolCallId(), "output", p.output());
                case Part.ToolOutputError p -> fields("type", "tool-output-error",
                        "toolCallId", p.toolCallId(), "errorText", p.errorText());
                case Part.SourceUrl p -> fields("type", "source-url", "sourceId", p.sourceId(),
                        "url", p.url(), "title", p.title());
                case Part.SourceDocument p -> fields("type", "source-document", "sourceId", p.sourceId(),
                        "mediaType", p.mediaType(), "title", p.title(), "filename", p.filename());
            };
        }

        private static ServerSentEvent<String> event(String data) {
            return ServerSentEvent.builder(data).build();
        }

        private static Map<String, Object> fields(Object... keyValues) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (int i = 0; i < keyValues.length; i += 2) {
                map.put((String) keyValues[i], keyValues[i + 1]);
            }
            return map;
        }
    }

    private static final class AssistantStreamAbortedException extends RuntimeException {

        private AssistantStreamAbortedException(String message) {
            super(message);
        }
    }
}
