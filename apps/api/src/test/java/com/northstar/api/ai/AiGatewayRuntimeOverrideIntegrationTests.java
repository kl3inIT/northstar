package com.northstar.api.ai;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {
        "northstar.auth.enabled=false",
        "northstar.ai.gateways.openai.api-key=",
        "northstar.ai.credentials.encryption-key-base64=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers
class AiGatewayRuntimeOverrideIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg18");

    @Autowired
    MockMvc mvc;

    @Test
    void deploymentGatewayUsesOneEncryptedRuntimeOverrideAndResetsWithoutDroppingRoutes() throws Exception {
        mvc.perform(get("/api/settings/ai"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gateways.length()").value(1))
                .andExpect(jsonPath("$.gateways[0].id").value("openai"))
                .andExpect(jsonPath("$.gateways[0].configured").value(false))
                .andExpect(jsonPath("$.gateways[0].credentialSource").value("NONE"))
                .andExpect(jsonPath("$.gateways[0].deploymentBacked").value(true))
                .andExpect(jsonPath("$.gateways[0].overridden").value(false))
                .andExpect(jsonPath("$.gateways[0].editable").value(true));

        mvc.perform(put("/api/settings/ai/gateways/openai")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id":"openai",
                                  "displayName":"OpenAI",
                                  "type":"OPENAI",
                                  "baseUrl":"https://api.openai.com/v1",
                                  "apiKey":"runtime-secret",
                                  "models":["gpt-5.6-luna"],
                                  "ttsTargets":[],
                                  "webSearchTargets":[],
                                  "webFetchTargets":[],
                                  "sttTargets":[],
                                  "imageTargets":[],
                                  "embeddingTargets":[],
                                  "discoverModels":false,
                                  "timeoutSeconds":60
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("openai"))
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.credentialSource").value("SETTINGS"))
                .andExpect(jsonPath("$.deploymentBacked").value(true))
                .andExpect(jsonPath("$.overridden").value(true));

        mvc.perform(put("/api/settings/ai/routes/ASSISTANT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gatewayId":"openai","modelId":"gpt-5.6-luna","options":{}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overridden").value(true));

        mvc.perform(delete("/api/settings/ai/gateways/openai"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/settings/ai"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gateways.length()").value(1))
                .andExpect(jsonPath("$.gateways[0].configured").value(false))
                .andExpect(jsonPath("$.gateways[0].credentialSource").value("NONE"))
                .andExpect(jsonPath("$.gateways[0].overridden").value(false))
                .andExpect(jsonPath("$.routes.ASSISTANT.overridden").value(true))
                .andExpect(jsonPath("$.routes.ASSISTANT.route.gatewayId").value("openai"));
    }
}
