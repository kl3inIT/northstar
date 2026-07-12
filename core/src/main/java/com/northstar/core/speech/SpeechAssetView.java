package com.northstar.core.speech;

import java.time.Instant;
import java.util.UUID;

public record SpeechAssetView(
        UUID id,
        UUID attachmentId,
        String gatewayId,
        String targetId,
        String locale,
        String format,
        String mimeType,
        int textLength,
        Instant createdAt) {
}
