package com.northstar.api.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientBuilderCustomizer;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Shared LLM plumbing for this app. The OpenAI starter autoconfigures the
 * {@link ChatClient.Builder}; the single ChatClient bean built from it serves
 * every AI feature (capture, alignment). The builder customizer is applied by
 * the autoconfiguration itself, so cross-cutting defaults live here — not in
 * each feature config.
 */
@Configuration(proxyBeanMethods = false)
class AiConfig {

    @Bean
    ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    /**
     * Logs every LLM request/response. Silent until the SimpleLoggerAdvisor
     * logger is raised to DEBUG (see application.yml) — zero cost otherwise.
     */
    @Bean
    ChatClientBuilderCustomizer loggingCustomizer() {
        return builder -> builder.defaultAdvisors(SimpleLoggerAdvisor.builder().build());
    }
}
