package com.northstar.api.study;

import com.northstar.core.study.SpeechAssessor;
import com.northstar.integration.speech.azure.AzureSpeechAssessor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SpeechAzureProperties.class)
class SpeechAzureConfig {

    @Bean
    @Conditional(SpeechConfiguredCondition.class)
    SpeechAssessor speechAssessor(SpeechAzureProperties properties, ObjectMapper json) {
        return new AzureSpeechAssessor(properties.key(), properties.region(), json);
    }
}
