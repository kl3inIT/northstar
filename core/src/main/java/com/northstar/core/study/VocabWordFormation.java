package com.northstar.core.study;

import java.util.List;

/** Optional word-building explanation; null means decomposition would not help. */
public record VocabWordFormation(
        List<VocabWordPart> parts,
        String explanation,
        List<String> family) {
}

