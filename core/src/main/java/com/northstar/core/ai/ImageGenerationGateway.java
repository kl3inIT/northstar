package com.northstar.core.ai;

/** Provider-neutral text-to-image port implemented by delivery integrations. */
public interface ImageGenerationGateway {

    GeneratedImage generate(AiRoute route, String prompt);
}

