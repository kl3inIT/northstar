package com.northstar.api.task;

import java.time.LocalDate;

/** Star/unstar payload: the "do" day; null clears the plan. Deadline untouched. */
record TaskPlannedRequest(LocalDate plannedDate) {
}
