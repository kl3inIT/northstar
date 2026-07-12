package com.northstar.core.study;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class VocabCoachTests {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void ratingSuccessValuesKeepTheAcceptedEbisuMapping() {
        assertThat(VocabReviewLog.Rating.AGAIN.success()).isZero();
        assertThat(VocabReviewLog.Rating.HARD.success()).isEqualTo(0.6);
        assertThat(VocabReviewLog.Rating.GOOD.success()).isEqualTo(0.9);
        assertThat(VocabReviewLog.Rating.EASY.success()).isEqualTo(1.0);
    }

    @Test
    void previewPreservesBaseAndUnknownMetadataAndAddsOnlySelectedFields() {
        var generated = new VocabCoach.GeneratedEnrichment(
                "She keeps meticulous records. — Cô ấy lưu hồ sơ rất tỉ mỉ.",
                List.of("meticulous planning"), List.of("careful"), List.of("careless"),
                "Meticulous emphasizes every detail.", "Think: minute details.");

        VocabEnrichmentPreview preview = VocabCoach.preview(
                "{\"reading\":\"/məˈtɪkjələs/\",\"partOfSpeech\":\"adjective\",\"future\":7}",
                generated, Set.of(VocabEnrichmentField.EXAMPLE, VocabEnrichmentField.COLLOCATIONS), json);

        assertThat(preview.example()).contains("meticulous records");
        assertThat(preview.collocations()).containsExactly("meticulous planning");
        assertThat(preview.synonyms()).isEmpty();
        assertThat(preview.contrast()).isNull();
        assertThat(preview.metadata())
                .contains("\"reading\":\"/məˈtɪkjələs/\"")
                .contains("\"partOfSpeech\":\"adjective\"")
                .contains("\"future\":7")
                .contains("\"example\"")
                .doesNotContain("\"synonyms\"")
                .doesNotContain("\"mnemonic\"");
    }

    @Test
    void malformedLegacyMetadataCanBeRepairedByAnExplicitPreview() {
        var generated = new VocabCoach.GeneratedEnrichment(null, null, null, null,
                null, "Minute details make meticulous.");

        VocabEnrichmentPreview preview = VocabCoach.preview("not-json", generated,
                Set.of(VocabEnrichmentField.MNEMONIC), json);

        assertThat(preview.metadata()).isEqualTo("{\"mnemonic\":\"Minute details make meticulous.\"}");
    }

    @Test
    void structuredOutputValidationRejectsEmptyRequestedFields() {
        var generated = new VocabCoach.GeneratedEnrichment("", List.of(), List.of(),
                List.of(), "", "");

        assertThat(VocabCoach.enrichmentProblem(generated,
                Set.of(VocabEnrichmentField.EXAMPLE, VocabEnrichmentField.SYNONYMS)))
                .contains("EXAMPLE is empty")
                .contains("SYNONYMS is empty");
        assertThat(VocabCoach.answerProblem(new VocabAnswerAssessment(
                VocabAnswerAssessment.Verdict.CLOSE, "Missing the sense of careful detail.")))
                .isNull();
    }
}
