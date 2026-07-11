package com.northstar.integration.web.openai;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OpenAiWebSearchProperties.class)
class OpenAiWebSearchConfiguration {
}
