package com.northstar.api.habit;

import io.swagger.v3.oas.annotations.media.Schema;

record HabitArchivedRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean archived) {
}

