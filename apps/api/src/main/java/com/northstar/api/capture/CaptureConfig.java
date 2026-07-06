package com.northstar.api.capture;

import com.northstar.core.capture.CaptureService;
import com.northstar.core.capture.VoiceTranscriber;
import com.northstar.core.discipline.DisciplineService;
import com.northstar.core.note.NoteService;
import java.time.ZoneId;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the capture module for this app (the shared ChatClient bean lives in
 * AiConfig): core's CaptureService and VoiceTranscriber are deliberately not
 * components so apps without an LLM (mcp, worker) never try to build them.
 */
@Configuration(proxyBeanMethods = false)
class CaptureConfig {

    @Bean
    CaptureService captureService(ChatClient chatClient, NoteService notes,
            DisciplineService disciplines) {
        return new CaptureService(chatClient, notes, disciplines, ZoneId.systemDefault());
    }

    @Bean
    VoiceTranscriber voiceTranscriber(TranscriptionModel transcriptionModel) {
        return new VoiceTranscriber(transcriptionModel);
    }
}
