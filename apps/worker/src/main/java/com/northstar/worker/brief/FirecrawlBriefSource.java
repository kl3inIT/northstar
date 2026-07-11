package com.northstar.worker.brief;

import static com.northstar.worker.brief.BriefSourceSupport.effectiveQueries;
import static com.northstar.worker.brief.BriefSourceSupport.instant;
import static com.northstar.worker.brief.BriefSourceSupport.integer;
import static com.northstar.worker.brief.BriefSourceSupport.list;
import static com.northstar.worker.brief.BriefSourceSupport.map;
import static com.northstar.worker.brief.BriefSourceSupport.plainText;
import static com.northstar.worker.brief.BriefSourceSupport.string;

import com.northstar.core.brief.BriefCandidate;
import com.northstar.core.brief.BriefCollectionRequest;
import com.northstar.core.brief.BriefKind;
import com.northstar.core.brief.BriefSourceProvider;
import com.northstar.core.brief.BriefSourceResult;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
class FirecrawlBriefSource implements BriefSourceProvider {

    private static final int RESULT_LIMIT = 3;
    private static final int CREDITS_PER_QUERY = 2 + RESULT_LIMIT;
    private static final int MAX_QUERIES = 4;
    private static final Set<String> OFFICIAL_DOMAINS = Set.of(
            "openai.com", "anthropic.com", "github.com", "github.blog", "flutter.dev",
            "dart.dev", "spring.io", "react.dev", "inside.java", "openjdk.org");
    private static final Set<String> COMMUNITY_DOMAINS = Set.of(
            "news.ycombinator.com", "reddit.com", "lobste.rs");

    private final BriefHttpClient http;
    private final ObjectMapper json;
    private final FirecrawlBriefProperties properties;

    FirecrawlBriefSource(BriefHttpClient http, ObjectMapper json, FirecrawlBriefProperties properties) {
        this.http = http;
        this.json = json;
        this.properties = properties;
    }

    @Override
    public String id() {
        return "firecrawl";
    }

    @Override
    public String displayName() {
        return "Firecrawl discovery";
    }

    @Override
    public boolean configured() {
        try {
            URI base = URI.create(properties.baseUrl());
            return !properties.apiKey().isBlank()
                    && "https".equalsIgnoreCase(base.getScheme()) && base.getHost() != null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    @Override
    public BriefSourceResult collect(BriefCollectionRequest request) {
        List<String> queries = effectiveQueries(request.queries(), request.topics(), MAX_QUERIES);
        int affordable = request.firecrawlCreditBudget() / CREDITS_PER_QUERY;
        queries = queries.stream().limit(Math.min(MAX_QUERIES, affordable)).toList();
        if (queries.isEmpty()) return new BriefSourceResult(List.of(), Map.of(
                "creditsUsed", 0,
                "creditBudget", request.firecrawlCreditBudget()));

        List<SearchOutcome> outcomes;
        try (ExecutorService executor = Executors.newFixedThreadPool(Math.min(2, queries.size()),
                Thread.ofVirtual().factory())) {
            List<Callable<SearchOutcome>> calls = queries.stream()
                    .<Callable<SearchOutcome>>map(query -> () -> search(query, request)).toList();
            outcomes = executor.invokeAll(calls).stream().map(future -> {
                try {
                    return future.get();
                } catch (Exception exception) {
                    return new SearchOutcome(List.of(), exception, 0);
                }
            }).toList();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Firecrawl collection was interrupted", exception);
        }
        long failed = outcomes.stream().filter(outcome -> outcome.failure() != null).count();
        if (failed == outcomes.size()) throw new IllegalStateException("Every Firecrawl search failed");
        List<BriefCandidate> items = outcomes.stream().flatMap(outcome -> outcome.items().stream()).toList();
        return new BriefSourceResult(items, Map.of(
                "queries", outcomes.size(),
                "failedQueries", failed,
                "creditsUsed", outcomes.stream().mapToInt(SearchOutcome::creditsUsed).sum(),
                "creditBudget", request.firecrawlCreditBudget()));
    }

    private SearchOutcome search(String query, BriefCollectionRequest request) {
        try {
            Map<String, Object> scrapeOptions = new LinkedHashMap<>();
            scrapeOptions.put("formats", List.of(Map.of("type", "markdown")));
            scrapeOptions.put("onlyMainContent", true);
            scrapeOptions.put("proxy", "basic");

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("query", query);
            body.put("limit", RESULT_LIMIT);
            body.put("sources", List.of(Map.of("type", "web")));
            body.put("tbs", timeFilter(request.since()));
            body.put("ignoreInvalidURLs", true);
            if (!request.blockedDomains().isEmpty()) body.put("excludeDomains", request.blockedDomains());
            body.put("scrapeOptions", scrapeOptions);

            Map<String, String> headers = properties.apiKey().isBlank()
                    ? Map.of() : Map.of("Authorization", "Bearer " + properties.apiKey());
            String responseBody = http.postJson(URI.create(properties.baseUrl().replaceAll("/+$", "") + "/v2/search"),
                    json.writeValueAsString(body), headers, properties.requestTimeout());
            Map<?, ?> response = map(json.readValue(responseBody, Map.class));
            List<BriefCandidate> items = new ArrayList<>();
            for (Object value : responseItems(response.get("data"))) {
                Map<?, ?> item = map(value);
                String title = string(item.get("title"));
                String url = string(item.get("url"));
                Map<?, ?> metadata = map(item.get("metadata"));
                if (title.isBlank()) title = string(metadata.get("title"));
                if (url.isBlank()) url = string(metadata.get("sourceURL"));
                String summary = plainText(string(item.get("markdown")), 480);
                if (summary.isBlank()) summary = plainText(string(item.get("description")), 480);
                Instant publishedAt = firstInstant(item.get("publishedDate"), item.get("date"),
                        metadata.get("publishedTime"), metadata.get("date"));
                if (!title.isBlank() && !url.isBlank()) {
                    String host = URI.create(url).getHost();
                    items.add(new BriefCandidate(kind(host), title, url, summary,
                            host == null ? "Firecrawl" : host, "", publishedAt, 0));
                }
            }
            return new SearchOutcome(items, null, integer(response.get("creditsUsed")));
        } catch (Exception exception) {
            return new SearchOutcome(List.of(), exception, 0);
        }
    }

    private static List<?> responseItems(Object data) {
        if (data instanceof List<?>) return list(data);
        Map<?, ?> grouped = map(data);
        List<Object> combined = new ArrayList<>();
        combined.addAll(list(grouped.get("web")));
        combined.addAll(list(grouped.get("news")));
        return combined;
    }

    private static String timeFilter(Instant since) {
        long hours = Math.max(1, ChronoUnit.HOURS.between(since, Instant.now()));
        if (hours <= 24) return "qdr:d";
        if (hours <= 24 * 7) return "qdr:w";
        return "qdr:m";
    }

    private static Instant firstInstant(Object... values) {
        for (Object value : values) {
            Instant parsed = instant(value);
            if (parsed != null) return parsed;
        }
        return null;
    }

    private static BriefKind kind(String host) {
        if (host == null) return BriefKind.PEOPLE;
        String normalized = host.toLowerCase(Locale.ROOT);
        if (matchesDomain(normalized, OFFICIAL_DOMAINS)) return BriefKind.OFFICIAL;
        if (matchesDomain(normalized, COMMUNITY_DOMAINS)) return BriefKind.COMMUNITY;
        return BriefKind.PEOPLE;
    }

    private static boolean matchesDomain(String host, Set<String> domains) {
        return domains.stream().anyMatch(domain -> host.equals(domain) || host.endsWith("." + domain));
    }

    private record SearchOutcome(List<BriefCandidate> items, Exception failure, int creditsUsed) {
    }
}
