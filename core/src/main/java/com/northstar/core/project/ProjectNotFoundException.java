package com.northstar.core.project;

import java.util.UUID;

/** Thrown when a project id does not exist. */
public class ProjectNotFoundException extends RuntimeException {

    public ProjectNotFoundException(UUID id) {
        super("Project not found: " + id);
    }
}
