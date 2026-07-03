package com.northstar.core.shared;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Base for every aggregate root: client-assigned UUID identity, audit
 * timestamps filled by JPA auditing (see {@link JpaAuditingConfig}), and an
 * optimistic-locking {@code version} so a stale write fails with a 409 instead
 * of silently overwriting. Every table therefore carries
 * {@code id, created_at, updated_at, version} columns in its Flyway DDL.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected BaseEntity() {
        // for JPA
    }

    protected BaseEntity(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    @Override
    public final boolean equals(Object o) {
        return this == o
                || (o != null && getClass() == o.getClass() && id != null && id.equals(((BaseEntity) o).id));
    }

    @Override
    public final int hashCode() {
        return id == null ? System.identityHashCode(this) : id.hashCode();
    }
}
