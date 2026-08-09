package com.northstar.worker;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables the jobs module when it is composed into the Northstar server. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class WorkerSchedulingConfiguration {
}
