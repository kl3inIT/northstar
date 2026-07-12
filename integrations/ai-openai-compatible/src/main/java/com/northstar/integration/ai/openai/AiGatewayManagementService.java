package com.northstar.integration.ai.openai;

import com.northstar.core.ai.AiRouteSettingRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.EnumMap;
import java.util.Map;
import com.northstar.core.ai.AiGatewayCapability;
import org.springframework.dao.DataAccessException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

@Service
public class AiGatewayManagementService {

    private final AiGatewayRegistry gateways;
    private final OpenAiModelCatalog catalog;
    private final OpenAiCompatibleTextToSpeechGateway speech;
    private final AiCapabilityCatalog capabilities;
    private final ObjectProvider<SpringAiClientRouter> router;
    private final AiRouteSettingRepository routes;
    private final JdbcClient jdbc;

    AiGatewayManagementService(AiGatewayRegistry gateways, OpenAiModelCatalog catalog,
            OpenAiCompatibleTextToSpeechGateway speech,
            AiCapabilityCatalog capabilities,
            ObjectProvider<SpringAiClientRouter> router, AiRouteSettingRepository routes, JdbcClient jdbc) {
        this.gateways = gateways;
        this.catalog = catalog;
        this.speech = speech;
        this.capabilities = capabilities;
        this.router = router;
        this.routes = routes;
        this.jdbc = jdbc;
    }

    @Transactional
    public AiGatewayDescriptor save(AiGatewayInput input) {
        AiGatewayDescriptor saved = gateways.save(input);
        invalidate(saved.id());
        return saved;
    }

    public AiGatewayTestResult test(AiGatewayInput input) {
        return test(gateways.draft(input));
    }

    public AiGatewayTestResult test(String gatewayId) {
        return test(gateways.definition(gatewayId));
    }

    @Transactional
    public void delete(String gatewayId) {
        routes.deleteByGatewayId(gatewayId);
        try {
            jdbc.sql("DELETE FROM assistant_conversation_route WHERE gateway_id = ?")
                    .param(gatewayId)
                    .update();
        } catch (DataAccessException ignored) {
            // Focused integration tests may not create the API-owned conversation table.
        }
        try {
            jdbc.sql("""
                    UPDATE web_research_setting
                    SET search_gateway_id = CASE WHEN search_gateway_id = ? THEN NULL ELSE search_gateway_id END,
                        search_target_id = CASE WHEN search_gateway_id = ? THEN NULL ELSE search_target_id END,
                        page_gateway_id = CASE WHEN page_gateway_id = ? THEN NULL ELSE page_gateway_id END,
                        page_target_id = CASE WHEN page_gateway_id = ? THEN NULL ELSE page_target_id END
                    WHERE search_gateway_id = ? OR page_gateway_id = ?
                    """)
                    .params(gatewayId, gatewayId, gatewayId, gatewayId, gatewayId, gatewayId)
                    .update();
        } catch (DataAccessException ignored) {
            // Focused integration tests may not create the core-owned web settings table.
        }
        gateways.delete(gatewayId);
        invalidate(gatewayId);
    }

    private AiGatewayTestResult test(AiGatewayDefinition gateway) {
        Instant started = Instant.now();
        try {
            List<AiModelDescriptor> models = catalog.probe(gateway);
            var ttsTargets = gateway.type().supports(com.northstar.core.ai.AiGatewayCapability.TEXT_TO_SPEECH)
                    ? speech.probeTargets(gateway)
                    : List.<com.northstar.core.speech.SpeechTarget>of();
            Map<AiGatewayCapability, List<AiCapabilityTarget>> capabilityTargets =
                    new EnumMap<>(AiGatewayCapability.class);
            for (AiGatewayCapability capability : List.of(
                    AiGatewayCapability.WEB_SEARCH, AiGatewayCapability.WEB_FETCH,
                    AiGatewayCapability.SPEECH_TO_TEXT, AiGatewayCapability.IMAGE_GENERATION,
                    AiGatewayCapability.EMBEDDING)) {
                if (gateway.type().supports(capability)) {
                    capabilityTargets.put(capability, capabilities.probe(gateway, capability));
                }
            }
            boolean empty = models.isEmpty() && ttsTargets.isEmpty()
                    && capabilityTargets.values().stream().allMatch(List::isEmpty);
            return new AiGatewayTestResult(true, elapsed(started), models, ttsTargets,
                    Map.copyOf(capabilityTargets),
                    empty
                            ? "Connection succeeded; no capability targets were returned"
                            : "Connection succeeded");
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            String message = status == 401 || status == 403
                    ? "The gateway rejected the API key"
                    : "The gateway returned HTTP " + status;
            return failed(started, message);
        } catch (ResourceAccessException exception) {
            return failed(started, "Could not reach the gateway before the timeout");
        } catch (RuntimeException exception) {
            return failed(started, "The gateway response was not OpenAI-compatible");
        }
    }

    private void invalidate(String gatewayId) {
        catalog.invalidate(gatewayId);
        SpringAiClientRouter clientRouter = router.getIfAvailable();
        if (clientRouter != null) {
            clientRouter.invalidate(gatewayId);
        }
    }

    private static long elapsed(Instant started) {
        return Duration.between(started, Instant.now()).toMillis();
    }

    private static AiGatewayTestResult failed(Instant started, String message) {
        return new AiGatewayTestResult(false, elapsed(started), List.of(), List.of(), Map.of(), message);
    }
}
