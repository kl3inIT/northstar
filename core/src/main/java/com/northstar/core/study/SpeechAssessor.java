package com.northstar.core.study;

/** Provider-neutral speech assessment. Provider adapters belong outside core. */
public interface SpeechAssessor {

    String providerId();

    String providerRevision();

    PronunciationResult assessReading(byte[] wavAudio, String referenceText, String locale);

    SpokenAnswerResult assessSpokenAnswer(byte[] wavAudio, String topic);
}
