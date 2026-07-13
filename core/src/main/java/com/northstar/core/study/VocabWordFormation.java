package com.northstar.core.study;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.List;

/** Optional word-building explanation; null means decomposition would not help. */
public record VocabWordFormation(
        @JsonAlias("PARTS")
        List<VocabWordPart> parts,
        @JsonAlias("EXPLANATION")
        String explanation,
        @JsonAlias({"FAMILY", "family_words", "FAMILY_WORDS"})
        List<String> family) {
}
