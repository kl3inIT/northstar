package com.northstar.core.speech;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.northstar.core.ai.AiRoute;
import com.northstar.core.attachment.AttachmentService;
import com.northstar.core.attachment.AttachmentView;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SpeechAssetServiceTests {

    private final SpeechAssetRepository repository = mock(SpeechAssetRepository.class);
    private final AttachmentService attachments = mock(AttachmentService.class);
    private final TextToSpeechGateway gateway = mock(TextToSpeechGateway.class);
    private final SpeechAssetService service = new SpeechAssetService(repository, attachments, gateway);
    private final AiRoute route = new AiRoute("nine-router", "edge-tts/vi-VN-HoaiMyNeural");

    @Test
    void cacheMissSynthesizesAndStoresOneImmutableAsset() {
        byte[] mp3 = {0x49, 0x44, 0x33, 1};
        UUID attachmentId = UUID.randomUUID();
        when(repository.findByCacheKey(any())).thenReturn(Optional.empty());
        when(gateway.synthesize(route, "Xin chao", "vi-vn"))
                .thenReturn(new SpeechAudio(mp3, "audio/mpeg", "mp3"));
        when(attachments.store(any(), eq("audio/mpeg"), any()))
                .thenReturn(new AttachmentView(attachmentId, "speech.mp3", "audio/mpeg",
                        mp3.length, "hash", Instant.now()));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SpeechAssetResult result = service.synthesize(route, "  Xin chao  ", "vi-VN");

        assertFalse(result.cacheHit());
        assertEquals(attachmentId, result.asset().attachmentId());
        assertEquals("vi-vn", result.asset().locale());
        verify(gateway).validate(route);
        verify(gateway).synthesize(route, "Xin chao", "vi-vn");
        verify(attachments).store(any(), eq("audio/mpeg"), any());
    }

    @Test
    void cacheHitNeverCallsTheProviderAgain() {
        SpeechAsset cached = new SpeechAsset(UUID.randomUUID(), "cache", "text", 9,
                route.gatewayId(), route.modelId(), "auto", "mp3", "audio/mpeg", UUID.randomUUID());
        when(repository.findByCacheKey(any())).thenReturn(Optional.of(cached));

        SpeechAssetResult result = service.synthesize(route, "Read this", null);

        assertTrue(result.cacheHit());
        verify(gateway, never()).validate(any());
        verify(gateway, never()).synthesize(any(), any(), any());
    }

    @Test
    void oversizedTextIsRejectedBeforeGatewayValidationOrSynthesis() {
        String text = "x".repeat(SpeechAssetService.MAX_TEXT_LENGTH + 1);

        assertThrows(IllegalArgumentException.class, () -> service.synthesize(route, text, null));

        verify(gateway, never()).validate(any());
        verify(gateway, never()).synthesize(any(), any(), any());
    }
}
