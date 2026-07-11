package com.northstar.core.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Arrays;

@Entity
@Table(name = "ai_gateway_setting")
public class AiGatewaySetting {

    @Id
    @Column(nullable = false, length = 64)
    private String id;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "base_url", nullable = false, length = 500)
    private String baseUrl;

    @Column(name = "api_key_ciphertext", nullable = false)
    private byte[] apiKeyCiphertext;

    @Column(nullable = false, columnDefinition = "text")
    private String models;

    @Column(name = "discover_models", nullable = false)
    private boolean discoverModels;

    @Column(name = "timeout_seconds", nullable = false)
    private int timeoutSeconds;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected AiGatewaySetting() {
    }

    public AiGatewaySetting(String id, String displayName, String baseUrl,
            byte[] apiKeyCiphertext, String models, boolean discoverModels,
            int timeoutSeconds) {
        this.id = id;
        apply(displayName, baseUrl, apiKeyCiphertext, models, discoverModels, timeoutSeconds);
    }

    public void apply(String displayName, String baseUrl, byte[] apiKeyCiphertext,
            String models, boolean discoverModels, int timeoutSeconds) {
        this.displayName = displayName;
        this.baseUrl = baseUrl;
        this.apiKeyCiphertext = Arrays.copyOf(apiKeyCiphertext, apiKeyCiphertext.length);
        this.models = models;
        this.discoverModels = discoverModels;
        this.timeoutSeconds = timeoutSeconds;
    }

    @PrePersist
    void created() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void updated() {
        updatedAt = Instant.now();
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public String baseUrl() { return baseUrl; }
    public byte[] apiKeyCiphertext() { return Arrays.copyOf(apiKeyCiphertext, apiKeyCiphertext.length); }
    public String models() { return models; }
    public boolean discoverModels() { return discoverModels; }
    public int timeoutSeconds() { return timeoutSeconds; }
}
