package com.northstar.integration.web.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.northstar.core.ai.AiGatewayConnection;
import com.northstar.core.ai.AiGatewayType;
import com.northstar.core.web.WebProviderRoute;
import com.northstar.core.web.WebSearchRequest;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OpenAiWebSearchProviderTests {

    @Test
    void usesTheSelectedOpenAiGatewayAndResponsesModel() {
        AiGatewayConnection connection = new AiGatewayConnection("openai", "OpenAI", AiGatewayType.OPENAI,
                "https://api.openai.com/v1", "secret", Duration.ofSeconds(30));
        RestClient.Builder builder = RestClient.builder().baseUrl(connection.baseUrl());
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var properties = new OpenAiWebSearchProperties("medium");
        var provider = new OpenAiWebSearchProvider(properties, _ -> connection,
                _ -> builder.build());
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("research-model")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("web_search")))
                .andRespond(withSuccess("""
                        {"output":[{"type":"message","content":[{"type":"output_text","text":"Answer",
                         "annotations":[{"url":"https://example.com","title":"Example"}]}]}]}
                        """, MediaType.APPLICATION_JSON));

        var result = provider.search(WebSearchRequest.of("latest"),
                new WebProviderRoute("openai", "research-model"));

        assertThat(result.answer()).isEqualTo("Answer");
        assertThat(result.sources()).singleElement()
                .satisfies(source -> assertThat(source.url()).isEqualTo("https://example.com"));
        server.verify();
    }
}
