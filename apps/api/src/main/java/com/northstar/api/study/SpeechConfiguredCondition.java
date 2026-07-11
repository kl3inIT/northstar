package com.northstar.api.study;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

final class SpeechConfiguredCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String provider = context.getEnvironment().getProperty("northstar.speech.provider", "azure");
        return "azure".equalsIgnoreCase(provider)
                && StringUtils.hasText(context.getEnvironment().getProperty("northstar.speech.azure.key"));
    }
}
