package com.northstar.api.capture;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "northstar.capture.realtime")
record RealtimeCaptureProperties(
        @DefaultValue("https://api.openai.com") String baseUrl,
        String apiKey,
        @DefaultValue("gpt-realtime-whisper") String model) {

    RealtimeCaptureProperties {
        apiKey = apiKey == null ? "" : apiKey.strip();
    }
}
