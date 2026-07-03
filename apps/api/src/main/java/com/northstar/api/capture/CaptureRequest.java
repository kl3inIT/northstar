package com.northstar.api.capture;

import com.northstar.core.capture.CaptureDraft;

/**
 * Raw captured text to classify and shape. {@code kind} is optional: when the
 * user pressed "Thêm task"/"Thêm note" the classification is forced and the AI
 * only shapes the draft.
 */
record CaptureRequest(String text, CaptureDraft.Kind kind) {
}
