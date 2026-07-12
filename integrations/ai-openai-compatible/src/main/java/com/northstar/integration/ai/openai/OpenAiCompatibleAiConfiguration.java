package com.northstar.integration.ai.openai;

import com.northstar.core.ai.AiRouteDefaults;
import com.northstar.core.ai.AiRouteSettingsService;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiProperties.class)
class OpenAiCompatibleAiConfiguration {

    @Bean
    @ConditionalOnMissingBean(RestClient.Builder.class)
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

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

    @Bean
    @ConditionalOnMissingBean(EmbeddingModel.class)
    EmbeddingModel routedEmbeddingModel(AiRouteSettingsService routes, AiGatewayRegistry gateways) {
        return new RoutedOpenAiEmbeddingModel(routes, gateways);
    }

    @Bean
    @ConditionalOnMissingBean(TranscriptionModel.class)
    TranscriptionModel routedTranscriptionModel(AiRouteSettingsService routes, AiGatewayRegistry gateways) {
        return new RoutedOpenAiTranscriptionModel(routes, gateways);
    }
}
