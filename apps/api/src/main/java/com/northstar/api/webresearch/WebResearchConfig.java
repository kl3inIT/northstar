package com.northstar.api.webresearch;

import com.northstar.core.web.WebResearchDefaults;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(WebResearchProperties.class)
class WebResearchConfig {

    @Bean
    WebResearchDefaults webResearchDefaults(WebResearchProperties properties) {
        return new WebResearchDefaults(
                properties.isEnabled(),
                properties.getDefaultSearchProvider(),
                properties.getDefaultPageReader(),
                properties.isFallbackEnabled(),
                properties.getSearchFallbackOrder(),
                properties.getPageReaderFallbackOrder(),
                properties.getCacheTtl(),
                properties.getCacheMaxSize());
    }
}
