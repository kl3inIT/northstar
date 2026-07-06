package com.northstar.api.assistant;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.execution.ToolExecutionExceptionProcessor;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/**
 * Wires the in-app assistant: a memory-carrying ChatClient over the SAME core
 * tools the mcp app publishes (the NorthstarTool beans), plus the decorated
 * ToolCallingManager that mirrors tool calls into the UI stream. Memory is the
 * JDBC repository (spring_ai_chat_memory, Flyway V12) so conversations survive
 * restarts; the window cap keeps the prompt from growing without bound.
 */
@Configuration(proxyBeanMethods = false)
class AssistantConfig {

    static final String ASSISTANT_CHAT_CLIENT = "assistantChatClient";

    /** Replaces the autoconfigured manager for the whole app; non-assistant calls pass through. */
    @Bean
    ToolCallingManager toolCallingManager(ToolCallbackResolver resolver,
            ToolExecutionExceptionProcessor exceptionProcessor,
            ObjectProvider<ObservationRegistry> observationRegistry,
            ObjectMapper json) {
        ToolCallingManager delegate = ToolCallingManager.builder()
                .observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
                .toolCallbackResolver(resolver)
                .toolExecutionExceptionProcessor(exceptionProcessor)
                .build();
        return new EventEmittingToolManager(delegate, json);
    }

    @Bean
    ChatMemory chatMemory(ChatMemoryRepository repository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(30)
                .build();
    }

    @Bean(ASSISTANT_CHAT_CLIENT)
    ChatClient assistantChatClient(ChatClient.Builder builder, ChatMemory chatMemory) {
        return builder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }
}
