package com.northstar.core.study;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Validated WAV PCM payload kept entirely in memory. */
public record WavAudio(byte[] pcm, double durationSeconds) {

    private static final int SAMPLE_RATE = 16_000;
    private static final int BITS_PER_SAMPLE = 16;
    private static final int CHANNELS = 1;

    public WavAudio {
        pcm = pcm.clone();
    }

    @Override
    public byte[] pcm() {
        return pcm.clone();
    }

    public static WavAudio parse(byte[] wav, double maximumSeconds) {
        if (wav == null || wav.length < 44 || maximumSeconds <= 0) {
            throw bad("A valid WAV recording is required");
        }
        if (!ascii(wav, 0, 4).equals("RIFF") || !ascii(wav, 8, 4).equals("WAVE")) {
            throw bad("Audio must be a WAV file");
        }

        Integer audioFormat = null;
        Integer channels = null;
        Integer sampleRate = null;
        Integer bitsPerSample = null;
        byte[] pcm = null;
        int offset = 12;
        while (offset + 8 <= wav.length) {
            String chunkId = ascii(wav, offset, 4);
            long chunkSizeLong = Integer.toUnsignedLong(littleInt(wav, offset + 4));
            if (chunkSizeLong > Integer.MAX_VALUE) throw bad("WAV chunk is too large");
            int chunkSize = (int) chunkSizeLong;
            int dataOffset = offset + 8;
            if (dataOffset + chunkSize > wav.length) throw bad("WAV chunk is truncated");
            if (chunkId.equals("fmt ")) {
                if (chunkSize < 16) throw bad("WAV format chunk is invalid");
                audioFormat = littleShort(wav, dataOffset);
                channels = littleShort(wav, dataOffset + 2);
                sampleRate = littleInt(wav, dataOffset + 4);
                bitsPerSample = littleShort(wav, dataOffset + 14);
            } else if (chunkId.equals("data")) {
                pcm = Arrays.copyOfRange(wav, dataOffset, dataOffset + chunkSize);
            }
            offset = dataOffset + chunkSize + (chunkSize & 1);
        }

        if (audioFormat == null || pcm == null || pcm.length == 0) {
            throw bad("WAV format and data chunks are required");
        }
        if (audioFormat != 1 || channels != CHANNELS || sampleRate != SAMPLE_RATE
                || bitsPerSample != BITS_PER_SAMPLE) {
            throw bad("Audio must be 16 kHz, 16-bit, mono PCM WAV");
        }
        double duration = pcm.length / (double) (SAMPLE_RATE * CHANNELS * (BITS_PER_SAMPLE / 8));
        if (duration > maximumSeconds) {
            throw bad("Audio exceeds the %.0f-second limit".formatted(maximumSeconds));
        }
        return new WavAudio(pcm, duration);
    }

    private static int littleInt(byte[] bytes, int offset) {
        return ByteBuffer.wrap(bytes, offset, Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    private static int littleShort(byte[] bytes, int offset) {
        return Short.toUnsignedInt(ByteBuffer.wrap(bytes, offset, Short.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN).getShort());
    }

    private static String ascii(byte[] bytes, int offset, int length) {
        if (offset < 0 || offset + length > bytes.length) return "";
        return new String(bytes, offset, length, StandardCharsets.US_ASCII);
    }

    private static SpeechAssessmentException bad(String message) {
        return new SpeechAssessmentException(SpeechAssessmentException.Failure.BAD_AUDIO, message);
    }
}
