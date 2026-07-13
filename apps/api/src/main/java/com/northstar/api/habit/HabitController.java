package com.northstar.api.habit;

import com.northstar.core.habit.HabitInsights;
import com.northstar.core.habit.HabitService;
import com.northstar.core.habit.HabitSummary;
import com.northstar.core.habit.HabitTodaySummary;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/habits")
class HabitController {

    private final HabitService habits;

    HabitController(HabitService habits) {
        this.habits = habits;
    }

    @GetMapping
    @Operation(operationId = "listHabits")
    List<HabitSummary> list(
            @RequestHeader(name = "X-Timezone", required = false) String timezone,
            @RequestParam(name = "includeArchived", defaultValue = "false") boolean includeArchived) {
        ZoneId zone = zone(timezone);
        return habits.list(includeArchived, LocalDate.now(zone));
    }

    @GetMapping("/today")
    @Operation(operationId = "listTodayHabits")
    List<HabitTodaySummary> today(
            @RequestHeader(name = "X-Timezone", required = false) String timezone) {
        return habits.today(zone(timezone));
    }

    @GetMapping("/insights")
    @Operation(operationId = "getHabitInsights")
    HabitInsights insights(
            @RequestHeader(name = "X-Timezone", required = false) String timezone,
            @RequestParam(name = "from", required = false) LocalDate from,
            @RequestParam(name = "to", required = false) LocalDate to,
            @RequestParam(name = "includeArchived", defaultValue = "false") boolean includeArchived) {
        LocalDate through = to == null ? LocalDate.now(zone(timezone)) : to;
        LocalDate start = from == null ? through.minusDays(364) : from;
        return habits.insights(start, through, includeArchived);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createHabit")
    HabitSummary create(
            @RequestHeader(name = "X-Timezone", required = false) String timezone,
            @Valid @RequestBody HabitRequest request) {
        ZoneId zone = zone(timezone);
        LocalDate effective = request.effectiveFrom() == null ? LocalDate.now(zone) : request.effectiveFrom();
        return habits.create(request.title(), request.cue(), request.notes(), request.color(), zone,
                request.frequencyType(), request.days(), request.weeklyTarget(), effective);
    }

    @PutMapping("/{id}")
    @Operation(operationId = "updateHabit")
    HabitSummary update(
            @PathVariable UUID id,
            @RequestHeader(name = "X-Timezone", required = false) String timezone,
            @Valid @RequestBody HabitRequest request) {
        ZoneId zone = zone(timezone);
        LocalDate effective = request.effectiveFrom() == null ? LocalDate.now(zone) : request.effectiveFrom();
        return habits.update(id, request.title(), request.cue(), request.notes(), request.color(), zone,
                request.frequencyType(), request.days(), request.weeklyTarget(), effective);
    }

    @PutMapping("/{id}/check-ins/{date}")
    @Operation(operationId = "setHabitCheckIn")
    HabitTodaySummary setCheckIn(@PathVariable UUID id, @PathVariable LocalDate date,
            @RequestHeader(name = "X-Timezone", required = false) String timezone,
            @Valid @RequestBody HabitCheckInRequest request) {
        return habits.checkIn(id, date, request.status(), zone(timezone));
    }

    @DeleteMapping("/{id}/check-ins/{date}")
    @Operation(operationId = "clearHabitCheckIn")
    HabitTodaySummary clearCheckIn(@PathVariable UUID id, @PathVariable LocalDate date,
            @RequestHeader(name = "X-Timezone", required = false) String timezone) {
        return habits.clearCheckIn(id, date, zone(timezone));
    }

    @PostMapping("/{id}/pause")
    @Operation(operationId = "pauseHabit")
    HabitSummary pause(@PathVariable UUID id,
            @RequestHeader(name = "X-Timezone", required = false) String timezone,
            @RequestBody(required = false) HabitDateRequest request) {
        ZoneId zone = zone(timezone);
        LocalDate date = request == null || request.date() == null ? LocalDate.now(zone) : request.date();
        return habits.pause(id, date);
    }

    @PostMapping("/{id}/resume")
    @Operation(operationId = "resumeHabit")
    HabitSummary resume(@PathVariable UUID id,
            @RequestHeader(name = "X-Timezone", required = false) String timezone,
            @RequestBody(required = false) HabitDateRequest request) {
        ZoneId zone = zone(timezone);
        LocalDate date = request == null || request.date() == null ? LocalDate.now(zone) : request.date();
        return habits.resume(id, date);
    }

    @PatchMapping("/{id}/archived")
    @Operation(operationId = "setHabitArchived")
    HabitSummary setArchived(@PathVariable UUID id,
            @RequestHeader(name = "X-Timezone", required = false) String timezone,
            @RequestBody HabitArchivedRequest request) {
        ZoneId zone = zone(timezone);
        return habits.setArchived(id, request.archived(), LocalDate.now(zone));
    }

    private static ZoneId zone(String value) {
        return value == null || value.isBlank() ? ZoneId.systemDefault() : ZoneId.of(value);
    }
}

