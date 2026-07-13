package com.northstar.core.study;

public record VocabAudioRecording(String mimeType, byte[] data) {

    public VocabAudioRecording {
        data = data.clone();
    }

    @Override
    public byte[] data() {
        return data.clone();
    }
}
