package com.northstar.core.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class NorthstarCacheConfiguration {

    @Bean
    @ConditionalOnMissingBean(SemanticCachePolicy.class)
    SemanticCachePolicy semanticCachePolicy() {
        return new SemanticCachePolicy();
    }

    @Bean
    @ConditionalOnMissingBean(SemanticResponseCache.class)
    SemanticResponseCache semanticResponseCache() {
        return new DisabledSemanticResponseCache();
    }

    @Bean
    @ConditionalOnMissingBean(CacheManager.class)
    CacheManager northstarCacheManager(List<ExactCacheSpec> specs) {
        Map<String, ExactCacheSpec> byName = new LinkedHashMap<>();
        for (ExactCacheSpec spec : specs) {
            if (byName.putIfAbsent(spec.name(), spec) != null) {
                throw new IllegalStateException("Duplicate cache specification: " + spec.name());
            }
        }

        CaffeineCacheManager manager = new CaffeineCacheManager(
                byName.keySet().toArray(String[]::new));
        manager.setAllowNullValues(false);
        byName.values().forEach(spec -> manager.registerCustomCache(spec.name(),
                Caffeine.newBuilder()
                        .maximumSize(spec.maximumSize())
                        .expireAfterWrite(spec.ttl())
                        .recordStats()
                        .build()));
        return manager;
    }
}
