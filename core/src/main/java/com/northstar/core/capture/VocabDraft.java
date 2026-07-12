package com.northstar.core.capture;

import java.util.List;

/**
 * LLM-parsed vocabulary cards. One captured message can carry several ("từ
 * mới: 磨蹭 = lề mề, 顺便 = tiện thể") — each word becomes one card. The
 * capture model completes the card's minimum language shape: {@code reading}
 * carries pinyin/IPA and {@code partOfSpeech} the lexical category. Examples
 * are preserved only when supplied by the user; generated examples belong to
 * the explicit enrichment workflow. "" means "none".
 */
public record VocabDraft(List<VocabItem> items) {

    public record VocabItem(
            String front,
            String back,
            String reading,
            String partOfSpeech,
            String example,
            String disciplineName) {
    }
}
