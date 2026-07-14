package com.northstar.core.cache;

public record SemanticCacheLookup(
        String scope,
        String namespace,
        String modelRoute,
        String instructionFingerprint,
        String contextFingerprint,
        String prompt,
        double minimumSimilarity) {

    public SemanticCacheLookup {
        scope = required(scope, "scope");
        namespace = required(namespace, "namespace");
        modelRoute = required(modelRoute, "modelRoute");
        instructionFingerprint = required(instructionFingerprint, "instructionFingerprint");
        contextFingerprint = required(contextFingerprint, "contextFingerprint");
        prompt = required(prompt, "prompt");
        if (!Double.isFinite(minimumSimilarity)
                || minimumSimilarity <= 0 || minimumSimilarity > 1) {
            throw new IllegalArgumentException("minimumSimilarity must be in (0, 1]");
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.strip();
    }
}
