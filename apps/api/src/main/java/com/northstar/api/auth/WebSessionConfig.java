package com.northstar.api.auth;

import org.jspecify.annotations.NullMarked;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

@NullMarked
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(WebSessionProperties.class)
class WebSessionConfig {

    @Bean
    CookieSerializer cookieSerializer(WebSessionProperties properties) {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieMaxAge(properties.cookieMaxAgeSeconds());
        serializer.setUseHttpOnlyCookie(properties.httpOnly());
        serializer.setSameSite(properties.sameSite());
        serializer.setUseSecureCookie(properties.secure());
        return serializer;
    }
}
