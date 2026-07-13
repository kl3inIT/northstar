package com.northstar.core.study;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shared selection rules for the target-language text used by audio practice. */
public final class VocabPracticeText {

    private static final String TRANSLATION_SEPARATOR = " — ";
    private static final Pattern LEXICAL_WORD = Pattern.compile("\\p{L}[-\\p{L}\\p{M}'’]*");
    private static final int MIN_SHADOWING_WORDS = 4;
    private static final int MIN_SHADOWING_HAN_CHARACTERS = 6;

    private VocabPracticeText() {
    }

    /** Removes the optional translated half from the persisted `example — translation` shape. */
    public static Optional<String> targetExample(String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        String clean = value.strip();
        int separator = clean.indexOf(TRANSLATION_SEPARATOR);
        String target = separator < 0 ? clean : clean.substring(0, separator).strip();
        return target.isBlank() ? Optional.empty() : Optional.of(target);
    }

    /** Shadowing needs connected speech, not an isolated word or two-word label. */
    public static boolean supportsShadowing(String value) {
        Optional<String> target = targetExample(value);
        if (target.isEmpty()) return false;
        String text = target.orElseThrow();
        long hanCharacters = text.codePoints()
                .filter(codePoint -> Character.UnicodeScript.of(codePoint)
                        == Character.UnicodeScript.HAN)
                .count();
        if (hanCharacters >= MIN_SHADOWING_HAN_CHARACTERS) return true;

        int words = 0;
        Matcher matcher = LEXICAL_WORD.matcher(text);
        while (matcher.find()) words++;
        return words >= MIN_SHADOWING_WORDS;
    }
}
