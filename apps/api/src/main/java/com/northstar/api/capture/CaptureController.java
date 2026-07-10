package com.northstar.api.capture;

import com.northstar.core.capture.CaptureDraft;
import com.northstar.core.capture.CaptureService;
import com.northstar.core.capture.VoiceTranscriber;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import java.io.IOException;
import java.util.Locale;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST delivery for AI capture. Interactive by design: the user pastes text (or
 * speaks) and waits for the result, so the LLM/transcription call runs on this
 * (virtual) request thread — the "no LLM on api threads" rule is for background
 * jobs, not for a response the user is actively waiting on.
 */
@RestController
@RequestMapping("/api/capture")
class CaptureController {

    private final CaptureService capture;
    private final VoiceTranscriber transcriber;

    CaptureController(CaptureService capture, VoiceTranscriber transcriber) {
        this.capture = capture;
        this.transcriber = transcriber;
    }

    @PostMapping("/draft")
    @Operation(operationId = "draftCapture")
    CaptureDraft draft(@Valid @RequestBody CaptureRequest request) {
        return capture.draft(request.text().strip(), request.kind());
    }

    /** Receipt capture: photo in, expense draft out — the image is never stored. */
    @PostMapping(value = "/receipt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(operationId = "draftReceiptCapture")
    CaptureDraft receipt(@RequestPart("image") MultipartFile image) {
        String mimeType = image.getContentType();
        if (mimeType == null || !mimeType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new IllegalArgumentException("Upload must be an image, got '" + mimeType + "'");
        }
        byte[] bytes;
        try {
            bytes = image.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read the uploaded image", e);
        }
        return capture.draftFromImage(bytes, mimeType);
    }

    /** Voice capture: audio in, text out — the recording is never stored. */
    @PostMapping(value = "/transcribe", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(operationId = "transcribeCaptureAudio")
    TranscriptionResponse transcribe(@RequestPart("audio") MultipartFile audio) {
        byte[] bytes;
        try {
            bytes = audio.getBytes();
        } catch (IOException e) {
            // A truncated/unreadable upload is the client's problem — 400, not 500.
            throw new IllegalArgumentException("Could not read the uploaded audio", e);
        }
        String filename = audio.getOriginalFilename();
        return new TranscriptionResponse(transcriber.transcribe(bytes,
                filename == null || filename.isBlank() ? "capture.webm" : filename));
    }
}
