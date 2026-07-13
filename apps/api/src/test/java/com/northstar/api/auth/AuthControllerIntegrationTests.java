package com.northstar.api.auth;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {
        "northstar.auth.enabled=true",
        "northstar.auth.username=datph",
        "northstar.auth.password-hash={bcrypt}$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG",
        "northstar.auth.mobile.enabled=true",
        "northstar.auth.mobile.jwt-secret=YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWE=",
        "northstar.security.cors.allowed-origins=http://127.0.0.1:7357,https://mobile-preview.example.com"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers
class AuthControllerIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg18");

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Test
    void unauthenticatedApiRequestsReturnProblemJsonInsteadOfRedirects() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false));

        mvc.perform(get("/api/notes"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Authentication required"));
    }

    @Test
    void loginUsesCsrfAndPersistsASessionCookie() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"datph\",\"password\":\"password\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"datph\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail", containsString("Invalid username or password")));

        MvcResult loggedIn = mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"datph\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.username").value("datph"))
                .andReturn();

        Cookie session = loggedIn.getResponse().getCookie("SESSION");
        org.assertj.core.api.Assertions.assertThat(session).isNotNull();
        org.assertj.core.api.Assertions.assertThat(session.getMaxAge()).isEqualTo(30 * 24 * 60 * 60);
        org.assertj.core.api.Assertions.assertThat(jdbc.sql("""
                        SELECT max_inactive_interval
                        FROM spring_session
                        WHERE principal_name = :username
                        ORDER BY creation_time DESC
                        LIMIT 1
                        """)
                .param("username", "datph")
                .query(Integer.class)
                .single()).isEqualTo(30 * 24 * 60 * 60);

        mvc.perform(get("/api/notes").cookie(session))
                .andExpect(status().isOk());

        mvc.perform(post("/api/auth/logout").with(csrf()).cookie(session))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/auth/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false));
    }

    @Test
    void csrfEndpointExposesTheSpaTokenCookie() throws Exception {
        MvcResult result = mvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();

        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        org.assertj.core.api.Assertions.assertThat(cookie).isNotNull();
        org.assertj.core.api.Assertions.assertThat(cookie.isHttpOnly()).isFalse();
    }

    @Test
    void mobileLoginIssuesBearerAndRotatesRefreshTokensWithoutCsrf() throws Exception {
        MvcResult login = mvc.perform(post("/api/auth/mobile/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"datph\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.username").value("datph"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();

        String accessToken = JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");
        String refreshToken = JsonPath.read(login.getResponse().getContentAsString(), "$.refreshToken");

        mvc.perform(get("/api/auth/mobile/me").header(AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("datph"));

        MvcResult refreshed = mvc.perform(post("/api/auth/mobile/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String replacement = JsonPath.read(refreshed.getResponse().getContentAsString(), "$.refreshToken");
        org.assertj.core.api.Assertions.assertThat(replacement).isNotEqualTo(refreshToken);

        mvc.perform(post("/api/auth/mobile/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/auth/mobile/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + replacement + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void mobileLogoutRevokesTheRefreshFamily() throws Exception {
        MvcResult login = mvc.perform(post("/api/auth/mobile/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"datph\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String refreshToken = JsonPath.read(login.getResponse().getContentAsString(), "$.refreshToken");

        mvc.perform(post("/api/auth/mobile/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/auth/mobile/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void corsAllowsOnlyExactConfiguredOriginsWithoutCredentials() throws Exception {
        mvc.perform(options("/api/auth/mobile/login")
                        .header("Origin", "http://127.0.0.1:7357")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://127.0.0.1:7357"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));

        mvc.perform(options("/api/auth/mobile/login")
                        .header("Origin", "https://mobile-preview.example.com")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://mobile-preview.example.com"));

        mvc.perform(options("/api/auth/mobile/login")
                        .header("Origin", "https://mobile-preview.example.com.evil.test")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void corsSupportsRestMethodsAndRejectsUnlistedHeaders() throws Exception {
        mvc.perform(options("/api/tasks/00000000-0000-0000-0000-000000000000")
                        .header("Origin", "https://mobile-preview.example.com")
                        .header("Access-Control-Request-Method", "PUT")
                        .header("Access-Control-Request-Headers", "authorization,content-type,accept,x-timezone"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("PUT")))
                .andExpect(header().string("Access-Control-Allow-Headers", containsString("authorization")))
                .andExpect(header().string("Access-Control-Allow-Headers", containsString("x-timezone")));

        mvc.perform(options("/api/auth/mobile/login")
                        .header("Origin", "https://mobile-preview.example.com")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "x-admin-override"))
                .andExpect(status().isForbidden());
    }
}
