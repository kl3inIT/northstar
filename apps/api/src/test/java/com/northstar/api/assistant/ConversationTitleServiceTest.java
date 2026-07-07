package com.northstar.api.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ConversationTitleServiceTest {

    @Test
    void trimsWhitespaceAndCollapsesRuns() {
        assertThat(ConversationTitleService.cleanTitle("  IELTS  writing   plan \n"))
                .isEqualTo("IELTS writing plan");
    }

    @Test
    void peelsSurroundingQuotes() {
        assertThat(ConversationTitleService.cleanTitle("\"Scholarship deadlines\"")).isEqualTo("Scholarship deadlines");
        assertThat(ConversationTitleService.cleanTitle("'HSK vocab review'")).isEqualTo("HSK vocab review");
    }

    @Test
    void stripsTrailingPunctuation() {
        assertThat(ConversationTitleService.cleanTitle("Plan the week!!!")).isEqualTo("Plan the week");
    }

    @Test
    void keepsInnerQuotesAndMidPunctuation() {
        assertThat(ConversationTitleService.cleanTitle("Notes on the \"deep work\" method"))
                .isEqualTo("Notes on the \"deep work\" method");
    }

    @Test
    void boundsLength() {
        String title = ConversationTitleService.cleanTitle("x".repeat(200));
        assertThat(title).isNotNull();
        assertThat(title.length()).isLessThanOrEqualTo(120);
    }

    @Test
    void returnsNullForEmptyOrNull() {
        assertThat(ConversationTitleService.cleanTitle(null)).isNull();
        assertThat(ConversationTitleService.cleanTitle("   ")).isNull();
        assertThat(ConversationTitleService.cleanTitle("\"\"")).isNull();
    }
}
