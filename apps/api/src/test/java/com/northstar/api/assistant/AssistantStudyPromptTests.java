package com.northstar.api.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AssistantStudyPromptTests {

    @Test
    void definitionRequestIsVocabularySaveIntentWithSafeExceptions() {
        String prompt = AssistantController.SYSTEM_PROMPT.replaceAll("\\s+", " ");
        assertThat(prompt)
                .contains("is also intent to learn it")
                .contains("call save_vocab_cards in the same turn")
                .contains("unless the user explicitly says not to save it")
                .contains("only when exactly one lexical item is clear")
                .contains("Do not auto-save words merely present in prose");
    }
}
