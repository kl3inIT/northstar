package com.northstar.api.study;

import com.northstar.core.study.WritingGrader;
import com.northstar.core.study.WritingService;
import java.time.ZoneId;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the writing grader for this app (the CaptureConfig precedent): core's
 * WritingGrader is deliberately not a component so mcp/worker boot without an
 * LLM configured. The grader model id is pinned via property — it is stored on
 * every feedback row, so changing it is a deliberate act, not a side effect of
 * a default-model upgrade.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(StudyProperties.class)
class StudyConfig {

    @Bean
    WritingGrader writingGrader(ChatClient chatClient, WritingService writing, StudyProperties properties) {
        return new WritingGrader(chatClient, writing, properties.graderModel(), ZoneId.systemDefault());
    }
}
