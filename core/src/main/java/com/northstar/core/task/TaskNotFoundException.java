package com.northstar.core.task;

import java.util.UUID;

/** Thrown when a task id does not exist. */
public class TaskNotFoundException extends RuntimeException {

    public TaskNotFoundException(UUID id) {
        super("Task not found: " + id);
    }
}
