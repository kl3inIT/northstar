package com.northstar.api.assistant;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import tools.jackson.databind.ObjectMapper;

/**
 * Encodes a {@link Part} flux as AI SDK UI Message Stream v1 frames over an
 * {@link SseEmitter}: start → text/tool parts → finish → [DONE]. Any stream
 * error still ends the response with a protocol-level error frame so the UI
 * shows a message instead of hanging.
 */
final class UiMessageStream {

    private UiMessageStream() {
    }

    static void pipe(Flux<Part> source, SseEmitter emitter, ObjectMapper json) {
        Encoder encoder = new Encoder(emitter, json);
        Disposable subscription = source.subscribe(
                encoder::write,
                _ -> {
                    encoder.error();
                    encoder.done();
                    emitter.complete();
                },
                () -> {
                    encoder.finish();
                    encoder.done();
                    emitter.complete();
                });
        emitter.onCompletion(subscription::dispose);
        emitter.onTimeout(() -> {
            // A long multi-tool turn ran past the emitter deadline. Don't leave the
            // UI hanging on a half-written message: surface a protocol error frame
            // (best-effort — the response may already be closing) then finish.
            try {
                encoder.error();
                encoder.done();
            } catch (RuntimeException ignored) {
                // response half-committed; nothing more we can send
            }
            subscription.dispose();
            emitter.complete();
        });
        emitter.onError(_ -> subscription.dispose());
    }

    private static final class Encoder {

        private final SseEmitter emitter;
        private final ObjectMapper json;
        private final String messageId = UUID.randomUUID().toString();
        private final AtomicBoolean started = new AtomicBoolean(false);

        private Encoder(SseEmitter emitter, ObjectMapper json) {
            this.emitter = emitter;
            this.json = json;
        }

        void write(Part part) {
            startIfNeeded();
            frame(json.writeValueAsString(payload(part)));
        }

        void finish() {
            startIfNeeded();
            frame(json.writeValueAsString(Map.of("type", "finish")));
        }

        void error() {
            startIfNeeded();
            frame(json.writeValueAsString(
                    fields("type", "error", "errorText", "The assistant stream failed.")));
        }

        void done() {
            frame("[DONE]");
        }

        private void startIfNeeded() {
            if (started.compareAndSet(false, true)) {
                frame(json.writeValueAsString(fields("type", "start", "messageId", messageId)));
            }
        }

        private void frame(String data) {
            try {
                emitter.send(SseEmitter.event().data(data));
            } catch (IOException e) {
                throw new IllegalStateException("could not send SSE frame", e);
            }
        }

        private static Map<String, Object> payload(Part part) {
            return switch (part) {
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
            };
        }

        private static Map<String, Object> fields(Object... keyValues) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (int i = 0; i < keyValues.length; i += 2) {
                map.put((String) keyValues[i], keyValues[i + 1]);
            }
            return map;
        }
    }
}
