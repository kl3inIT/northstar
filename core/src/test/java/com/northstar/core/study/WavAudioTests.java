package com.northstar.core.study;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class WavAudioTests {

    @Test
    void extractsHeaderFreePcmAndDuration() {
        byte[] wav = wav(32_000, 16_000, 1, 16);

        WavAudio parsed = WavAudio.parse(wav, 2);

        assertThat(parsed.pcm()).hasSize(32_000);
        assertThat(parsed.durationSeconds()).isEqualTo(1.0);
    }

    @Test
    void rejectsWrongFormatAndOverlongAudio() {
        assertThatThrownBy(() -> WavAudio.parse(wav(32_000, 24_000, 1, 16), 2))
                .isInstanceOf(SpeechAssessmentException.class)
                .hasMessageContaining("16 kHz");
        assertThatThrownBy(() -> WavAudio.parse(wav(64_000, 16_000, 1, 16), 1))
                .isInstanceOf(SpeechAssessmentException.class)
                .hasMessageContaining("1-second");
    }

    static byte[] wav(int pcmBytes, int sampleRate, int channels, int bitsPerSample) {
        ByteBuffer result = ByteBuffer.allocate(44 + pcmBytes).order(ByteOrder.LITTLE_ENDIAN);
        result.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        result.putInt(36 + pcmBytes);
        result.put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII));
        result.putInt(16);
        result.putShort((short) 1);
        result.putShort((short) channels);
        result.putInt(sampleRate);
        int bytesPerSample = bitsPerSample / 8;
        result.putInt(sampleRate * channels * bytesPerSample);
        result.putShort((short) (channels * bytesPerSample));
        result.putShort((short) bitsPerSample);
        result.put("data".getBytes(StandardCharsets.US_ASCII));
        result.putInt(pcmBytes);
        result.put(new byte[pcmBytes]);
        return result.array();
    }
}
