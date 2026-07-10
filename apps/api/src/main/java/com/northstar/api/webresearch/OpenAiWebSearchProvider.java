package com.northstar.api.webresearch;

import com.northstar.core.web.WebResearchException;
import com.northstar.core.web.WebResearchFailureCode;
import com.northstar.core.web.WebSearchProvider;
import com.northstar.core.web.WebSearchProviderResult;
import com.northstar.core.web.WebSearchRequest;
import com.northstar.core.web.WebSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
class OpenAiWebSearchProvider implements WebSearchProvider {

    private final WebResearchProperties.OpenAi properties;
    private final RestClient openai;

    @Autowired
    OpenAiWebSearchProvider(WebResearchProperties properties) {
        this(properties.getOpenai(), client(properties.getOpenai()));
    }

    OpenAiWebSearchProvider(WebResearchProperties.OpenAi properties, RestClient openai) {
        this.properties = properties;
        this.openai = openai;
    }

    private static RestClient client(WebResearchProperties.OpenAi properties) {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(http);
        requests.setReadTimeout(properties.getRequestTimeout());
        return RestClient.builder()
                .baseUrl("https://api.openai.com")
                .defaultHeader("Authorization", "Bearer " + properties.getApiKey())
                .requestFactory(requests)
                .build();
    }

    @Override
    public String id() {
        return "openai";
    }

    @Override
    public String displayName() {
        return "OpenAI Web Search";
    }

    @Override
    public boolean configured() {
        return StringUtils.hasText(properties.getApiKey());
    }

    @Override
    public WebSearchProviderResult search(WebSearchRequest request) {
        if (!configured()) {
            throw new WebResearchException(WebResearchFailureCode.NOT_CONFIGURED,
                    "OPENAI_API_KEY is not configured");
        }
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("type", "web_search");
        tool.put("search_context_size", properties.getSearchContextSize());
        if (!request.allowedDomains().isEmpty()) {
            tool.put("filters", Map.of("allowed_domains", request.allowedDomains()));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel());
        body.put("input", searchPrompt(request));
        body.put("tools", List.of(tool));
        body.put("include", List.of("web_search_call.action.sources"));
        body.put("store", false);

        try {
            Map<?, ?> response = openai.post().uri("/v1/responses")
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            if (response == null) {
                throw new WebResearchException(WebResearchFailureCode.UNAVAILABLE,
                        "OpenAI returned an empty web-search response");
            }
            return parse(response, request);
        } catch (RestClientResponseException exception) {
            WebResearchFailureCode code = exception.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS
                    ? WebResearchFailureCode.RATE_LIMITED
                    : exception.getStatusCode().is5xxServerError()
                            ? WebResearchFailureCode.UNAVAILABLE
                            : WebResearchFailureCode.INVALID_REQUEST;
            throw new WebResearchException(code,
                    "OpenAI web search failed with HTTP " + exception.getStatusCode().value(), exception);
        } catch (ResourceAccessException exception) {
            throw new WebResearchException(WebResearchFailureCode.UNAVAILABLE,
                    "OpenAI web search could not be reached", exception);
        }
    }

    private static String searchPrompt(WebSearchRequest request) {
        StringBuilder prompt = new StringBuilder(request.query());
        if (request.recency() != com.northstar.core.web.WebRecency.ANY) {
            prompt.append("\nPrefer information published within the last ")
                    .append(request.recency().name().toLowerCase()).append('.');
        }
        if (!request.blockedDomains().isEmpty()) {
            prompt.append("\nDo not use these domains: ")
                    .append(String.join(", ", request.blockedDomains())).append('.');
        }
        prompt.append("\nUse at most ").append(request.maxResults())
                .append(" primary, relevant sources. Answer concisely and factually.");
        return prompt.toString();
    }

    private static WebSearchProviderResult parse(Map<?, ?> response, WebSearchRequest request) {
        StringBuilder answer = new StringBuilder();
        Map<String, WebSource> sources = new LinkedHashMap<>();
        for (Object itemValue : list(response.get("output"))) {
            Map<?, ?> item = map(itemValue);
            if ("message".equals(item.get("type"))) {
                for (Object contentValue : list(item.get("content"))) {
                    Map<?, ?> content = map(contentValue);
                    if ("output_text".equals(content.get("type")) && content.get("text") instanceof String text) {
                        if (!answer.isEmpty()) answer.append("\n\n");
                        answer.append(text.strip());
                    }
                    for (Object annotationValue : list(content.get("annotations"))) {
                        Map<?, ?> annotation = map(annotationValue);
                        addSource(sources, annotation.get("url"), annotation.get("title"), request);
                    }
                }
            }
            if ("web_search_call".equals(item.get("type"))) {
                Map<?, ?> action = map(item.get("action"));
                for (Object sourceValue : list(action.get("sources"))) {
                    Map<?, ?> source = map(sourceValue);
                    addSource(sources, source.get("url"), source.get("title"), request);
                }
            }
        }
        List<WebSource> limited = sources.values().stream().limit(request.maxResults()).toList();
        return new WebSearchProviderResult(answer.toString(), limited);
    }

    private static void addSource(Map<String, WebSource> sources, Object urlValue, Object titleValue,
            WebSearchRequest request) {
        if (!(urlValue instanceof String url) || url.isBlank() || isBlocked(url, request.blockedDomains())) {
            return;
        }
        String title = titleValue instanceof String value && !value.isBlank() ? value : url;
        WebSource existing = sources.get(url);
        if (existing == null || (existing.title().equals(url) && !title.equals(url))) {
            sources.put(url, new WebSource(title, url, "", (Instant) null));
        }
    }

    private static boolean isBlocked(String url, List<String> blockedDomains) {
        try {
            String host = URI.create(url).getHost();
            if (host == null) return true;
            String normalized = host.toLowerCase();
            return blockedDomains.stream().anyMatch(domain ->
                    normalized.equals(domain) || normalized.endsWith("." + domain));
        } catch (IllegalArgumentException exception) {
            return true;
        }
    }

    private static List<?> list(Object value) {
        return value instanceof List<?> values ? values : List.of();
    }

    private static Map<?, ?> map(Object value) {
        return value instanceof Map<?, ?> values ? values : Map.of();
    }
}
