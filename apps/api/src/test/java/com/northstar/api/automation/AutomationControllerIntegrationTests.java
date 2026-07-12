package com.northstar.api.automation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers
class AutomationControllerIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg18");

    @Autowired
    MockMvc mvc;

    @Autowired
    List<NorthstarTool> tools;

    @Test
    void inAppAssistantDiscoversAutomationTools() {
        assertThat(tools).extracting(tool -> tool.getClass().getSimpleName())
                .contains("AutomationTools");
    }

    @Test
    void createsUpdatesQueuesAndDeletesMorningBrief() throws Exception {
        mvc.perform(get("/api/automations/types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("morning-brief.v1"))
                .andExpect(jsonPath("$[0].displayName").value("Morning Brief"))
                .andExpect(jsonPath("$[0].defaultConfig.maxItems").value(6));

        String created = mvc.perform(post("/api/automations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("Daily research", true, "06:30", "MONDAY", "TUESDAY")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scheduleSynced").value(false))
                .andExpect(jsonPath("$.workflowConfig.language").value("vi"))
                .andReturn().getResponse().getContentAsString();

        String id = JsonPath.read(created, "$.id");
        Number version = JsonPath.read(created, "$.version");
        String updated = mvc.perform(put("/api/automations/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest("Weekday brief", false, "07:15", version.longValue())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Weekday brief"))
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.trigger.localTime").value("07:15:00"))
                .andReturn().getResponse().getContentAsString();

        Number updatedVersion = JsonPath.read(updated, "$.version");
        mvc.perform(post("/api/automations/{id}/runs", id))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.runKind").value("MANUAL"));

        String history = mvc.perform(get("/api/automations/{id}/runs", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("QUEUED"))
                .andReturn().getResponse().getContentAsString();
        assertThat(history).doesNotContain("test-key", "apiKey");

        mvc.perform(delete("/api/automations/{id}", id).param("version", updatedVersion.toString()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/automations/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void rejectsInvalidWeeklyTriggerAndUnknownType() throws Exception {
        mvc.perform(post("/api/automations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("Bad weekly", true, "06:30", "MONDAY", "TUESDAY")
                                .replace("\"DAILY\"", "\"WEEKLY\"")))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/automations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("Unknown", true, "06:30", "MONDAY")
                                .replace("morning-brief.v1", "unknown.v1")))
                .andExpect(status().isBadRequest());
    }

    private static String request(String name, boolean enabled, String time, String... days) {
        return """
                {
                  "type":"morning-brief.v1",
                  "name":"%s",
                  "enabled":%s,
                  "trigger":{"kind":"DAILY","localTime":"%s","daysOfWeek":%s,
                    "timezone":"Asia/Bangkok","catchUpWindowMinutes":240},
                  "workflowConfig":{"language":"vi","lookbackHours":24,"maxItems":6,
                    "topics":["AI agents"],"queries":[],"blockedDomains":[],"saveAsNote":true}
                }
                """.formatted(name, enabled, time,
                java.util.Arrays.stream(days).map(day -> "\"" + day + "\"")
                        .collect(java.util.stream.Collectors.joining(",", "[", "]")));
    }

    private static String updateRequest(String name, boolean enabled, String time, long version) {
        return """
                {
                  "name":"%s",
                  "enabled":%s,
                  "trigger":{"kind":"DAILY","localTime":"%s",
                    "daysOfWeek":["MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY"],
                    "timezone":"Asia/Bangkok","catchUpWindowMinutes":240},
                  "workflowConfig":{"language":"vi","lookbackHours":24,"maxItems":6,
                    "topics":["AI agents"],"queries":[],"blockedDomains":[],"saveAsNote":true},
                  "version":%d
                }
                """.formatted(name, enabled, time, version);
    }
}
