package com.northstar.api.assistant;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.execution.ToolExecutionExceptionProcessor;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/**
 * Wires the in-app assistant: a memory-carrying ChatClient over the SAME core
 * tools the mcp app publishes (the NorthstarTool beans), plus the decorated
 * ToolCallingManager that mirrors tool calls into the UI stream. Memory is the
 * JDBC repository (spring_ai_chat_memory, Flyway V12) so conversations survive
 * restarts; the window cap keeps the prompt from growing without bound.
 *
 * <p>Tool discovery is dynamic (ToolSearchToolCallingAdvisor): the registered
 * tools are Lucene-indexed per conversation, only the search_tools definition
 * is sent to the model up front, and tools it discovers stay expanded for the
 * rest of that conversation (LRU-evicted). The advisor also moves the tool
 * execution loop into the advisor chain — it runs our EventEmittingToolManager
 * directly, so the SSE tool events keep flowing unchanged.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AssistantTitleProperties.class)
class AssistantConfig {

    /** Recent messages the model sees; the FULL transcript is kept regardless (see WindowedChatMemory). */
    private static final int MODEL_WINDOW_MESSAGES = 30;

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
        // NOT MessageWindowChatMemory: that trims to the window and saveAll-REPLACES
        // the rows, physically deleting older history that /history and /conversations
        // read. WindowedChatMemory keeps the full transcript and only windows the prompt.
        return new WindowedChatMemory(repository, MODEL_WINDOW_MESSAGES);
    }

}
