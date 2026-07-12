package com.northstar.integration.web.ninerouter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.northstar.core.ai.AiGatewayConnection;
import com.northstar.core.ai.AiGatewayType;
import com.northstar.core.web.WebPageRequest;
import com.northstar.core.web.WebProviderRoute;
import com.northstar.core.web.WebResearchException;
import com.northstar.core.web.WebResearchFailureCode;
import com.northstar.core.web.WebSearchRequest;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class NineRouterWebProviderTests {

    private static final WebProviderRoute SEARCH_ROUTE = new WebProviderRoute("router", "search-combo");
    private static final WebProviderRoute FETCH_ROUTE = new WebProviderRoute("router", "fetch-combo");

    @Test
    void searchesThroughTheSelectedComboAndMapsNormalizedSources() {
        Harness harness = harness();
        harness.server.expect(requestTo("https://router.example/v1/search"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("search-combo")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("northstar")))
                .andRespond(withSuccess("""
                        {"provider":"searxng","query":"northstar","answer":null,"results":[
                          {"title":"Allowed","url":"https://example.com/one","snippet":"Useful","published_at":"2026-07-12T00:00:00Z"},
                          {"title":"Blocked","url":"https://blocked.example/two","snippet":"Ignore"}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        var result = harness.provider.search(new WebSearchRequest(
                "northstar", null, 5, java.util.List.of(), java.util.List.of("blocked.example")), SEARCH_ROUTE);

        assertThat(result.sources()).singleElement().satisfies(source -> {
            assertThat(source.title()).isEqualTo("Allowed");
            assertThat(source.url()).isEqualTo("https://example.com/one");
            assertThat(source.publishedAt()).isNotNull();
        });
        harness.server.verify();
    }

    @Test
    void fetchesMarkdownThroughTheSelectedCombo() {
        Harness harness = harness();
        harness.server.expect(requestTo("https://router.example/v1/web/fetch"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("fetch-combo")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("https://example.com/article")))
                .andRespond(withSuccess("""
                        {"provider":"firecrawl","url":"https://example.com/final","title":"Example",
                         "content":{"format":"markdown","text":"# Useful page","length":13}}
                        """, MediaType.APPLICATION_JSON));

        var page = harness.provider.read(WebPageRequest.of("https://example.com/article"), FETCH_ROUTE);

        assertThat(page.finalUrl().toString()).isEqualTo("https://example.com/final");
        assertThat(page.title()).isEqualTo("Example");
        assertThat(page.content()).isEqualTo("# Useful page");
        assertThat(page.contentType()).isEqualTo("text/markdown");
        harness.server.verify();
    }

    @Test
    void mapsRateLimitsAndRejectsNonNineRouterConnections() {
        Harness harness = harness();
        harness.server.expect(requestTo("https://router.example/v1/search"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> harness.provider.search(WebSearchRequest.of("quota"), SEARCH_ROUTE))
                .isInstanceOfSatisfying(WebResearchException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(WebResearchFailureCode.RATE_LIMITED);
                    assertThat(exception.isRetryable()).isTrue();
                });

        var openAi = new AiGatewayConnection("openai", "OpenAI", AiGatewayType.OPENAI,
                "https://api.openai.com/v1", "secret", Duration.ofSeconds(30));
        var provider = new NineRouterWebProvider(_ -> openAi, new ObjectMapper(), _ -> RestClient.create());
        assertThat(provider.configured(new WebProviderRoute("openai", "search-combo"))).isFalse();
    }

    private static Harness harness() {
        AiGatewayConnection connection = new AiGatewayConnection("router", "9Router",
                AiGatewayType.NINE_ROUTER, "https://router.example/v1", "secret", Duration.ofSeconds(30));
        RestClient.Builder builder = RestClient.builder().baseUrl(connection.baseUrl());
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        NineRouterWebProvider provider = new NineRouterWebProvider(_ -> connection,
                new ObjectMapper(), _ -> builder.build());
        return new Harness(provider, server);
    }

    private record Harness(NineRouterWebProvider provider, MockRestServiceServer server) {
    }
}
