package com.northstar.core.study;

import java.util.List;

public record PronunciationResult(
        double accuracy,
        double fluency,
        Double prosody,
        String recognizedText,
        List<WordScore> words) {

    public PronunciationResult {
        recognizedText = recognizedText == null ? "" : recognizedText;
        words = words == null ? List.of() : List.copyOf(words);
    }
}
