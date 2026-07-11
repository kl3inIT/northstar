package com.northstar.core.study;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pins the structural half of the evaluator-optimizer loop: these checks run
 * in code (free and exact) before the LLM faithfulness evaluator, and a
 * regression here would let malformed grades into the feedback history.
 */
class WritingGraderTests {

    private static final String ESSAY = """
            Many people is believing that remote work bring only advantages.
            In one hand, workers save the commuting time.""";

    private static WritingGrade grade(List<WritingGrade.CriterionGrade> criteria,
            double min, double max, String summary, List<WritingGrade.EssayError> errors) {
        return new WritingGrade("reasoning", criteria, min, max, summary, errors);
    }

    private static List<WritingGrade.CriterionGrade> validCriteria() {
        return List.of(new WritingGrade.CriterionGrade("TR", 5.0,
                "Quotes \"remote work bring only advantages\" but develops nothing."));
    }

    @Test
    void validGradePasses() {
        WritingGrade grade = grade(validCriteria(), 5.0, 5.5, "Unofficial estimate ~5.0-5.5.",
                List.of(new WritingGrade.EssayError("Subject-verb agreement",
                        "Many people is believing", "Many people believe")));
        assertThat(WritingGrader.structuralProblems(grade, ESSAY)).isNull();
    }

    @Test
    void rejectsOffStepBand() {
        WritingGrade grade = grade(
                List.of(new WritingGrade.CriterionGrade("TR", 5.3, "Quoted evidence.")),
                5.0, 5.5, "Summary.", List.of());
        assertThat(WritingGrader.structuralProblems(grade, ESSAY)).contains("5.3");
    }

    @Test
    void rejectsTooWideRange() {
        WritingGrade grade = grade(validCriteria(), 4.5, 6.0, "Summary.", List.of());
        assertThat(WritingGrader.structuralProblems(grade, ESSAY)).contains("Overall range");
    }

    @Test
    void rejectsInvertedRange() {
        WritingGrade grade = grade(validCriteria(), 6.0, 5.5, "Summary.", List.of());
        assertThat(WritingGrader.structuralProblems(grade, ESSAY)).contains("Overall range");
    }

    @Test
    void rejectsNonVerbatimQuote() {
        WritingGrade grade = grade(validCriteria(), 5.0, 5.5, "Summary.",
                List.of(new WritingGrade.EssayError("Agreement",
                        "Many people are believing", "Many people believe")));
        assertThat(WritingGrader.structuralProblems(grade, ESSAY))
                .contains("not verbatim");
    }

    @Test
    void quoteCheckToleratesWhitespaceDifferences() {
        // The essay wraps across a line break; the model quotes it on one line.
        WritingGrade grade = grade(validCriteria(), 5.0, 5.5, "Summary.",
                List.of(new WritingGrade.EssayError("Development",
                        "only advantages. In one hand", "only advantages. On the one hand")));
        assertThat(WritingGrader.structuralProblems(grade, ESSAY)).isNull();
    }

    @Test
    void rejectsMissingCriteriaAndEmptySummary() {
        assertThat(WritingGrader.structuralProblems(
                grade(List.of(), 5.0, 5.5, "Summary.", List.of()), ESSAY))
                .contains("No criteria");
        assertThat(WritingGrader.structuralProblems(
                grade(validCriteria(), 5.0, 5.5, " ", List.of()), ESSAY))
                .contains("Summary is empty");
    }
}
