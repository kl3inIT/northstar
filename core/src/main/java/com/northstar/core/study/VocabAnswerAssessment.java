package com.northstar.core.study;

import jakarta.validation.constraints.NotNull;

/** Advisory meaning-equivalence feedback; it never records a review. */
public record VocabAnswerAssessment(@NotNull Verdict verdict, @NotNull String feedback) {

    public enum Verdict { CORRECT, CLOSE, MISSED }
}
