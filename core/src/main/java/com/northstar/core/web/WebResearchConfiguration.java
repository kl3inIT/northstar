package com.northstar.core.web;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(WebResearchProperties.class)
class WebResearchConfiguration {

    @Bean
    WebResearchDefaults webResearchDefaults(WebResearchProperties properties) {
        return new WebResearchDefaults(properties.enabled(), properties.defaultSearchProvider(),
                properties.defaultPageReader(), properties.fallbackEnabled(),
                properties.searchFallbackOrder(), properties.pageReaderFallbackOrder(),
                properties.cacheTtl(), properties.cacheMaxSize());
    }
}
