package com.northstar.core.speech;

import com.northstar.core.ai.AiRoute;
import java.util.List;

/** Provider delivery boundary. Core never depends on an audio vendor SDK. */
public interface TextToSpeechGateway {

    SpeechAudio synthesize(AiRoute route, String text, String locale);

    List<SpeechTarget> targets(String gatewayId);

    void validate(AiRoute route);
}
