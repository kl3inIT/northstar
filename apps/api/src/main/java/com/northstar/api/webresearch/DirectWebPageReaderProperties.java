package com.northstar.api.webresearch;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "northstar.web.direct")
record DirectWebPageReaderProperties(
        @DefaultValue("2097152") int maxBytes,
        @DefaultValue("40000") int maxCharacters,
        @DefaultValue("4") int maxRedirects,
        @DefaultValue("5s") Duration connectTimeout,
        @DefaultValue("10s") Duration requestTimeout) {
}
