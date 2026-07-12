package com.northstar.api.speech;

import com.northstar.core.attachment.AttachmentService;
import com.northstar.core.speech.SpeechAssetRepository;
import com.northstar.core.speech.SpeechAssetService;
import com.northstar.core.speech.TextToSpeechGateway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class SpeechConfiguration {

    @Bean
    SpeechAssetService speechAssetService(SpeechAssetRepository assets,
            AttachmentService attachments, TextToSpeechGateway gateway) {
        return new SpeechAssetService(assets, attachments, gateway);
    }
}
