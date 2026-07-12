package com.northstar.integration.web.ninerouter;

import com.northstar.core.ai.AiGatewayConnection;
import com.northstar.core.ai.AiGatewayConnectionResolver;
import com.northstar.core.ai.AiGatewayCapability;
import com.northstar.core.ai.AiGatewayType;
import com.northstar.core.web.WebPageProviderResult;
import com.northstar.core.web.WebPageReader;
import com.northstar.core.web.WebPageRequest;
import com.northstar.core.web.WebProviderRoute;
import com.northstar.core.web.WebRecency;
import com.northstar.core.web.WebResearchException;
import com.northstar.core.web.WebResearchFailureCode;
import com.northstar.core.web.WebSearchProvider;
import com.northstar.core.web.WebSearchProviderResult;
import com.northstar.core.web.WebSearchRequest;
import com.northstar.core.web.WebSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.ObjectMapper;

@Component
public class NineRouterWebProvider implements WebSearchProvider, WebPageReader {

    private static final int MAX_RESPONSE_BYTES = 2_000_000;
    private static final int MAX_PAGE_CHARACTERS = 40_000;

    private final AiGatewayConnectionResolver gateways;
    private final ObjectMapper json;
    private final Function<AiGatewayConnection, RestClient> clients;

    @Autowired
    NineRouterWebProvider(AiGatewayConnectionResolver gateways, ObjectMapper json) {
        this(gateways, json, NineRouterWebProvider::client);
    }

    NineRouterWebProvider(AiGatewayConnectionResolver gateways, ObjectMapper json,
            Function<AiGatewayConnection, RestClient> clients) {
        this.gateways = gateways;
        this.json = json;
        this.clients = clients;
    }

    @Override
    public String id() {
        return "nine-router";
    }

    @Override
    public String displayName() {
        return "9Router Web";
    }

    @Override
    public boolean routeRequired() {
        return true;
    }

    @Override
    public Set<AiGatewayType> gatewayTypes() {
        return Set.of(AiGatewayType.NINE_ROUTER);
    }

    @Override
    public boolean configured() {
        return false;
    }

    @Override
    public boolean configured(WebProviderRoute route) {
        if (route == null || !route.complete()) return false;
        try {
            AiGatewayConnection gateway = gateways.require(route.gatewayId());
            return gateway.type() == AiGatewayType.NINE_ROUTER
                    && gateway.supports(AiGatewayCapability.WEB_SEARCH)
                    && gateway.supports(AiGatewayCapability.WEB_FETCH);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    @Override
    public WebSearchProviderResult search(WebSearchRequest request) {
        throw missingRoute();
    }

    @Override
    public WebSearchProviderResult search(WebSearchRequest request, WebProviderRoute route) {
        AiGatewayConnection gateway = require(route);
        requireCapability(gateway, AiGatewayCapability.WEB_SEARCH);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", route.targetId());
        body.put("query", request.query());
        body.put("search_type", "web");
        body.put("max_results", request.maxResults());
        if (request.recency() != WebRecency.ANY) {
            body.put("time_range", request.recency().name().toLowerCase(Locale.ROOT));
        }
        if (!request.allowedDomains().isEmpty()) {
            body.put("domain_filter", request.allowedDomains());
        }

        Map<?, ?> response = post(gateway, "/search", body);
        List<WebSource> sources = new ArrayList<>();
        for (Object value : list(response.get("results"))) {
            Map<?, ?> result = map(value);
            String url = string(result.get("url"));
            if (!accepted(url, request.allowedDomains(), request.blockedDomains())) continue;
            sources.add(new WebSource(string(result.get("title")), url,
                    string(result.get("snippet")), instant(result.get("published_at"))));
            if (sources.size() == request.maxResults()) break;
        }
        return new WebSearchProviderResult(string(response.get("answer")), sources);
    }

    @Override
    public boolean supports(URI url) {
        if (url == null || url.getHost() == null || url.getUserInfo() != null) return false;
        if (!("http".equalsIgnoreCase(url.getScheme()) || "https".equalsIgnoreCase(url.getScheme()))) return false;
        String host = url.getHost().toLowerCase(Locale.ROOT);
        return !host.equals("localhost") && !host.endsWith(".localhost") && !host.endsWith(".local");
    }

    @Override
    public WebPageProviderResult read(WebPageRequest request) {
        throw missingRoute();
    }

    @Override
    public WebPageProviderResult read(WebPageRequest request, WebProviderRoute route) {
        if (!supports(request.url())) {
            throw new WebResearchException(WebResearchFailureCode.BLOCKED,
                    "9Router reads only public HTTP(S) URLs without embedded credentials");
        }
        AiGatewayConnection gateway = require(route);
        requireCapability(gateway, AiGatewayCapability.WEB_FETCH);
        Map<String, Object> body = Map.of(
                "model", route.targetId(),
                "url", request.url().toString(),
                "format", "markdown",
                "max_characters", MAX_PAGE_CHARACTERS);
        Map<?, ?> response = post(gateway, "/web/fetch", body);
        Map<?, ?> content = map(response.get("content"));
        String text = string(content.get("text")).strip();
        if (text.isBlank()) {
            throw new WebResearchException(WebResearchFailureCode.UNSUPPORTED,
                    "9Router returned no readable page content");
        }
        URI finalUrl = uri(string(response.get("url")), request.url());
        String title = string(response.get("title")).strip();
        if (title.isBlank()) title = finalUrl.getHost();
        boolean truncated = number(content.get("length")) > text.length()
                || text.length() >= MAX_PAGE_CHARACTERS;
        return new WebPageProviderResult(finalUrl, title, text, "text/markdown", truncated);
    }

    private AiGatewayConnection require(WebProviderRoute route) {
        if (route == null || !route.complete()) throw missingRoute();
        try {
            return gateways.require(route.gatewayId());
        } catch (IllegalArgumentException exception) {
            throw new WebResearchException(WebResearchFailureCode.NOT_CONFIGURED,
                    "The selected 9Router gateway is not configured", exception);
        }
    }

    private static void requireCapability(AiGatewayConnection gateway,
            AiGatewayCapability capability) {
        if (gateway.type() != AiGatewayType.NINE_ROUTER || !gateway.supports(capability)) {
            throw new WebResearchException(WebResearchFailureCode.UNSUPPORTED,
                    "The selected gateway does not support " + capability.name().toLowerCase(Locale.ROOT));
        }
    }

    private Map<?, ?> post(AiGatewayConnection gateway, String path, Map<String, Object> body) {
        try {
            byte[] response = clients.apply(gateway).post().uri(path).body(body).retrieve().body(byte[].class);
            if (response == null || response.length == 0) {
                throw new WebResearchException(WebResearchFailureCode.UNAVAILABLE,
                        "9Router returned an empty response");
            }
            if (response.length > MAX_RESPONSE_BYTES) {
                throw new WebResearchException(WebResearchFailureCode.RESPONSE_TOO_LARGE,
                        "9Router response exceeded the configured byte limit");
            }
            return json.readValue(response, Map.class);
        } catch (WebResearchException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            throw failure(exception.getStatusCode(), exception);
        } catch (ResourceAccessException exception) {
            throw new WebResearchException(WebResearchFailureCode.UNAVAILABLE,
                    "9Router could not be reached", exception);
        } catch (Exception exception) {
            throw new WebResearchException(WebResearchFailureCode.UNAVAILABLE,
                    "9Router returned an invalid response", exception);
        }
    }

    private static RestClient client(AiGatewayConnection gateway) {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(gateway.timeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(http);
        requests.setReadTimeout(gateway.timeout());
        return RestClient.builder()
                .baseUrl(gateway.baseUrl())
                .defaultHeader("Authorization", "Bearer " + gateway.apiKey())
                .requestFactory(requests)
                .build();
    }

    private static WebResearchException missingRoute() {
        return new WebResearchException(WebResearchFailureCode.NOT_CONFIGURED,
                "9Router requires a configured gateway and provider or combo target");
    }

    private static WebResearchException failure(HttpStatusCode status, Exception cause) {
        WebResearchFailureCode code = switch (status.value()) {
            case 401, 403 -> WebResearchFailureCode.NOT_CONFIGURED;
            case 402, 429 -> WebResearchFailureCode.RATE_LIMITED;
            default -> status.is5xxServerError()
                    ? WebResearchFailureCode.UNAVAILABLE : WebResearchFailureCode.INVALID_REQUEST;
        };
        return new WebResearchException(code,
                "9Router request failed with HTTP " + status.value(), cause);
    }

    private static boolean accepted(String url, List<String> allowed, List<String> blocked) {
        try {
            String host = URI.create(url).getHost();
            if (host == null) return false;
            String normalized = host.toLowerCase(Locale.ROOT);
            if (blocked.stream().anyMatch(domain -> matches(normalized, domain))) return false;
            return allowed.isEmpty() || allowed.stream().anyMatch(domain -> matches(normalized, domain));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean matches(String host, String domain) {
        return host.equals(domain) || host.endsWith("." + domain);
    }

    private static Instant instant(Object value) {
        if (!(value instanceof String text) || text.isBlank()) return null;
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private static URI uri(String value, URI fallback) {
        try {
            URI parsed = URI.create(value);
            return parsed.getHost() == null ? fallback : parsed;
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    private static long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0;
    }

    private static String string(Object value) {
        return value instanceof String text ? text : "";
    }

    private static List<?> list(Object value) {
        return value instanceof List<?> values ? values : List.of();
    }

    private static Map<?, ?> map(Object value) {
        return value instanceof Map<?, ?> values ? values : Map.of();
    }
}
