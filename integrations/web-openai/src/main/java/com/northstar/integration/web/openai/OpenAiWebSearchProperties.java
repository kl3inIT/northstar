package com.northstar.integration.web.openai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "northstar.web.openai")
public record OpenAiWebSearchProperties(
        @DefaultValue("medium") String searchContextSize) {
}
