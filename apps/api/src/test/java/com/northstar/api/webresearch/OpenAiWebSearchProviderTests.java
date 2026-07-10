package com.northstar.api.webresearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.northstar.core.web.WebRecency;
import com.northstar.core.web.WebSearchProviderResult;
import com.northstar.core.web.WebSearchRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OpenAiWebSearchProviderTests {

    @Test
    void parsesAnswerAndDeduplicatedCitationSources() {
        WebResearchProperties.OpenAi properties = new WebResearchProperties.OpenAi();
        properties.setApiKey("test-key");
        properties.setModel("test-model");
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.openai.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiWebSearchProvider provider = new OpenAiWebSearchProvider(properties, builder.build());
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("allowed_domains")))
                .andRespond(withSuccess("""
                        {
                          "output": [
                            {"type":"web_search_call","action":{"type":"search","sources":[
                              {"type":"url","url":"https://example.com/report"},
                              {"type":"url","url":"https://blocked.test/nope"}
                            ]}},
                            {"type":"message","content":[{"type":"output_text","text":"Current answer", "annotations":[
                              {"type":"url_citation","url":"https://example.com/report","title":"Example report"}
                            ]}]}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        WebSearchProviderResult result = provider.search(new WebSearchRequest(
                "current answer", WebRecency.WEEK, 5, List.of("example.com"), List.of("blocked.test")));

        assertThat(result.answer()).isEqualTo("Current answer");
        assertThat(result.sources()).hasSize(1);
        assertThat(result.sources().getFirst().title()).isEqualTo("Example report");
        server.verify();
    }
}
