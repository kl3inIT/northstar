package com.northstar.core.study;

public record PhonemeScore(String phoneme, double accuracy) {

    public PhonemeScore {
        phoneme = phoneme == null ? "" : phoneme;
    }
}
