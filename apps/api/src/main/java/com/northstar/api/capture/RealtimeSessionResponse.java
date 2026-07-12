package com.northstar.api.capture;

import jakarta.validation.constraints.NotNull;

/**
 * Ephemeral Realtime credential (ek_…, lives ~1 minute): enough to open one
 * WebSocket transcription session from the browser, useless afterwards.
 */
record RealtimeSessionResponse(@NotNull String clientSecret, @NotNull String websocketUrl) {
}
