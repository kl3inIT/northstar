package com.northstar.core.study;

public final class SpeechLocales {

    private SpeechLocales() {
    }

    public static String forReference(String referenceText) {
        if (referenceText == null) return "en-US";
        return referenceText.codePoints().anyMatch(SpeechLocales::isCjkUnifiedIdeograph)
                ? "zh-CN" : "en-US";
    }

    private static boolean isCjkUnifiedIdeograph(int codePoint) {
        return Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN;
    }
}
