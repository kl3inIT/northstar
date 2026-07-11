package com.northstar.api.assistant;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "northstar.assistant.title")
record AssistantTitleProperties(@DefaultValue("true") boolean enabled) {
}
