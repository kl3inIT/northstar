package com.northstar.api.habit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.northstar.core.habit.HabitCheckInStatus;
import com.northstar.core.habit.HabitDayState;
import com.northstar.core.habit.HabitFrequencyType;
import com.northstar.core.habit.HabitService;
import com.northstar.core.shared.ColorName;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import com.jayway.jsonpath.JsonPath;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers
class HabitServiceIntegrationTests {

    private static final ZoneId ZONE = ZoneId.of("Asia/Bangkok");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg18");

    @Autowired HabitService habits;
    @Autowired MockMvc mvc;

    @Test
    void schedulePauseCheckInAndInsightsPreserveMeaning() {
        LocalDate monday = LocalDate.of(2026, 1, 5);
        var habit = habits.create("Read after dinner", "After dinner", null, ColorName.GREEN,
                ZONE, HabitFrequencyType.ON_DAYS,
                Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), 0, monday);

        habits.checkIn(habit.id(), monday, HabitCheckInStatus.DONE, ZONE);
        habits.checkIn(habit.id(), monday.plusDays(1), HabitCheckInStatus.EXCUSED, ZONE);
        habits.pause(habit.id(), monday.plusDays(2));
        habits.resume(habit.id(), monday.plusDays(4));

        var firstWeek = habits.insight(habit.id(), monday, monday.plusDays(6));
        assertThat(firstWeek.expected()).isEqualTo(2);
        assertThat(firstWeek.completed()).isEqualTo(1);
        assertThat(firstWeek.excused()).isEqualTo(1);
        assertThat(firstWeek.consistency()).isEqualTo(50);
        assertThat(firstWeek.days()).extracting(day -> day.state()).containsExactly(
                HabitDayState.DONE, HabitDayState.EXCUSED, HabitDayState.PAUSED,
                HabitDayState.PAUSED, HabitDayState.MISSED,
                HabitDayState.NOT_SCHEDULED, HabitDayState.NOT_SCHEDULED);

        LocalDate nextMonday = monday.plusWeeks(1);
        habits.update(habit.id(), habit.title(), habit.cue(), habit.notes(), habit.color(), ZONE,
                HabitFrequencyType.WEEKLY_TARGET, Set.of(), 3, nextMonday);
        habits.checkIn(habit.id(), nextMonday, HabitCheckInStatus.DONE, ZONE);
        habits.checkIn(habit.id(), nextMonday.plusDays(1), HabitCheckInStatus.DONE, ZONE);
        habits.checkIn(habit.id(), nextMonday.plusDays(2), HabitCheckInStatus.DONE, ZONE);

        var secondWeek = habits.insight(habit.id(), nextMonday, nextMonday.plusDays(6));
        assertThat(secondWeek.expected()).isEqualTo(3);
        assertThat(secondWeek.completed()).isEqualTo(3);
        assertThat(secondWeek.consistency()).isEqualTo(100);

        assertThat(habits.insight(habit.id(), monday, monday.plusDays(6)).expected())
                .isEqualTo(2);
    }

    @Test
    void restContractSupportsTodayUndoPauseAndArchive() throws Exception {
        LocalDate today = LocalDate.now(ZONE);
        String body = """
                {
                  "title": "Walk outside",
                  "cue": "After lunch",
                  "notes": "Minimum ten minutes",
                  "color": "BLUE",
                  "frequencyType": "ON_DAYS",
                  "days": ["%s"],
                  "weeklyTarget": 1
                }
                """.formatted(today.getDayOfWeek());

        String response = mvc.perform(post("/api/habits")
                        .header("X-Timezone", ZONE.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Walk outside"))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(response, "$.id");

        mvc.perform(get("/api/habits/today").header("X-Timezone", ZONE.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].dueToday").value(true))
                .andExpect(jsonPath("$[0].todayState").value("OPEN"));

        mvc.perform(put("/api/habits/{id}/check-ins/{date}", id, today)
                        .header("X-Timezone", ZONE.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DONE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todayState").value("DONE"));

        mvc.perform(delete("/api/habits/{id}/check-ins/{date}", id, today)
                        .header("X-Timezone", ZONE.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todayState").value("OPEN"));

        mvc.perform(post("/api/habits/{id}/pause", id)
                        .header("X-Timezone", ZONE.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.paused").value(true));
        mvc.perform(post("/api/habits/{id}/resume", id)
                        .header("X-Timezone", ZONE.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.paused").value(false));
        mvc.perform(patch("/api/habits/{id}/archived", id)
                        .header("X-Timezone", ZONE.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"archived\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ARCHIVED"));
        mvc.perform(get("/api/habits").header("X-Timezone", ZONE.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void openApiMarksHabitOperationsAndPrimitiveFields() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/habits/today'].get.operationId")
                        .value("listTodayHabits"))
                .andExpect(jsonPath("$.paths['/api/habits/{id}/check-ins/{date}'].put.operationId")
                        .value("setHabitCheckIn"))
                .andExpect(jsonPath("$.components.schemas.HabitTodaySummary.required",
                        hasItems("dueToday", "completedThisWeek", "targetThisWeek",
                                "consistency30", "consistency90", "currentStreak", "bestStreak")))
                .andExpect(jsonPath("$.components.schemas.HabitSummary.required",
                        hasItems("paused", "version")));
    }
}
