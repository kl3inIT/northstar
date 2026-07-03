package com.northstar.api.capture;

import com.northstar.core.capture.CaptureDraft;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Raw captured text to classify and shape. {@code kind} is optional: when the
 * user pressed "Thêm task"/"Thêm note" the classification is forced and the AI
 * only shapes the draft.
 */
record CaptureRequest(@NotBlank @Size(max = 20_000) String text, CaptureDraft.Kind kind) {
}
