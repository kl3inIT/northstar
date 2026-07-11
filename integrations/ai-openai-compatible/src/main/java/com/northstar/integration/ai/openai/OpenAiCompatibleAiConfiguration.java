package com.northstar.integration.ai.openai;

import com.northstar.core.ai.AiRouteDefaults;
import com.northstar.core.ai.AiRouteSettingsService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiProperties.class)
class OpenAiCompatibleAiConfiguration {

    @Bean
    AiRouteDefaults aiRouteDefaults(AiProperties properties) {
        return new AiRouteDefaults(properties.routeDefaults());
    }

    @Bean
    AiRouteSettingsService aiRouteSettingsService(
            com.northstar.core.ai.AiRouteSettingRepository settings,
            AiRouteDefaults defaults) {
        return new AiRouteSettingsService(settings, defaults);
    }
}
