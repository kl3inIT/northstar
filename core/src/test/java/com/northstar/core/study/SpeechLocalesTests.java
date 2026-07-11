package com.northstar.core.study;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SpeechLocalesTests {

    @Test
    void detectsMandarinFromCjkIdeographs() {
        assertThat(SpeechLocales.forReference("学习")).isEqualTo("zh-CN");
    }

    @Test
    void defaultsNonCjkTextToEnglish() {
        assertThat(SpeechLocales.forReference("environment")).isEqualTo("en-US");
        assertThat(SpeechLocales.forReference(null)).isEqualTo("en-US");
    }
}
