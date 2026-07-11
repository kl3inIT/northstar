package com.northstar.worker.brief;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "northstar.brief.github")
record GitHubBriefProperties(@DefaultValue("") String token) {
}
