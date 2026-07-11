package com.northstar.api.assistant;

import com.northstar.core.ai.AiClientRouter;
import com.northstar.core.ai.AiRoute;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.toolsearch.ToolSearchToolCallingAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.toolsearch.index.lucene.LuceneToolIndex;
import org.springframework.stereotype.Component;

@Component
class AssistantChatClientFactory {

    private final AiClientRouter router;
    private final ChatMemory memory;
    private final ToolSearchToolCallingAdvisor toolSearch;
    private final Map<String, ChatClient> clients = new ConcurrentHashMap<>();

    AssistantChatClientFactory(AiClientRouter router, ChatMemory memory,
            ToolCallingManager toolCallingManager) {
        this.router = router;
        this.memory = memory;
        this.toolSearch = ToolSearchToolCallingAdvisor.builder()
                .toolCallingManager(toolCallingManager)
                .toolIndex(new LuceneToolIndex())
                .build();
    }

    ChatClient client(AiRoute route) {
        return clients.computeIfAbsent(route.gatewayId(), gatewayId -> ChatClient.builder(router.model(route))
                .defaultAdvisors(
                        SimpleLoggerAdvisor.builder().build(),
                        MessageChatMemoryAdvisor.builder(memory).build(),
                        toolSearch)
                .build());
    }
}
