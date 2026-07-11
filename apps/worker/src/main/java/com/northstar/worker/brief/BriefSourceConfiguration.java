package com.northstar.worker.brief;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({GitHubBriefProperties.class, FirecrawlBriefProperties.class})
class BriefSourceConfiguration {
}
