package com.northstar.core.web;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class WebResearchService {

    private final WebResearchSettingsService settings;
    private final WebProviderRegistry providers;
    private final Clock clock;
    private final Cache<SearchKey, WebSearchResult> searchCache;
    private final Cache<PageKey, WebPageResult> pageCache;

    @Autowired
    WebResearchService(WebResearchSettingsService settings, WebProviderRegistry providers) {
        this(settings, providers, Clock.systemUTC());
    }

    WebResearchService(WebResearchSettingsService settings, WebProviderRegistry providers, Clock clock) {
        this.settings = settings;
        this.providers = providers;
        this.clock = clock;
        WebResearchDefaults defaults = settings.defaults();
        this.searchCache = Caffeine.newBuilder()
                .maximumSize(defaults.cacheMaxSize())
                .expireAfterWrite(defaults.cacheTtl())
                .build();
        this.pageCache = Caffeine.newBuilder()
                .maximumSize(defaults.cacheMaxSize())
                .expireAfterWrite(defaults.cacheTtl())
                .build();
    }

    public WebSearchResult search(WebSearchRequest request) {
        WebResearchSettings current = requireEnabled();
        SearchKey key = new SearchKey(current.searchProviderId(), current.fallbackEnabled(),
                settings.defaults().searchFallbackOrder(), request);
        WebSearchResult cached = searchCache.getIfPresent(key);
        if (cached != null) return cached;

        List<String> candidates = candidates(current.searchProviderId(),
                current.fallbackEnabled(), settings.defaults().searchFallbackOrder());
        WebSearchResult result = route(candidates, id -> {
            WebSearchProvider provider = providers.searchProvider(id);
            requireConfigured(provider.configured(), "search provider", id);
            WebSearchProviderResult found = provider.search(request);
            return new WebSearchResult(request.query(), id, found.answer(), found.sources(),
                    Instant.now(clock), id.equals(current.searchProviderId()) ? null : current.searchProviderId());
        });
        searchCache.put(key, result);
        return result;
    }

    public WebPageResult read(WebPageRequest request) {
        WebResearchSettings current = requireEnabled();
        PageKey key = new PageKey(current.pageReaderId(), current.fallbackEnabled(),
                settings.defaults().pageReaderFallbackOrder(), request.url());
        WebPageResult cached = pageCache.getIfPresent(key);
        if (cached != null) return cached;

        List<String> candidates = candidates(current.pageReaderId(),
                current.fallbackEnabled(), settings.defaults().pageReaderFallbackOrder());
        WebPageResult result = route(candidates, id -> {
            WebPageReader reader = providers.pageReader(id);
            requireConfigured(reader.configured(), "page reader", id);
            if (!reader.supports(request.url())) {
                throw new WebResearchException(WebResearchFailureCode.UNSUPPORTED,
                        "Reader " + id + " does not support this URL");
            }
            WebPageProviderResult page = reader.read(request);
            return new WebPageResult(request.url(), page.finalUrl(), id, page.title(), page.content(),
                    page.contentType(), page.truncated(), Instant.now(clock),
                    id.equals(current.pageReaderId()) ? null : current.pageReaderId());
        });
        pageCache.put(key, result);
        return result;
    }

    private WebResearchSettings requireEnabled() {
        WebResearchSettings current = settings.current();
        if (!current.enabled()) {
            throw new WebResearchException(WebResearchFailureCode.DISABLED,
                    "Web research is disabled in Settings");
        }
        return current;
    }

    private static void requireConfigured(boolean configured, String kind, String id) {
        if (!configured) {
            throw new WebResearchException(WebResearchFailureCode.NOT_CONFIGURED,
                    "The selected " + kind + " is not configured: " + id);
        }
    }

    private static List<String> candidates(String selected, boolean fallbackEnabled, List<String> fallbackOrder) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        ids.add(selected);
        if (fallbackEnabled) ids.addAll(fallbackOrder);
        return List.copyOf(ids);
    }

    private static <T> T route(List<String> candidates, Function<String, T> call) {
        List<WebResearchException> failures = new ArrayList<>();
        for (int index = 0; index < candidates.size(); index++) {
            try {
                return call.apply(candidates.get(index));
            } catch (WebResearchException exception) {
                failures.add(exception);
                if (!exception.isRetryable() || index == candidates.size() - 1) throw exception;
            }
        }
        throw failures.getLast();
    }

    private record SearchKey(String providerId, boolean fallbackEnabled,
            List<String> fallbackOrder, WebSearchRequest request) {
    }

    private record PageKey(String readerId, boolean fallbackEnabled,
            List<String> fallbackOrder, java.net.URI url) {
    }
}
