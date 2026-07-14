package com.northstar.core.web;

import com.northstar.core.cache.ExactCacheNames;
import com.northstar.core.cache.ExactCacheSpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(WebResearchProperties.class)
class WebResearchConfiguration {

    @Bean
    WebResearchDefaults webResearchDefaults(WebResearchProperties properties) {
        return new WebResearchDefaults(properties.enabled(), properties.defaultSearchProvider(),
                new WebProviderRoute(properties.defaultSearchGateway(), properties.defaultSearchTarget()),
                properties.defaultPageReader(),
                new WebProviderRoute(properties.defaultPageGateway(), properties.defaultPageTarget()),
                properties.fallbackEnabled(),
                properties.searchFallbackOrder(), properties.pageReaderFallbackOrder(),
                properties.cacheTtl(), properties.cacheMaxSize());
    }

    @Bean
    ExactCacheSpec webSearchCacheSpec(WebResearchDefaults defaults) {
        return new ExactCacheSpec(ExactCacheNames.WEB_SEARCH,
                defaults.cacheTtl(), defaults.cacheMaxSize());
    }

    @Bean
    ExactCacheSpec webPageCacheSpec(WebResearchDefaults defaults) {
        return new ExactCacheSpec(ExactCacheNames.WEB_PAGE,
                defaults.cacheTtl(), defaults.cacheMaxSize());
    }
}
