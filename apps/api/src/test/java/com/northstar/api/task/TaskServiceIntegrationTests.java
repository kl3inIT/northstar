package com.northstar.api.task;

import static org.assertj.core.api.Assertions.assertThat;

import com.northstar.core.task.TaskService;
import com.northstar.core.task.TaskStatus;
import com.northstar.core.task.TaskSummary;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Task module acceptance against a real Postgres: Today bundles overdue +
 * due-today + completed-today; completing removes a task from tomorrow's list;
 * upcoming windows exclude today.
 */
@SpringBootTest(properties = "spring.ai.openai.api-key=test-key")
@Testcontainers
class TaskServiceIntegrationTests {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg18");

    @Autowired
    TaskService tasks;

    @Test
    void todayBundlesOverdueDueTodayAndCompletedToday() {
        LocalDate today = LocalDate.now(ZONE);
        TaskSummary overdue = tasks.create("Nộp form học bổng", null, today.minusDays(2), null, null);
        TaskSummary dueToday = tasks.create("Làm docs MLN121", null, today, null, null);
        tasks.create("Mock test IELTS", null, today.plusDays(2), null, null);
        TaskSummary done = tasks.create("Review essay", null, today, null, null);
        tasks.setDone(done.id(), true);

        var todayList = tasks.today(ZONE);

        assertThat(todayList).extracting(TaskSummary::id)
                .contains(overdue.id(), dueToday.id(), done.id());
        assertThat(todayList).extracting(TaskSummary::title).doesNotContain("Mock test IELTS");
        assertThat(todayList.stream().filter(t -> t.id().equals(done.id())).findFirst().orElseThrow().status())
                .isEqualTo(TaskStatus.DONE);
    }

    @Test
    void upcomingWindowsExcludeTodayAndReopenWorks() {
        LocalDate today = LocalDate.now(ZONE);
        tasks.create("Đóng học phí", null, today.plusDays(5), null, null);
        TaskSummary t = tasks.create("Học từ vựng", null, today, null, null);

        assertThat(tasks.upcoming(ZONE, 7)).extracting(TaskSummary::title).contains("Đóng học phí");
        assertThat(tasks.upcoming(ZONE, 7)).extracting(TaskSummary::title).doesNotContain("Học từ vựng");

        tasks.setDone(t.id(), true);
        tasks.setDone(t.id(), false);
        assertThat(tasks.today(ZONE).stream().filter(x -> x.id().equals(t.id())).findFirst().orElseThrow().status())
                .isEqualTo(TaskStatus.OPEN);
    }
}
