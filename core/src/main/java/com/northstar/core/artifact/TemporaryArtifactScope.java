package com.northstar.core.artifact;

import java.util.Locale;

/** Owner, workflow session, and category boundary for temporary content. */
public record TemporaryArtifactScope(String ownerScope, String sessionId, String category) {

    private static final int MAX_COMPONENT_LENGTH = 128;

    public TemporaryArtifactScope {
        ownerScope = required(ownerScope, "ownerScope");
        sessionId = required(sessionId, "sessionId");
        category = required(category, "category").toLowerCase(Locale.ROOT);
        if (!category.matches("[a-z0-9][a-z0-9._-]*")) {
            throw new IllegalArgumentException(
                    "category must contain only lowercase letters, numbers, dot, underscore, or dash");
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        String normalized = value.strip();
        if (normalized.length() > MAX_COMPONENT_LENGTH) {
            throw new IllegalArgumentException(field + " must be at most " + MAX_COMPONENT_LENGTH + " characters");
        }
        if (normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(field + " must not contain control characters");
        }
        return normalized;
    }
}
