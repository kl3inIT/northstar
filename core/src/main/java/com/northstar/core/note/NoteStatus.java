package com.northstar.core.note;

/**
 * MFI working states for a note (Staging → Resources / Archive). A note is
 * {@code STAGING} while a machine wrote it (capture, MCP) and the user has not
 * reviewed it yet; {@code RESOURCE} once reviewed (or written by hand — you
 * don't review your own writing); {@code ARCHIVED} when discarded but kept.
 */
public enum NoteStatus {
    STAGING,
    RESOURCE,
    ARCHIVED
}
