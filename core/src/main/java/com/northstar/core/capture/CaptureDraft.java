package com.northstar.core.capture;

import jakarta.validation.constraints.NotNull;

/**
 * Classified capture result: exactly one of {@code note}/{@code task} is set,
 * matching {@code kind}. The user can still override the classification in the
 * review UI before anything is persisted.
 */
public record CaptureDraft(@NotNull Kind kind, NoteDraft note, TaskDraft task) {

    public enum Kind { NOTE, TASK }
}
