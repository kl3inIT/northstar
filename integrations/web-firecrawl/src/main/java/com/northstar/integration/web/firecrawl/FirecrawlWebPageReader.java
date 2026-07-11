package com.northstar.integration.web.firecrawl;

import com.northstar.core.web.WebPageProviderResult;
import com.northstar.core.web.WebPageReader;
import com.northstar.core.web.WebPageRequest;
import com.northstar.core.web.WebResearchException;
import com.northstar.core.web.WebResearchFailureCode;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

@Component
public class FirecrawlWebPageReader implements WebPageReader {

    private final FirecrawlWebPageReaderProperties properties;
    private final RestClient firecrawl;
    private final ObjectMapper json;

    @Autowired
    FirecrawlWebPageReader(FirecrawlWebPageReaderProperties properties, ObjectMapper json) {
        this(properties, client(properties), json);
    }

    public FirecrawlWebPageReader(FirecrawlWebPageReaderProperties properties,
            RestClient firecrawl, ObjectMapper json) {
        this.properties = properties;
        this.firecrawl = firecrawl;
        this.json = json;
    }

    private static RestClient client(FirecrawlWebPageReaderProperties properties) {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(http);
        requests.setReadTimeout(properties.requestTimeout());
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader("Authorization", "Bearer " + properties.apiKey())
                .requestFactory(requests)
                .build();
    }

    @Override
    public String id() {
        return "firecrawl";
    }

    @Override
    public String displayName() {
        return "Firecrawl Page Reader";
    }

    @Override
    public boolean configured() {
        if (!StringUtils.hasText(properties.apiKey())) return false;
        try {
            URI base = URI.create(properties.baseUrl());
            return "https".equalsIgnoreCase(base.getScheme()) && base.getHost() != null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
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
        if (!configured()) {
            throw new WebResearchException(WebResearchFailureCode.NOT_CONFIGURED,
                    "FIRECRAWL_API_KEY is not configured");
        }
        if (!supports(request.url())) {
            throw new WebResearchException(WebResearchFailureCode.BLOCKED,
                    "Firecrawl reads only public HTTP(S) URLs without embedded credentials");
        }

        Map<String, Object> body = Map.of(
                "url", request.url().toString(),
                "formats", List.of("markdown"),
                "onlyMainContent", true,
                "proxy", "basic",
                "timeout", properties.requestTimeout().toMillis());
        try {
            byte[] response = firecrawl.post().uri("/v2/scrape").body(body).retrieve()
                    .onStatus(HttpStatusCode::isError, (_, raw) -> {
                        throw failure(raw.getStatusCode());
                    })
                    .body(byte[].class);
            if (response == null || response.length == 0) {
                throw new WebResearchException(WebResearchFailureCode.UNAVAILABLE,
                        "Firecrawl returned an empty scrape response");
            }
            if (response.length > properties.maxResponseBytes()) {
                throw new WebResearchException(WebResearchFailureCode.RESPONSE_TOO_LARGE,
                        "Firecrawl response exceeded the configured byte limit");
            }
            return parse(request.url(), json.readValue(response, Map.class));
        } catch (WebResearchException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            throw new WebResearchException(WebResearchFailureCode.UNAVAILABLE,
                    "Firecrawl could not be reached", exception);
        } catch (Exception exception) {
            throw new WebResearchException(WebResearchFailureCode.UNAVAILABLE,
                    "Firecrawl returned an invalid scrape response", exception);
        }
    }

    private WebPageProviderResult parse(URI requestedUrl, Map<?, ?> response) {
        if (!Boolean.TRUE.equals(response.get("success"))) {
            throw new WebResearchException(WebResearchFailureCode.UNAVAILABLE,
                    "Firecrawl did not complete the scrape");
        }
        Map<?, ?> data = map(response.get("data"));
        Map<?, ?> metadata = map(data.get("metadata"));
        String content = string(data.get("markdown")).strip();
        if (content.isBlank()) {
            throw new WebResearchException(WebResearchFailureCode.UNSUPPORTED,
                    "Firecrawl returned no readable page content");
        }
        URI finalUrl = uri(string(metadata.get("sourceURL")), requestedUrl);
        String title = string(metadata.get("title")).strip();
        if (title.isBlank()) title = finalUrl.getHost();
        boolean truncated = content.length() > properties.maxCharacters();
        if (truncated) content = content.substring(0, properties.maxCharacters()).stripTrailing();
        return new WebPageProviderResult(finalUrl, title, content, "text/markdown", truncated);
    }

    private static WebResearchException failure(HttpStatusCode status) {
        WebResearchFailureCode code = switch (status.value()) {
            case 401, 403 -> WebResearchFailureCode.NOT_CONFIGURED;
            case 402, 429 -> WebResearchFailureCode.RATE_LIMITED;
            default -> status.is5xxServerError()
                    ? WebResearchFailureCode.UNAVAILABLE : WebResearchFailureCode.INVALID_REQUEST;
        };
        return new WebResearchException(code, "Firecrawl scrape failed with HTTP " + status.value());
    }

    private static URI uri(String value, URI fallback) {
        try {
            URI parsed = URI.create(value);
            return parsed.getHost() == null ? fallback : parsed;
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    private static String string(Object value) {
        return value instanceof String text ? text : "";
    }

    private static Map<?, ?> map(Object value) {
        return value instanceof Map<?, ?> values ? values : Map.of();
    }
}
