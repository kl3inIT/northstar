package com.northstar.core.alignment;

/**
 * Structured output of the review-writer call. Splitting the priority out of
 * the prose means its "**Tomorrow's priority:** …" line is composed in code —
 * a schema guarantee instead of a prompt convention the model could drift from.
 */
public record ReviewCommentary(String commentary, String priority) {
}
