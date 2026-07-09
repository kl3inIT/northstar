package com.northstar.api;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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

/**
 * The HTTP error contract, end to end through the real MVC stack: Bean
 * Validation renders 400 ProblemDetail with a per-field {@code errors} map,
 * unknown resources render 404, and a stale {@code version} renders 409 — so
 * the typed web client can rely on one error shape.
 */
@SpringBootTest(properties = "spring.ai.openai.api-key=test-key")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers
class ApiErrorContractTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg18");

    @Autowired
    MockMvc mvc;

    @Test
    void blankTitleRendersValidationProblemWithFieldErrors() throws Exception {
        mvc.perform(post("/api/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.title").exists());
    }

    @Test
    void unknownSlugRendersNotFoundProblem() throws Exception {
        mvc.perform(get("/api/notes/{slug}", "does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail", containsString("does-not-exist")));
    }

    @Test
    void staleVersionRendersConflictProblem() throws Exception {
        String created = mvc.perform(post("/api/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Conflict probe\",\"contentMarkdown\":\"v1\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String noteId = JsonPath.read(created, "$.id");

        mvc.perform(put("/api/notes/{id}", noteId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Conflict probe\",\"contentMarkdown\":\"v2\",\"version\":99}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void emptyDisciplineCanBeDeleted() throws Exception {
        String created = mvc.perform(post("/api/disciplines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Delete probe\",\"color\":\"GRAY\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String disciplineId = JsonPath.read(created, "$.id");

        mvc.perform(delete("/api/disciplines/{id}", disciplineId))
                .andExpect(status().isNoContent());
    }

    @Test
    void disciplineWithLinkedWorkRendersConflictProblem() throws Exception {
        String created = mvc.perform(post("/api/disciplines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Busy discipline\",\"color\":\"BLUE\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String disciplineId = JsonPath.read(created, "$.id");

        mvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Linked project","disciplineId":"%s"}
                                """.formatted(disciplineId)))
                .andExpect(status().isCreated());

        mvc.perform(delete("/api/disciplines/{id}", disciplineId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail", containsString("Move or delete linked work")));
    }
}
