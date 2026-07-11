package com.northstar.api.assistant;

import com.northstar.core.ai.AiClientRouter;
import com.northstar.core.ai.AiRoute;
import com.northstar.core.ai.AiTask;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
class AssistantConversationRouteService {

    private final JdbcClient jdbc;
    private final AiClientRouter router;

    AssistantConversationRouteService(JdbcClient jdbc, AiClientRouter router) {
        this.jdbc = jdbc;
        this.router = router;
    }

    @Transactional
    AiRoute resolve(String conversationId, String gatewayId, String modelId) {
        boolean gatewayPresent = StringUtils.hasText(gatewayId);
        boolean modelPresent = StringUtils.hasText(modelId);
        if (gatewayPresent != modelPresent) {
            throw new IllegalArgumentException("gatewayId and modelId must be sent together");
        }
        if (gatewayPresent) {
            AiRoute requested = new AiRoute(gatewayId, modelId);
            router.validate(AiTask.ASSISTANT, requested);
            save(conversationId, requested);
            return requested;
        }
        AiRoute selected = find(conversationId);
        if (selected == null) {
            selected = mostRecent();
        }
        if (selected == null) {
            selected = router.route(AiTask.ASSISTANT);
        }
        router.validate(AiTask.ASSISTANT, selected);
        save(conversationId, selected);
        return selected;
    }

    @Transactional(readOnly = true)
    AiRoute selection(String conversationId) {
        AiRoute selected = find(conversationId);
        if (selected == null) selected = mostRecent();
        return selected == null ? router.route(AiTask.ASSISTANT) : selected;
    }

    @Transactional
    void delete(String conversationId) {
        jdbc.sql("DELETE FROM assistant_conversation_route WHERE conversation_id = ?")
                .param(conversationId)
                .update();
    }

    private AiRoute find(String conversationId) {
        return jdbc.sql("SELECT gateway_id, model_id FROM assistant_conversation_route WHERE conversation_id = ?")
                .param(conversationId)
                .query((rs, _) -> new AiRoute(rs.getString("gateway_id"), rs.getString("model_id")))
                .optional()
                .orElse(null);
    }

    private AiRoute mostRecent() {
        return jdbc.sql("SELECT gateway_id, model_id FROM assistant_conversation_route ORDER BY updated_at DESC LIMIT 1")
                .query((rs, _) -> new AiRoute(rs.getString("gateway_id"), rs.getString("model_id")))
                .optional()
                .orElse(null);
    }

    private void save(String conversationId, AiRoute route) {
        jdbc.sql("""
                INSERT INTO assistant_conversation_route (conversation_id, gateway_id, model_id, updated_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (conversation_id) DO UPDATE SET
                    gateway_id = EXCLUDED.gateway_id,
                    model_id = EXCLUDED.model_id,
                    updated_at = EXCLUDED.updated_at
                """)
                .params(conversationId, route.gatewayId(), route.modelId(), Timestamp.from(Instant.now()))
                .update();
    }
}
