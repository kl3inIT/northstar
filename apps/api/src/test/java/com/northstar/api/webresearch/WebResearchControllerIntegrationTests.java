package com.northstar.api.webresearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.northstar.core.assistant.NorthstarTool;
import java.util.List;
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
        "northstar.web.firecrawl.api-key=test-key"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers
class WebResearchControllerIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg18");

    @Autowired
    MockMvc mvc;

    @Autowired
    List<NorthstarTool> tools;

    @Test
    void listsProvidersUpdatesRuntimeOverrideAndResetsToDefaults() throws Exception {
        mvc.perform(get("/api/settings/web-research"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.searchProviderId").value("openai"))
                .andExpect(jsonPath("$.pageReaderId").value("direct"))
                .andExpect(jsonPath("$.overridden").value(false));

        String providers = mvc.perform(get("/api/settings/web-research/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == 'openai')].configured").value(true))
                .andExpect(jsonPath("$[?(@.id == 'firecrawl')].configured").value(true))
                .andExpect(jsonPath("$[?(@.id == 'firecrawl')].capabilities[0]").value("READ_PAGE"))
                .andReturn().getResponse().getContentAsString();
        assertThat(providers).doesNotContain("test-key", "apiKey", "api-key");

        mvc.perform(put("/api/settings/web-research")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enabled":false,"searchProviderId":"openai",
                                 "pageReaderId":"direct","fallbackEnabled":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.fallbackEnabled").value(true))
                .andExpect(jsonPath("$.overridden").value(true));

        mvc.perform(delete("/api/settings/web-research/override"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.overridden").value(false));
    }

    @Test
    void inAppAssistantDiscoversWebToolsWithoutPublishingThemThroughCoreMcpTools() {
        assertThat(tools).extracting(tool -> tool.getClass().getSimpleName())
                .contains("WebResearchTools");
    }
}
