package com.northstar.api.project;

/** Body of PATCH /api/projects/{id}/status. */
record ProjectStatusRequest(boolean done) {
}
