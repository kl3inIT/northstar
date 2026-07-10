package com.northstar.core.note;

import jakarta.validation.constraints.NotNull;

/**
 * One knowledge-base folder with its live note count. {@code path} is the full
 * folder path ({@code ''} = root); the tree shape is derived from the paths.
 */
public record FolderSummary(
        @NotNull String path,
        long noteCount) {
}
