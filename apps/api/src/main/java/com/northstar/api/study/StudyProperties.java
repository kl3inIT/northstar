package com.northstar.api.study;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "northstar.study")
record StudyProperties(
        @DefaultValue("gpt-5.5") String graderModel) {
}
