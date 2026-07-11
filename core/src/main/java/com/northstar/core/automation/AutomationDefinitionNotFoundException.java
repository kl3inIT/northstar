package com.northstar.core.automation;

import java.util.UUID;

public class AutomationDefinitionNotFoundException extends RuntimeException {
    public AutomationDefinitionNotFoundException(UUID id) {
        super("Automation not found: " + id);
    }
}
