package com.northstar.integration.web.openai;

import com.northstar.core.ai.AiGatewayCapability;
import com.northstar.core.ai.AiGatewayConnection;
import com.northstar.core.ai.AiGatewayConnectionResolver;
import com.northstar.core.ai.AiGatewayType;
import com.northstar.core.web.WebProviderRoute;
import com.northstar.core.web.WebResearchException;
import com.northstar.core.web.WebResearchFailureCode;
import com.northstar.core.web.WebSearchProvider;
import com.northstar.core.web.WebSearchProviderResult;
import com.northstar.core.web.WebSearchRequest;
import com.northstar.core.web.WebSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class OpenAiWebSearchProvider implements WebSearchProvider {

    private final OpenAiWebSearchProperties properties;
    private final AiGatewayConnectionResolver gateways;
    private final Function<AiGatewayConnection, RestClient> gatewayClients;

    @Autowired
    OpenAiWebSearchProvider(OpenAiWebSearchProperties properties,
            ObjectProvider<AiGatewayConnectionResolver> gateways) {
        this(properties, gateways.getIfAvailable(), OpenAiWebSearchProvider::client);
    }

    public OpenAiWebSearchProvider(OpenAiWebSearchProperties properties, AiGatewayConnectionResolver gateways,
            Function<AiGatewayConnection, RestClient> gatewayClients) {
        this.properties = properties;
        this.gateways = gateways;
        this.gatewayClients = gatewayClients;
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
        return gateways != null;
    }

    @Override
    public boolean routeRequired() {
        return true;
    }

    @Override
    public Set<AiGatewayType> gatewayTypes() {
        return Set.of(AiGatewayType.OPENAI);
    }

    @Override
    public boolean configured(WebProviderRoute route) {
        if (route == null || !route.complete()) return configured();
        try {
            AiGatewayConnection connection = requireConnection(route);
            return connection.type() == AiGatewayType.OPENAI
                    && connection.supports(AiGatewayCapability.WEB_SEARCH);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    @Override
    public WebSearchProviderResult search(WebSearchRequest request) {
        throw new WebResearchException(WebResearchFailureCode.NOT_CONFIGURED,
                "OpenAI Web Search requires a configured gateway route");
    }

    @Override
    public WebSearchProviderResult search(WebSearchRequest request, WebProviderRoute route) {
        if (route == null || !route.complete()) return search(request);
        AiGatewayConnection connection = requireConnection(route);
        return search(gatewayClients.apply(connection), "/responses", route.targetId(), request);
    }

    private WebSearchProviderResult search(RestClient client, String path, String model,
            WebSearchRequest request) {
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("type", "web_search");
        tool.put("search_context_size", properties.searchContextSize());
        if (!request.allowedDomains().isEmpty()) {
            tool.put("filters", Map.of("allowed_domains", request.allowedDomains()));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("input", searchPrompt(request));
        body.put("tools", List.of(tool));
        body.put("include", List.of("web_search_call.action.sources"));
        body.put("store", false);

        try {
            Map<?, ?> response = client.post().uri(path).body(body).retrieve().body(Map.class);
            if (response == null) {
                throw new WebResearchException(WebResearchFailureCode.UNAVAILABLE,
                        "OpenAI returned an empty web-search response");
            }
            return parse(response, request);
        } catch (RestClientResponseException exception) {
            WebResearchFailureCode code = exception.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS
                    ? WebResearchFailureCode.RATE_LIMITED
                    : exception.getStatusCode().is5xxServerError()
                            ? WebResearchFailureCode.UNAVAILABLE : WebResearchFailureCode.INVALID_REQUEST;
            throw new WebResearchException(code,
                    "OpenAI web search failed with HTTP " + exception.getStatusCode().value(), exception);
        } catch (ResourceAccessException exception) {
            throw new WebResearchException(WebResearchFailureCode.UNAVAILABLE,
                    "OpenAI web search could not be reached", exception);
        }
    }

    private AiGatewayConnection requireConnection(WebProviderRoute route) {
        if (gateways == null) {
            throw new WebResearchException(WebResearchFailureCode.NOT_CONFIGURED,
                    "AI gateway routing is not available");
        }
        AiGatewayConnection connection = gateways.require(route.gatewayId());
        if (connection.type() != AiGatewayType.OPENAI
                || !connection.supports(AiGatewayCapability.WEB_SEARCH)) {
            throw new WebResearchException(WebResearchFailureCode.UNSUPPORTED,
                    "The selected gateway does not support OpenAI Web Search");
        }
        return connection;
    }

    private static RestClient client(AiGatewayConnection connection) {
        HttpClient.Builder http = HttpClient.newBuilder()
                .connectTimeout(connection.timeout())
                .followRedirects(HttpClient.Redirect.NEVER);
        if (cleartext(connection.baseUrl())) {
            http.version(HttpClient.Version.HTTP_1_1);
        }
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(http.build());
        requests.setReadTimeout(connection.timeout());
        return RestClient.builder()
                .baseUrl(connection.baseUrl())
                .defaultHeader("Authorization", "Bearer " + connection.apiKey())
                .requestFactory(requests)
                .build();
    }

    private static boolean cleartext(String baseUrl) {
        return baseUrl.regionMatches(true, 0, "http://", 0, 7);
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
        return new WebSearchProviderResult(answer.toString(),
                sources.values().stream().limit(request.maxResults()).toList());
    }

    private static void addSource(Map<String, WebSource> sources, Object urlValue, Object titleValue,
            WebSearchRequest request) {
        if (!(urlValue instanceof String url) || url.isBlank() || isBlocked(url, request.blockedDomains())) return;
        String title = titleValue instanceof String value && !value.isBlank() ? value : url;
        WebSource existing = sources.get(url);
        if (existing == null || (existing.title().equals(url) && !title.equals(url))) {
            sources.put(url, new WebSource(title, url, "", null));
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
