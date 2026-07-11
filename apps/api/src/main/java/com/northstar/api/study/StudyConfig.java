package com.northstar.api.study;

import com.northstar.core.study.WritingGrader;
import com.northstar.core.study.WritingService;
import com.northstar.core.ai.AiClientRouter;
import com.northstar.core.study.SpeakingCoach;
import com.northstar.core.study.SpeakingService;
import com.northstar.core.study.SpeechAssessor;
import com.northstar.core.study.StudyService;
import java.time.ZoneId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the writing grader for this app (the CaptureConfig precedent): core's
 * WritingGrader is deliberately not a component so mcp/worker boot without an
 * LLM configured. The grader model id is pinned via property — it is stored on
 * every feedback row, so changing it is a deliberate act, not a side effect of
 * a default-model upgrade.
 */
@Configuration(proxyBeanMethods = false)
class StudyConfig {

    @Bean
    WritingGrader writingGrader(AiClientRouter ai, WritingService writing) {
        return new WritingGrader(ai, writing, ZoneId.systemDefault());
    }

    @Bean
    @ConditionalOnBean(SpeechAssessor.class)
    SpeakingCoach speakingCoach(AiClientRouter ai, SpeechAssessor speech, SpeakingService speaking,
            WritingService writing, StudyService study) {
        return new SpeakingCoach(ai, speech, speaking, writing, study, ZoneId.systemDefault());
    }
}
