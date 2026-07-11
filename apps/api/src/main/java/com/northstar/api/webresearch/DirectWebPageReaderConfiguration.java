package com.northstar.api.webresearch;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DirectWebPageReaderProperties.class)
class DirectWebPageReaderConfiguration {
}
