package com.northstar.core.capture;

import java.util.List;

/**
 * LLM-parsed vocabulary cards. One captured message can carry several ("từ
 * mới: 磨蹭 = lề mề, 顺便 = tiện thể") — each word becomes one card. The
 * capture model also ENRICHES what the user typed: {@code reading} carries
 * pinyin/phonetics and {@code example} one short example sentence, both
 * generated when the user did not supply them — the AI-native replacement for
 * hand-authoring Anki card fields. "" means "none".
 */
public record VocabDraft(List<VocabItem> items) {

    public record VocabItem(
            String front,
            String back,
            String reading,
            String example,
            String disciplineName) {
    }
}
