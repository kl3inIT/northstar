package com.northstar.integration.speech.azure;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class AzureSpeechResponseParserTests {

    private final AzureSpeechResponseParser parser = new AzureSpeechResponseParser(new ObjectMapper());

    @Test
    void parsesCapturedLiveReadingResponseIncludingPhonemes() throws IOException {
        String captured;
        try (var input = getClass().getResourceAsStream("/azure-reading-response.json")) {
            captured = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        var result = parser.reading(List.of(captured));

        assertThat(result.accuracy()).isEqualTo(92.0);
        assertThat(result.fluency()).isEqualTo(100.0);
        assertThat(result.prosody()).isEqualTo(90.3);
        assertThat(result.recognizedText()).isEqualTo("Good morning.");
        assertThat(result.words()).hasSize(2);
        assertThat(result.words().getFirst().phonemes()).extracting("phoneme")
                .containsExactly("g", "uh", "d");
    }

    @Test
    void aggregatesContinuousSdkSegmentsWithoutInventingACompositeScore() {
        String first = segment("I enjoy", 80, 60, 70, 1_000_000, 2_000_000);
        String second = segment("reading books.", 60, 80, 90, 4_000_000, 2_000_000);

        var result = parser.spoken(List.of(first, second));

        assertThat(result.transcript()).isEqualTo("I enjoy reading books.");
        assertThat(result.pronunciation()).isEqualTo(70.0);
        assertThat(result.prosody()).isEqualTo(80.0);
        assertThat(result.fluency()).isBetween(60.0, 100.0);
        assertThat(result.words()).extracting("word").containsExactly("I enjoy", "reading books");
    }

    private static String segment(String text, double accuracy, double fluency, double prosody,
            long offset, long duration) {
        return """
                {"RecognitionStatus":"Success","NBest":[{"Display":"%s","PronunciationAssessment":{
                "AccuracyScore":%s,"FluencyScore":%s,"ProsodyScore":%s},"Words":[{
                "Word":"%s","Offset":%s,"Duration":%s,"PronunciationAssessment":{
                "AccuracyScore":%s,"ErrorType":"None"},"Phonemes":[]}]}]}
                """.formatted(text, accuracy, fluency, prosody, text.replace(".", ""),
                        offset, duration, accuracy);
    }
}
