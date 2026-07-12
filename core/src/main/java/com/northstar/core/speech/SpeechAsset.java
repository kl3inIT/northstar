package com.northstar.core.speech;

import com.northstar.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "speech_asset")
class SpeechAsset extends BaseEntity {

    @Column(name = "cache_key", nullable = false, length = 64)
    private String cacheKey;

    @Column(name = "text_hash", nullable = false, length = 64)
    private String textHash;

    @Column(name = "text_length", nullable = false)
    private int textLength;

    @Column(name = "gateway_id", nullable = false, length = 64)
    private String gatewayId;

    @Column(name = "target_id", nullable = false, length = 255)
    private String targetId;

    @Column(nullable = false, length = 35)
    private String locale;

    @Column(nullable = false, length = 16)
    private String format;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "attachment_id", nullable = false)
    private UUID attachmentId;

    protected SpeechAsset() {
        // for JPA
    }

    SpeechAsset(UUID id, String cacheKey, String textHash, int textLength, String gatewayId,
            String targetId, String locale, String format, String mimeType, UUID attachmentId) {
        super(id);
        this.cacheKey = cacheKey;
        this.textHash = textHash;
        this.textLength = textLength;
        this.gatewayId = gatewayId;
        this.targetId = targetId;
        this.locale = locale;
        this.format = format;
        this.mimeType = mimeType;
        this.attachmentId = attachmentId;
    }

    String cacheKey() { return cacheKey; }
    String textHash() { return textHash; }
    int textLength() { return textLength; }
    String gatewayId() { return gatewayId; }
    String targetId() { return targetId; }
    String locale() { return locale; }
    String format() { return format; }
    String mimeType() { return mimeType; }
    UUID attachmentId() { return attachmentId; }
}
