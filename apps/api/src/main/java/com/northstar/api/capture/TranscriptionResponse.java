package com.northstar.api.capture;

import jakarta.validation.constraints.NotNull;

/** Voice capture result: what the user said, ready for the composer to review. */
record TranscriptionResponse(@NotNull String text) {
}
