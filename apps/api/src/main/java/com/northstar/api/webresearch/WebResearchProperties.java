package com.northstar.api.webresearch;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "northstar.web")
public class WebResearchProperties {

    private boolean enabled = true;
    private String defaultSearchProvider = "openai";
    private String defaultPageReader = "direct";
    private boolean fallbackEnabled;
    private List<String> searchFallbackOrder = new ArrayList<>();
    private List<String> pageReaderFallbackOrder = new ArrayList<>();
    private Duration cacheTtl = Duration.ofMinutes(15);
    private long cacheMaxSize = 200;
    private final OpenAi openai = new OpenAi();
    private final Direct direct = new Direct();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDefaultSearchProvider() {
        return defaultSearchProvider;
    }

    public void setDefaultSearchProvider(String defaultSearchProvider) {
        this.defaultSearchProvider = defaultSearchProvider;
    }

    public String getDefaultPageReader() {
        return defaultPageReader;
    }

    public void setDefaultPageReader(String defaultPageReader) {
        this.defaultPageReader = defaultPageReader;
    }

    public boolean isFallbackEnabled() {
        return fallbackEnabled;
    }

    public void setFallbackEnabled(boolean fallbackEnabled) {
        this.fallbackEnabled = fallbackEnabled;
    }

    public List<String> getSearchFallbackOrder() {
        return searchFallbackOrder;
    }

    public void setSearchFallbackOrder(List<String> searchFallbackOrder) {
        this.searchFallbackOrder = searchFallbackOrder;
    }

    public List<String> getPageReaderFallbackOrder() {
        return pageReaderFallbackOrder;
    }

    public void setPageReaderFallbackOrder(List<String> pageReaderFallbackOrder) {
        this.pageReaderFallbackOrder = pageReaderFallbackOrder;
    }

    public Duration getCacheTtl() {
        return cacheTtl;
    }

    public void setCacheTtl(Duration cacheTtl) {
        this.cacheTtl = cacheTtl;
    }

    public long getCacheMaxSize() {
        return cacheMaxSize;
    }

    public void setCacheMaxSize(long cacheMaxSize) {
        this.cacheMaxSize = cacheMaxSize;
    }

    public OpenAi getOpenai() {
        return openai;
    }

    public Direct getDirect() {
        return direct;
    }

    public static class OpenAi {
        private String apiKey = "";
        private String model = "gpt-5.5";
        private String searchContextSize = "medium";
        private Duration connectTimeout = Duration.ofSeconds(5);
        private Duration requestTimeout = Duration.ofSeconds(60);

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getSearchContextSize() {
            return searchContextSize;
        }

        public void setSearchContextSize(String searchContextSize) {
            this.searchContextSize = searchContextSize;
        }

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Duration getRequestTimeout() {
            return requestTimeout;
        }

        public void setRequestTimeout(Duration requestTimeout) {
            this.requestTimeout = requestTimeout;
        }
    }

    public static class Direct {
        private int maxBytes = 2 * 1024 * 1024;
        private int maxCharacters = 40_000;
        private int maxRedirects = 4;
        private Duration connectTimeout = Duration.ofSeconds(5);
        private Duration requestTimeout = Duration.ofSeconds(10);

        public int getMaxBytes() {
            return maxBytes;
        }

        public void setMaxBytes(int maxBytes) {
            this.maxBytes = maxBytes;
        }

        public int getMaxCharacters() {
            return maxCharacters;
        }

        public void setMaxCharacters(int maxCharacters) {
            this.maxCharacters = maxCharacters;
        }

        public int getMaxRedirects() {
            return maxRedirects;
        }

        public void setMaxRedirects(int maxRedirects) {
            this.maxRedirects = maxRedirects;
        }

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Duration getRequestTimeout() {
            return requestTimeout;
        }

        public void setRequestTimeout(Duration requestTimeout) {
            this.requestTimeout = requestTimeout;
        }
    }
}
