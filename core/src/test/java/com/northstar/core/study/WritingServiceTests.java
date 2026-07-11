package com.northstar.core.study;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * Pins the error-corpus aggregation the grammar tutor drills from: grouping is
 * case-insensitive, ordering is recency-first, and a malformed stored JSON row
 * degrades to "no patterns from that row" instead of failing the whole call.
 */
class WritingServiceTests {

    private final WritingFeedbackRepository repository = mock(WritingFeedbackRepository.class);
    private final WritingService service = new WritingService(repository, new ObjectMapper());

    private static WritingFeedback feedback(Instant submittedAt, String topErrors) {
        return new WritingFeedback(UUID.randomUUID(), submittedAt, "IELTS Task 2", "ielts-writing",
                "essay text long enough", 250, 5.0, 5.5, "[]", topErrors, "summary", "gpt-5.5");
    }

    @Test
    void aggregatesAcrossGradingsCaseInsensitively() {
        Instant now = Instant.now();
        when(repository.findByOrderBySubmittedAtDesc()).thenReturn(List.of(
                feedback(now, """
                        [{"label":"Article errors","quote":"a advantages","fix":"advantages"}]"""),
                feedback(now.minus(3, ChronoUnit.DAYS), """
                        [{"label":"article errors","quote":"the technology","fix":"technology"},
                         {"label":"Subject-verb agreement","quote":"people is","fix":"people are"}]""")));

        List<GrammarWeakness> weaknesses = service.grammarWeaknesses();

        assertThat(weaknesses).hasSize(2);
        GrammarWeakness articles = weaknesses.get(0);
        // Newest-first: the freshest label casing wins and its example comes first.
        assertThat(articles.label()).isEqualTo("Article errors");
        assertThat(articles.occurrences()).isEqualTo(2);
        assertThat(articles.lastSeen())
                .isEqualTo(now.atZone(ZoneId.systemDefault()).toLocalDate());
        assertThat(articles.examples()).hasSize(2);
        assertThat(articles.examples().get(0).quote()).isEqualTo("a advantages");
        assertThat(weaknesses.get(1).label()).isEqualTo("Subject-verb agreement");
    }

    @Test
    void skipsMalformedJsonRows() {
        when(repository.findByOrderBySubmittedAtDesc()).thenReturn(List.of(
                feedback(Instant.now(), "not json at all"),
                feedback(Instant.now(), """
                        [{"label":"Linking phrases","quote":"In one hand","fix":"On the one hand"}]""")));

        List<GrammarWeakness> weaknesses = service.grammarWeaknesses();

        assertThat(weaknesses).hasSize(1);
        assertThat(weaknesses.get(0).label()).isEqualTo("Linking phrases");
    }

    @Test
    void emptyHistoryYieldsEmptyList() {
        when(repository.findByOrderBySubmittedAtDesc()).thenReturn(List.of());
        assertThat(service.grammarWeaknesses()).isEmpty();
    }
}
