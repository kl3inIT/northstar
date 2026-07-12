package com.northstar.integration.news.huggingnews;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(HuggingNewsProperties.class)
class HuggingNewsConfiguration {
}
