package com.northstar.core.speech;

import com.northstar.core.ai.AiRoute;
import com.northstar.core.attachment.AttachmentContent;
import com.northstar.core.attachment.AttachmentService;
import com.northstar.core.attachment.AttachmentView;
import com.northstar.core.shared.Hashing;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

public class SpeechAssetService {

    public static final int MAX_TEXT_LENGTH = 4096;
    private static final String FORMAT = "mp3";
    private static final String MIME_TYPE = "audio/mpeg";

    private final SpeechAssetRepository assets;
    private final AttachmentService attachments;
    private final TextToSpeechGateway gateway;

    public SpeechAssetService(SpeechAssetRepository assets, AttachmentService attachments,
            TextToSpeechGateway gateway) {
        this.assets = assets;
        this.attachments = attachments;
        this.gateway = gateway;
    }

    public SpeechAssetResult synthesize(AiRoute route, String text, String locale) {
        String normalizedText = normalizeText(text);
        String normalizedLocale = normalizeLocale(locale);
        String textHash = Hashing.sha256Hex(normalizedText);
        String cacheKey = Hashing.sha256Hex("speech-v1\n" + textHash + "\n"
                + route.gatewayId() + "\n" + route.modelId() + "\n"
                + normalizedLocale + "\n" + FORMAT);
        Optional<SpeechAsset> existing = assets.findByCacheKey(cacheKey);
        if (existing.isPresent()) {
            return new SpeechAssetResult(view(existing.orElseThrow()), true);
        }

        gateway.validate(route);
        SpeechAudio audio = gateway.synthesize(route, normalizedText, normalizedLocale);
        if (!FORMAT.equalsIgnoreCase(audio.format())) {
            throw new SpeechSynthesisException("TTS provider returned unsupported format: " + audio.format());
        }
        if (audio.data().length == 0) {
            throw new SpeechSynthesisException("TTS provider returned empty audio");
        }
        if (audio.data().length > AttachmentService.MAX_BYTES) {
            throw new SpeechSynthesisException("TTS provider returned audio larger than 25MB");
        }

        AttachmentView attachment = attachments.store("speech-" + cacheKey.substring(0, 12) + ".mp3",
                MIME_TYPE, audio.data());
        SpeechAsset asset = new SpeechAsset(UUID.randomUUID(), cacheKey, textHash,
                normalizedText.length(), route.gatewayId(), route.modelId(), normalizedLocale,
                FORMAT, MIME_TYPE, attachment.id());
        try {
            return new SpeechAssetResult(view(assets.saveAndFlush(asset)), false);
        } catch (DataIntegrityViolationException duplicate) {
            return assets.findByCacheKey(cacheKey)
                    .map(value -> new SpeechAssetResult(view(value), true))
                    .orElseThrow(() -> duplicate);
        }
    }

    @Transactional(readOnly = true)
    public Optional<SpeechAssetContent> load(UUID id) {
        return assets.findById(id).map(asset -> {
            AttachmentContent content = attachments.load(asset.attachmentId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Speech attachment is missing: " + asset.attachmentId()));
            return new SpeechAssetContent(view(asset), content.data());
        });
    }

    private static SpeechAssetView view(SpeechAsset asset) {
        return new SpeechAssetView(asset.getId(), asset.attachmentId(), asset.gatewayId(),
                asset.targetId(), asset.locale(), asset.format(), asset.mimeType(),
                asset.textLength(), asset.getCreatedAt());
    }

    private static String normalizeText(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text is required");
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n').strip();
        if (normalized.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("text must be at most " + MAX_TEXT_LENGTH + " characters");
        }
        return normalized;
    }

    private static String normalizeLocale(String locale) {
        if (locale == null || locale.isBlank()) {
            return "auto";
        }
        String normalized = locale.strip();
        if (normalized.length() > 35 || !normalized.matches("[A-Za-z0-9-]+")) {
            throw new IllegalArgumentException("locale is invalid");
        }
        return normalized.toLowerCase(Locale.ROOT);
    }
}
