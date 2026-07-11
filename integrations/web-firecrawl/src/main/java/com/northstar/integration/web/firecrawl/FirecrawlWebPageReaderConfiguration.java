package com.northstar.integration.web.firecrawl;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FirecrawlWebPageReaderProperties.class)
class FirecrawlWebPageReaderConfiguration {
}
