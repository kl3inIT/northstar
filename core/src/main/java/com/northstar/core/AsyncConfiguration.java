package com.northstar.core;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * {@code @ApplicationModuleListener} is meta-annotated {@code @Async}, which is
 * inert until something turns async support on — Boot does not. Lives in the
 * shared root package so every app that bootstraps the core modules gets it;
 * with {@code spring.threads.virtual.enabled=true} the auto-configured executor
 * runs listeners on virtual threads.
 */
@Configuration(proxyBeanMethods = false)
@EnableAsync
class AsyncConfiguration {
}
