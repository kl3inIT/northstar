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
                "Meticulous emphasizes every detail.", "Think: minute details.", null);

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
                null, "Minute details make meticulous.", null);

        VocabEnrichmentPreview preview = VocabCoach.preview("not-json", generated,
                Set.of(VocabEnrichmentField.MNEMONIC), json);

        assertThat(preview.metadata()).isEqualTo("{\"mnemonic\":\"Minute details make meticulous.\"}");
    }

    @Test
    void structuredOutputValidationRejectsEmptyRequestedFields() {
        var generated = new VocabCoach.GeneratedEnrichment("", List.of(), List.of(),
                List.of(), "", "", null);

        assertThat(VocabCoach.enrichmentProblem(generated,
                Set.of(VocabEnrichmentField.EXAMPLE, VocabEnrichmentField.SYNONYMS)))
                .contains("EXAMPLE is empty")
                .contains("SYNONYMS is empty");
        assertThat(VocabCoach.answerProblem(new VocabAnswerAssessment(
                VocabAnswerAssessment.Verdict.CLOSE, "Missing the sense of careful detail.")))
                .isNull();
    }

    @Test
    void wordFormationIsStructuredAndMayBeOmittedWhenNotUseful() {
        VocabWordFormation formation = new VocabWordFormation(List.of(
                new VocabWordPart("re-", "prefix", "again"),
                new VocabWordPart("assign", "base", "give a task")),
                "reassign means assign again", List.of("assign", "assignment", "reassignment"));
        var generated = new VocabCoach.GeneratedEnrichment(null, null, null, null,
                null, null, formation);

        VocabEnrichmentPreview preview = VocabCoach.preview("{}", generated,
                Set.of(VocabEnrichmentField.WORD_FORMATION), json);

        assertThat(preview.wordFormation()).isEqualTo(formation);
        assertThat(preview.metadata()).contains("\"wordFormation\"").contains("\"prefix\"");

        var absent = new VocabCoach.GeneratedEnrichment(null, null, null, null,
                null, null, null);
        assertThat(VocabCoach.enrichmentProblem(absent,
                Set.of(VocabEnrichmentField.WORD_FORMATION))).isNull();

        var malformed = new VocabCoach.GeneratedEnrichment(null, null, null, null,
                null, null, new VocabWordFormation(List.of(
                        new VocabWordPart("serendipity", "history", "uncertain origin")),
                        "Not a useful modern decomposition", List.of()));
        assertThat(VocabCoach.enrichmentProblem(malformed,
                Set.of(VocabEnrichmentField.WORD_FORMATION))).isNull();
        assertThat(VocabCoach.preview("{}", malformed,
                Set.of(VocabEnrichmentField.WORD_FORMATION), json).wordFormation()).isNull();
    }
}
