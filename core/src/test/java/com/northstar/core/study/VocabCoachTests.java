package com.northstar.core.study;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.ai.converter.BeanOutputConverter;
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
                new VocabWordPart("re-", VocabWordPartKind.prefix, "again"),
                new VocabWordPart("assign", VocabWordPartKind.base, "give a task")),
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
                        new VocabWordPart("serendipity", VocabWordPartKind.root, "uncertain origin")),
                        "Not a useful modern decomposition", List.of()));
        assertThat(VocabCoach.enrichmentProblem(malformed,
                Set.of(VocabEnrichmentField.WORD_FORMATION))).isNull();
        assertThat(VocabCoach.preview("{}", malformed,
                Set.of(VocabEnrichmentField.WORD_FORMATION), json).wordFormation()).isNull();
    }

    @Test
    void providerSchemaRestrictsWordPartKinds() {
        String schema = new BeanOutputConverter<>(VocabCoach.GeneratedEnrichment.class)
                .getJsonSchema();

        assertThat(schema)
                .contains("\"example\"")
                .contains("\"wordFormation\"")
                .contains("\"family\"")
                .doesNotContain("\"EXAMPLE\"")
                .doesNotContain("\"WORD_FORMATION\"")
                .doesNotContain("\"family_words\"");
        assertThat(schema).containsPattern(
                "\\\"enum\\\"\\s*:\\s*\\[\\s*\\\"prefix\\\"\\s*,\\s*\\\"root\\\""
                        + "\\s*,\\s*\\\"base\\\"\\s*,\\s*\\\"suffix\\\"\\s*]");
    }

    @Test
    void compatibleGatewayAliasesMapTheCapturedProductionResponse() {
        String response = """
                {
                  "EXAMPLE": "The company moved its headquarters to Singapore. — Công ty đã chuyển trụ sở chính tới Singapore.",
                  "COLLOCATIONS": ["corporate headquarters", "company headquarters"],
                  "SYNONYMS": ["head office", "main office"],
                  "ANTONYMS": ["branch office", "satellite office"],
                  "CONTRAST": "Headquarters is the main control center; a branch is a secondary location.",
                  "MNEMONIC": "The head of the company works at its headquarters.",
                  "WORD_FORMATION": {
                    "parts": [
                      {"form": "head", "kind": "base", "meaning": "main or leading"},
                      {"form": "quarters", "kind": "base", "meaning": "lodging or premises"}
                    ],
                    "explanation": "The main premises from which an organization is directed.",
                    "family_words": ["headquarter", "headquartered"]
                  }
                }
                """;

        VocabCoach.GeneratedEnrichment generated = json.readValue(
                response, VocabCoach.GeneratedEnrichment.class);

        assertThat(generated.example()).contains("headquarters to Singapore");
        assertThat(generated.collocations()).containsExactly(
                "corporate headquarters", "company headquarters");
        assertThat(generated.synonyms()).containsExactly("head office", "main office");
        assertThat(generated.antonyms()).containsExactly("branch office", "satellite office");
        assertThat(generated.contrast()).startsWith("Headquarters is the main control center");
        assertThat(generated.mnemonic()).contains("head of the company");
        assertThat(generated.wordFormation().family())
                .containsExactly("headquarter", "headquartered");
        assertThat(VocabCoach.enrichmentProblem(generated, Set.of(
                VocabEnrichmentField.EXAMPLE,
                VocabEnrichmentField.COLLOCATIONS,
                VocabEnrichmentField.SYNONYMS,
                VocabEnrichmentField.ANTONYMS,
                VocabEnrichmentField.CONTRAST,
                VocabEnrichmentField.MNEMONIC,
                VocabEnrichmentField.WORD_FORMATION))).isNull();
    }
}
