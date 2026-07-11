package com.northstar.integration.web.openai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "northstar.web.openai")
public record OpenAiWebSearchProperties(
        @DefaultValue("") String apiKey,
        @DefaultValue("gpt-5.5") String model,
        @DefaultValue("medium") String searchContextSize,
        @DefaultValue("5s") Duration connectTimeout,
        @DefaultValue("60s") Duration requestTimeout) {
}
