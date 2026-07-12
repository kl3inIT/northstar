package com.northstar.core.speech;

public record SpeechAssetContent(SpeechAssetView asset, byte[] data) {

    public SpeechAssetContent {
        data = data.clone();
    }

    @Override
    public byte[] data() {
        return data.clone();
    }
}
