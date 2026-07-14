package com.northstar.integration.news.huggingnews;

import com.northstar.core.cache.ExactCache;
import com.northstar.core.cache.ExactCacheNames;
import com.northstar.core.brief.BriefFeed;
import com.northstar.core.brief.BriefFeedException;
import com.northstar.core.brief.BriefFeedProvider;
import com.northstar.core.brief.BriefStoryDetail;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Bounded, cached adapter over HuggingNews' public SvelteKit page-data routes. */
@Component
public class HuggingNewsFeedProvider implements BriefFeedProvider {

    private static final int MAX_FEED_BYTES = 2_000_000;
    private static final int MAX_DETAIL_BYTES = 300_000;
    private static final Pattern ROUTE_PART = Pattern.compile("[a-z0-9][a-z0-9-]{0,159}");

    private final HuggingNewsProperties properties;
    private final HuggingNewsParser parser;
    private final HttpClient http;
    private final URI base;
    private final Clock clock;
    private final Object feedLock = new Object();
    private final ExactCache<String, BriefStoryDetail> details;
    private volatile Cached<BriefFeed> feed;

    @Autowired
    HuggingNewsFeedProvider(HuggingNewsProperties properties, ObjectMapper json,
            CacheManager cacheManager) {
        this(properties, json, HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build(), Clock.systemUTC(), cacheManager);
    }

    HuggingNewsFeedProvider(HuggingNewsProperties properties, ObjectMapper json,
            HttpClient http, Clock clock, CacheManager cacheManager) {
        this.properties = properties;
        this.parser = new HuggingNewsParser(json);
        this.http = http;
        this.clock = clock;
        this.base = validBase(properties.baseUrl());
        this.details = ExactCache.from(cacheManager, ExactCacheNames.HUGGINGNEWS_DETAIL);
    }

    @Override
    public BriefFeed feed() {
        Instant now = clock.instant();
        Cached<BriefFeed> current = feed;
        if (current != null && current.fresh(now)) return current.value();
        synchronized (feedLock) {
            current = feed;
            if (current != null && current.fresh(now)) return current.value();
            try {
                BriefFeed fetched = parser.feed(get(base.resolve("/__data.json"), MAX_FEED_BYTES));
                feed = new Cached<>(fetched, now.plus(properties.feedCacheTtl()));
                return fetched;
            } catch (RuntimeException exception) {
                if (current != null) {
                    BriefFeed stale = current.value();
                    return new BriefFeed(stale.provider(), stale.updatedAt(), true, stale.tldr(), stale.days());
                }
                throw exception;
            }
        }
    }

    @Override
    public BriefStoryDetail story(String topic, String slug) {
        requireRoutePart(topic, "topic");
        requireRoutePart(slug, "slug");
        String key = topic + "/" + slug;
        BriefStoryDetail cached = details.find(key).orElse(null);
        if (cached != null) return cached;
        BriefStoryDetail fetched = parser.detail(get(base.resolve("/" + key + "/__data.json"), MAX_DETAIL_BYTES));
        details.put(key, fetched);
        return fetched;
    }

    private String get(URI uri, int maxBytes) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(properties.requestTimeout())
                .header("Accept", "application/json")
                .header("Accept-Encoding", "identity")
                .header("User-Agent", "NorthstarBriefs/1.0")
                .GET().build();
        try {
            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                if (response.statusCode() != 200) {
                    throw new BriefFeedException("HuggingNews returned HTTP " + response.statusCode());
                }
                byte[] bytes = body.readNBytes(maxBytes + 1);
                if (bytes.length > maxBytes) throw new BriefFeedException("HuggingNews response exceeded the size limit");
                return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BriefFeedException("HuggingNews request was interrupted", exception);
        } catch (IOException exception) {
            throw new BriefFeedException("HuggingNews request failed", exception);
        }
    }

    private static URI validBase(String value) {
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException("HuggingNews base URL must be a public HTTPS origin");
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid HuggingNews base URL", exception);
        }
    }

    private static void requireRoutePart(String value, String label) {
        if (value == null || !ROUTE_PART.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid HuggingNews " + label);
        }
    }

    private record Cached<T>(T value, Instant expiresAt) {
        boolean fresh(Instant now) {
            return now.isBefore(expiresAt);
        }
    }
}
