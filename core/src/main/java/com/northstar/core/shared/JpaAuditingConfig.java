package com.northstar.core.shared;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Turns on JPA auditing for every app that scans {@code com.northstar.core}
 * (api, mcp, worker) so {@code @CreatedDate}/{@code @LastModifiedDate} on
 * {@link BaseEntity} are filled consistently no matter which process writes.
 */
@Configuration
@EnableJpaAuditing
class JpaAuditingConfig {
}
