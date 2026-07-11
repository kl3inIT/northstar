package com.northstar.integration.web.firecrawl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.northstar.core.web.WebPageProviderResult;
import com.northstar.core.web.WebPageRequest;
import com.northstar.core.web.WebResearchException;
import com.northstar.core.web.WebResearchFailureCode;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class FirecrawlWebPageReaderTests {

    @Test
    void requestsMainMarkdownAndMapsTheScrapedPage() {
        FirecrawlWebPageReaderProperties properties = properties(24);
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.firecrawl.dev");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FirecrawlWebPageReader reader = new FirecrawlWebPageReader(properties, builder.build(), new ObjectMapper());
        server.expect(requestTo("https://api.firecrawl.dev/v2/scrape"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("onlyMainContent")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("markdown")))
                .andRespond(withSuccess("""
                        {"success":true,"data":{"markdown":"# Example Domain\\nUseful content that is long.",
                        "metadata":{"title":"Example Domain","sourceURL":"https://example.com/final"}}}
                        """, MediaType.APPLICATION_JSON));

        WebPageProviderResult page = reader.read(WebPageRequest.of("https://example.com/start"));

        assertThat(page.finalUrl().toString()).isEqualTo("https://example.com/final");
        assertThat(page.title()).isEqualTo("Example Domain");
        assertThat(page.content()).hasSizeLessThanOrEqualTo(24)
                .startsWith("# Example Domain").doesNotEndWith(" ");
        assertThat(page.contentType()).isEqualTo("text/markdown");
        assertThat(page.truncated()).isTrue();
        server.verify();
    }

    @Test
    void mapsQuotaResponsesToARetryableFailure() {
        FirecrawlWebPageReaderProperties properties = properties(40_000);
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.firecrawl.dev");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FirecrawlWebPageReader reader = new FirecrawlWebPageReader(properties, builder.build(), new ObjectMapper());
        server.expect(requestTo("https://api.firecrawl.dev/v2/scrape"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> reader.read(WebPageRequest.of("https://example.com")))
                .isInstanceOfSatisfying(WebResearchException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(WebResearchFailureCode.RATE_LIMITED);
                    assertThat(exception.isRetryable()).isTrue();
                });
    }

    private static FirecrawlWebPageReaderProperties properties(int maxCharacters) {
        return new FirecrawlWebPageReaderProperties("https://api.firecrawl.dev", "test-key",
                2 * 1024 * 1024, maxCharacters, Duration.ofSeconds(5), Duration.ofSeconds(60));
    }
}
