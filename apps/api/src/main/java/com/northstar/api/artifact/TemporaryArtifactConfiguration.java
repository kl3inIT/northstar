package com.northstar.api.artifact;

import com.github.benmanes.caffeine.cache.Ticker;
import com.northstar.core.artifact.TemporaryArtifactStore;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TemporaryArtifactProperties.class)
class TemporaryArtifactConfiguration {

    @Bean
    @ConditionalOnMissingBean(TemporaryArtifactStore.class)
    TemporaryArtifactStore temporaryArtifactStore(TemporaryArtifactProperties properties) {
        return new CaffeineTemporaryArtifactStore(properties, Clock.systemUTC(), Ticker.systemTicker());
    }
}
