package com.northstar.integration.speech.azure;

import java.util.List;

interface AzureSpeechGateway {

    List<String> assess(byte[] pcm, String referenceText, String locale, boolean continuous);
}
