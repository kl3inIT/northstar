package com.northstar.integration.news.huggingnews;

import com.northstar.core.cache.ExactCacheNames;
import com.northstar.core.cache.ExactCacheSpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(HuggingNewsProperties.class)
class HuggingNewsConfiguration {

    @Bean
    ExactCacheSpec huggingNewsDetailCacheSpec(HuggingNewsProperties properties) {
        return new ExactCacheSpec(ExactCacheNames.HUGGINGNEWS_DETAIL,
                properties.detailCacheTtl(), properties.detailCacheMaxSize());
    }
}
