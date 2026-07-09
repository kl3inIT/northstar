package com.northstar.api.note;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.northstar.core.note.NoteService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {
        "spring.ai.openai.api-key=test-key",
        "northstar.auth.enabled=false"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers
class NoteControllerIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg18");

    @Autowired
    MockMvc mvc;

    @Autowired
    NoteService notes;

    @Test
    void searchEndpointReturnsRankedNoteSummaries() throws Exception {
        notes.create("Kho dữ liệu chuyên ngành", "Career/DTH",
                "Các lệnh registry và image push cho môi trường OpenShift sandbox.", List.of("devops"));
        notes.create("OpenShift sandbox account", "Career/DTH",
                "Registry credentials and image push commands.", List.of("devops"));

        mvc.perform(get("/api/notes/search").param("q", "dữ liệu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Kho dữ liệu chuyên ngành"))
                .andExpect(jsonPath("$[0].slug").value("kho-du-lieu-chuyen-nganh"));

        mvc.perform(get("/api/notes/search").param("q", "OpenShfit sandbox"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("OpenShift sandbox account"));
    }
}
