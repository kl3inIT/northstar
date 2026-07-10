package com.northstar.core.capture;

import jakarta.validation.constraints.NotNull;

/**
 * Classified capture result: exactly one of {@code note}/{@code task}/{@code event}/
 * {@code expense} is set, matching {@code kind}. The user can still override the
 * classification in the review UI before anything is persisted. {@code reasoning}
 * comes FIRST so the model must argue the intent before committing to a kind
 * (reason-then-label measurably beats label-only classification); the UI does not
 * display it.
 */
public record CaptureDraft(
        String reasoning, @NotNull Kind kind, NoteDraft note, TaskDraft task, EventDraft event,
        ExpenseDraft expense) {

    public enum Kind { NOTE, TASK, EVENT, EXPENSE }
}
