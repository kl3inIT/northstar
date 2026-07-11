package com.northstar.core.study;

import java.util.List;

public record WordScore(String word, double accuracy, String errorType, List<PhonemeScore> phonemes) {

    public WordScore {
        word = word == null ? "" : word;
        errorType = errorType == null || errorType.isBlank() ? "None" : errorType;
        phonemes = phonemes == null ? List.of() : List.copyOf(phonemes);
    }
}
