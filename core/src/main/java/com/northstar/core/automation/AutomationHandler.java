package com.northstar.core.automation;

public interface AutomationHandler<C> {
    String type();
    String displayName();
    String description();
    int configVersion();
    Class<C> configType();
    C defaultConfig();
    void validate(C config);
    AutomationHandlerResult execute(AutomationExecutionContext context, C config);
}
