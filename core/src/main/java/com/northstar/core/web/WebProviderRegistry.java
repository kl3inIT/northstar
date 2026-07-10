package com.northstar.core.web;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class WebProviderRegistry {

    private final Map<String, WebSearchProvider> searchProviders;
    private final Map<String, WebPageReader> pageReaders;

    WebProviderRegistry(List<WebSearchProvider> searchProviders, List<WebPageReader> pageReaders) {
        this.searchProviders = indexed(searchProviders, WebSearchProvider::id, "search provider");
        this.pageReaders = indexed(pageReaders, WebPageReader::id, "page reader");
    }

    public WebSearchProvider searchProvider(String id) {
        WebSearchProvider provider = searchProviders.get(normalized(id));
        if (provider == null) {
            throw new WebResearchException(WebResearchFailureCode.NOT_CONFIGURED,
                    "Unknown web search provider: " + id);
        }
        return provider;
    }

    public WebPageReader pageReader(String id) {
        WebPageReader reader = pageReaders.get(normalized(id));
        if (reader == null) {
            throw new WebResearchException(WebResearchFailureCode.NOT_CONFIGURED,
                    "Unknown web page reader: " + id);
        }
        return reader;
    }

    public List<WebProviderDescriptor> descriptors() {
        Set<String> ids = new LinkedHashSet<>(searchProviders.keySet());
        ids.addAll(pageReaders.keySet());
        List<WebProviderDescriptor> result = new ArrayList<>();
        for (String id : ids) {
            WebSearchProvider search = searchProviders.get(id);
            WebPageReader reader = pageReaders.get(id);
            Set<WebResearchCapability> capabilities = new LinkedHashSet<>();
            if (search != null) capabilities.add(WebResearchCapability.SEARCH);
            if (reader != null) capabilities.add(WebResearchCapability.READ_PAGE);
            result.add(new WebProviderDescriptor(
                    id,
                    search != null ? search.displayName() : reader.displayName(),
                    capabilities,
                    (search == null || search.configured()) && (reader == null || reader.configured())));
        }
        return result.stream().sorted(Comparator.comparing(WebProviderDescriptor::displayName)).toList();
    }

    private static <T> Map<String, T> indexed(List<T> values, IdReader<T> ids, String kind) {
        Map<String, T> result = new LinkedHashMap<>();
        for (T value : values) {
            String id = normalized(ids.read(value));
            if (id.isBlank()) throw new IllegalStateException("A " + kind + " has a blank id");
            if (result.putIfAbsent(id, value) != null) {
                throw new IllegalStateException("Duplicate " + kind + " id: " + id);
            }
        }
        return Map.copyOf(result);
    }

    private static String normalized(String id) {
        return id == null ? "" : id.strip().toLowerCase();
    }

    @FunctionalInterface
    private interface IdReader<T> {
        String read(T value);
    }
}
