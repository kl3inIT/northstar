package com.northstar.api.capture;

import com.northstar.core.capture.CaptureService;
import com.northstar.core.discipline.DisciplineService;
import com.northstar.core.note.NoteService;
import java.time.ZoneId;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the capture module for this app: the OpenAI starter autoconfigures the
 * {@link ChatClient.Builder}; core's CaptureService is deliberately not a
 * component so apps without an LLM (mcp, worker) never try to build it.
 */
@Configuration(proxyBeanMethods = false)
class CaptureConfig {

    @Bean
    ChatClient captureChatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    @Bean
    CaptureService captureService(ChatClient captureChatClient, NoteService notes,
            DisciplineService disciplines) {
        return new CaptureService(captureChatClient, notes, disciplines, ZoneId.systemDefault());
    }
}
