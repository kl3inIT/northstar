package com.northstar.core.study;

import java.util.regex.Pattern;

/** The learner's independent vocabulary library and review queue. */
public enum VocabLanguage {
    ENGLISH,
    CHINESE;

    private static final Pattern HAN = Pattern.compile("[\\p{IsHan}]");

    /** Deterministic compatibility rule for callers and cards created before language existed. */
    public static VocabLanguage detect(String front) {
        return front != null && HAN.matcher(front).find() ? CHINESE : ENGLISH;
    }
}
