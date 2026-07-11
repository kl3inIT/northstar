package com.northstar.core.automation;

public record AutomationRunClaim(
        boolean execute,
        AutomationDefinitionSummary definition,
        AutomationRunSummary run) {
}
