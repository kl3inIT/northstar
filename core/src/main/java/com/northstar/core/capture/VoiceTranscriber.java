package com.northstar.core.capture;

import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.core.io.ByteArrayResource;

/**
 * Voice capture's audio→text step, memos-style dictation-first: transcription
 * runs server-side, the user reviews the text in the composer before drafting,
 * and the recording itself is never stored.
 *
 * <p>Plain class like {@link CaptureService}: the delivering app provides the
 * {@link TranscriptionModel} bean (OpenAI Whisper via the api app), so apps
 * without an LLM never instantiate it and the provider stays swappable.
 */
public class VoiceTranscriber {

    private final TranscriptionModel model;

    public VoiceTranscriber(TranscriptionModel model) {
        this.model = model;
    }

    /** The filename's extension tells the provider the container format (webm/wav/…). */
    public String transcribe(byte[] audio, String filename) {
        ByteArrayResource resource = new ByteArrayResource(audio) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        return model.transcribe(resource);
    }
}
