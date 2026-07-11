package com.northstar.api.study;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "northstar.speech.azure")
record SpeechAzureProperties(
        @DefaultValue("") String key,
        @DefaultValue("") String region) {
}
