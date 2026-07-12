package com.northstar.api.study;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.northstar.core.ai.AiClientRouter;
import com.northstar.core.ai.AiRoute;
import com.northstar.core.ai.AiTask;
import com.northstar.core.ai.GeneratedImage;
import com.northstar.core.ai.ImageGenerationGateway;
import com.northstar.core.attachment.AttachmentService;
import com.northstar.core.attachment.AttachmentView;
import com.northstar.core.study.VocabCardSummary;
import com.northstar.core.study.VocabCoach;
import com.northstar.core.study.VocabEnrichmentField;
import com.northstar.core.study.VocabEnrichmentPreview;
import com.northstar.core.study.VocabLanguage;
import com.northstar.core.study.VocabService;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.AsyncTaskExecutor;
import tools.jackson.databind.ObjectMapper;

class VocabEnrichmentJobServiceTests {

    private final VocabService vocab = mock(VocabService.class);
    private final VocabCoach coach = mock(VocabCoach.class);
    private final AiClientRouter ai = mock(AiClientRouter.class);
    private final ImageGenerationGateway images = mock(ImageGenerationGateway.class);
    private final AttachmentService attachments = mock(AttachmentService.class);
    private final AsyncTaskExecutor executor = mock(AsyncTaskExecutor.class);
    private VocabEnrichmentJobService jobs;
    private VocabCardSummary card;

    @BeforeEach
    void setUp() {
        card = card();
        when(vocab.find(card.id())).thenReturn(card);
        when(ai.route(AiTask.IMAGE_GENERATION)).thenReturn(new AiRoute("nine-router", "auto:image"));
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(executor).execute(any(Runnable.class));
        jobs = new VocabEnrichmentJobService(vocab, coach, ai, images, attachments,
                executor, new ObjectMapper());
    }

    @Test
    void imagePreviewStaysTransientUntilApply() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
        when(images.generate(any(), anyString())).thenReturn(new GeneratedImage(png, "image/png"));

        VocabEnrichmentJobView ready = jobs.start(card.id(), Set.of(VocabEnrichmentField.IMAGE));

        assertThat(ready.status()).isEqualTo(VocabEnrichmentJobStatus.READY);
        assertThat(ready.imageBase64()).isNotBlank();
        assertThat(ready.imageAlt()).doesNotContain(card.front());
        verify(attachments, never()).store(anyString(), anyString(), any());

        UUID attachmentId = UUID.randomUUID();
        when(attachments.store(anyString(), anyString(), any())).thenReturn(new AttachmentView(
                attachmentId, "image.png", "image/png", png.length, "hash", Instant.now()));
        when(vocab.update(any(), anyString(), anyString(), anyString(), any(), any(), any(),
                any(Boolean.class), any(Boolean.class))).thenReturn(card);

        jobs.apply(ready.id());

        verify(attachments).store(anyString(), anyString(), any());
        verify(vocab).update(any(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.contains(attachmentId.toString()), any(), any(), any(),
                any(Boolean.class), any(Boolean.class));
    }

    @Test
    void discardDoesNotPersistTextPreview() {
        VocabEnrichmentPreview preview = new VocabEnrichmentPreview("Example", List.of(), List.of(),
                List.of(), null, null, null, "{\"example\":\"Example\"}");
        when(coach.enrich(card, Set.of(VocabEnrichmentField.EXAMPLE))).thenReturn(preview);

        VocabEnrichmentJobView ready = jobs.start(card.id(), Set.of(VocabEnrichmentField.EXAMPLE));
        jobs.discard(ready.id());

        verify(vocab, never()).update(any(), anyString(), anyString(), anyString(), any(), any(),
                any(), any(Boolean.class), any(Boolean.class));
        verify(attachments, never()).store(anyString(), anyString(), any());
    }

    @Test
    void reviewVersionChangeDoesNotInvalidatePreview() {
        VocabEnrichmentPreview preview = new VocabEnrichmentPreview("Example", List.of(), List.of(),
                List.of(), null, null, null, "{\"example\":\"Example\"}");
        when(coach.enrich(card, Set.of(VocabEnrichmentField.EXAMPLE))).thenReturn(preview);
        VocabCardSummary reviewed = copy(card, card.front(), card.version() + 1);
        when(vocab.find(card.id())).thenReturn(card, reviewed);
        when(vocab.update(any(), anyString(), anyString(), anyString(), any(), any(), any(),
                any(Boolean.class), any(Boolean.class))).thenReturn(reviewed);

        VocabEnrichmentJobView ready = jobs.start(card.id(), Set.of(VocabEnrichmentField.EXAMPLE));

        assertThat(jobs.apply(ready.id())).isEqualTo(reviewed);
    }

    @Test
    void contentChangeInvalidatesPreviewBeforePersistence() {
        VocabEnrichmentPreview preview = new VocabEnrichmentPreview("Example", List.of(), List.of(),
                List.of(), null, null, null, "{\"example\":\"Example\"}");
        when(coach.enrich(card, Set.of(VocabEnrichmentField.EXAMPLE))).thenReturn(preview);
        when(vocab.find(card.id())).thenReturn(card, copy(card, "reassigned", card.version() + 1));

        VocabEnrichmentJobView ready = jobs.start(card.id(), Set.of(VocabEnrichmentField.EXAMPLE));

        assertThatThrownBy(() -> jobs.apply(ready.id()))
                .hasMessageContaining("Card content changed");
        verify(vocab, never()).update(any(), anyString(), anyString(), anyString(), any(), any(),
                any(), any(Boolean.class), any(Boolean.class));
    }

    private static VocabCardSummary card() {
        Instant now = Instant.parse("2026-07-12T00:00:00Z");
        return new VocabCardSummary(UUID.randomUUID(), "reassign", "giao lại", "{}",
                VocabLanguage.ENGLISH, "IELTS", null, 0.4, 24, now, 1, false, now, 7,
                true, 0.3, 0);
    }

    private static VocabCardSummary copy(VocabCardSummary source, String front, long version) {
        return new VocabCardSummary(source.id(), front, source.back(), source.metadata(),
                source.language(), source.deck(), source.disciplineId(), source.recallProbability(),
                source.halflifeHours(), source.lastReviewedAt(), source.reviewCount(),
                source.suspended(), source.createdAt(), version, source.productionEnabled(),
                source.productionRecallProbability(), source.productionReviewCount());
    }
}
