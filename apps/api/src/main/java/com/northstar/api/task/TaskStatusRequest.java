package com.northstar.api.task;

/** Toggle payload: {@code done=true} completes the task, {@code false} reopens it. */
record TaskStatusRequest(boolean done) {
}
