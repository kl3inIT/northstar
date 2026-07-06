package com.northstar.api.task;

import java.util.UUID;

/** Body of PATCH /api/tasks/{id}/project — null {@code projectId} detaches. */
record TaskProjectRequest(UUID projectId) {
}
