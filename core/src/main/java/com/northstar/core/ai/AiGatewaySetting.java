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

    @Column(nullable = false, length = 40)
    private String type;

    @Column(name = "base_url", nullable = false, length = 500)
    private String baseUrl;

    @Column(name = "api_key_ciphertext", nullable = false)
    private byte[] apiKeyCiphertext;

    @Column(nullable = false, columnDefinition = "text")
    private String models;

    @Column(name = "tts_targets", nullable = false, columnDefinition = "text")
    private String ttsTargets;

    @Column(name = "web_search_targets", nullable = false, columnDefinition = "text")
    private String webSearchTargets;

    @Column(name = "web_fetch_targets", nullable = false, columnDefinition = "text")
    private String webFetchTargets;

    @Column(name = "stt_targets", nullable = false, columnDefinition = "text")
    private String sttTargets;

    @Column(name = "image_targets", nullable = false, columnDefinition = "text")
    private String imageTargets;

    @Column(name = "embedding_targets", nullable = false, columnDefinition = "text")
    private String embeddingTargets;

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

    public AiGatewaySetting(String id, String displayName, AiGatewayType type, String baseUrl,
            byte[] apiKeyCiphertext, String models, String ttsTargets,
            String webSearchTargets, String webFetchTargets, String sttTargets,
            String imageTargets, String embeddingTargets, boolean discoverModels,
            int timeoutSeconds) {
        this.id = id;
        apply(displayName, type, baseUrl, apiKeyCiphertext, models, ttsTargets,
                webSearchTargets, webFetchTargets,
                sttTargets, imageTargets, embeddingTargets,
                discoverModels, timeoutSeconds);
    }

    public void apply(String displayName, AiGatewayType type, String baseUrl, byte[] apiKeyCiphertext,
            String models, String ttsTargets, String webSearchTargets, String webFetchTargets,
            String sttTargets, String imageTargets, String embeddingTargets,
            boolean discoverModels, int timeoutSeconds) {
        this.displayName = displayName;
        this.type = type.name();
        this.baseUrl = baseUrl;
        this.apiKeyCiphertext = Arrays.copyOf(apiKeyCiphertext, apiKeyCiphertext.length);
        this.models = models;
        this.ttsTargets = ttsTargets;
        this.webSearchTargets = webSearchTargets;
        this.webFetchTargets = webFetchTargets;
        this.sttTargets = sttTargets;
        this.imageTargets = imageTargets;
        this.embeddingTargets = embeddingTargets;
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
    public AiGatewayType type() { return AiGatewayType.valueOf(type); }
    public String baseUrl() { return baseUrl; }
    public byte[] apiKeyCiphertext() { return Arrays.copyOf(apiKeyCiphertext, apiKeyCiphertext.length); }
    public String models() { return models; }
    public String ttsTargets() { return ttsTargets; }
    public String webSearchTargets() { return webSearchTargets; }
    public String webFetchTargets() { return webFetchTargets; }
    public String sttTargets() { return sttTargets; }
    public String imageTargets() { return imageTargets; }
    public String embeddingTargets() { return embeddingTargets; }
    public boolean discoverModels() { return discoverModels; }
    public int timeoutSeconds() { return timeoutSeconds; }
}
