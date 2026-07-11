package com.northstar.integration.speech.azure;

import com.northstar.core.study.PronunciationResult;
import com.northstar.core.study.SpeechAssessor;
import com.northstar.core.study.SpokenAnswerResult;
import com.northstar.core.study.WavAudio;
import tools.jackson.databind.ObjectMapper;

public final class AzureSpeechAssessor implements SpeechAssessor {

    private static final double READING_MAX_SECONDS = 30;
    private static final double SPEAKING_MAX_SECONDS = 75;

    private final AzureSpeechGateway gateway;
    private final AzureSpeechResponseParser parser;

    public AzureSpeechAssessor(String key, String region, ObjectMapper json) {
        this(new SpeechSdkGateway(key, region), new AzureSpeechResponseParser(json));
    }

    AzureSpeechAssessor(AzureSpeechGateway gateway, AzureSpeechResponseParser parser) {
        this.gateway = gateway;
        this.parser = parser;
    }

    @Override
    public String providerId() {
        return "azure";
    }

    @Override
    public String providerRevision() {
        return "speech-sdk-1.50.0";
    }

    @Override
    public PronunciationResult assessReading(byte[] wavAudio, String referenceText, String locale) {
        if (referenceText == null || referenceText.isBlank()) {
            throw new IllegalArgumentException("referenceText is required");
        }
        WavAudio audio = WavAudio.parse(wavAudio, READING_MAX_SECONDS);
        return parser.reading(gateway.assess(audio.pcm(), referenceText.strip(), locale, false));
    }

    @Override
    public SpokenAnswerResult assessSpokenAnswer(byte[] wavAudio, String topic) {
        WavAudio audio = WavAudio.parse(wavAudio, SPEAKING_MAX_SECONDS);
        return parser.spoken(gateway.assess(audio.pcm(), "", "en-US", true));
    }
}
