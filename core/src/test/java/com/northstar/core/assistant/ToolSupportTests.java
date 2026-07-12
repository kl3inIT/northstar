package com.northstar.core.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ToolSupportTests {

    @Test
    void vocabMetadataKeepsBaseFieldsAndEscapesUserText() {
        assertThat(ToolSupport.vocabMetadata(" /təˈmeɪtoʊ/ ", " noun ",
                "Say \"tomato\".\n— Nói cà chua."))
                .isEqualTo("{\"reading\":\"/təˈmeɪtoʊ/\",\"partOfSpeech\":\"noun\","
                        + "\"example\":\"Say \\\"tomato\\\".\\n— Nói cà chua.\"}");
    }

    @Test
    void vocabMetadataOmitsBlankOptionalFields() {
        assertThat(ToolSupport.vocabMetadata("", "adjective", null))
                .isEqualTo("{\"partOfSpeech\":\"adjective\"}");
        assertThat(ToolSupport.vocabMetadata(" ", null, "")).isNull();
    }
}
