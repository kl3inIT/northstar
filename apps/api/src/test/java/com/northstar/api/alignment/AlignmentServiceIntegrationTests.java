package com.northstar.api.alignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.northstar.core.alignment.AlignmentService;
import com.northstar.core.finance.FinanceService;
import com.northstar.core.finance.NewTransaction;
import com.northstar.core.finance.TransactionSource;
import com.northstar.core.finance.TransactionType;
import com.northstar.core.note.NoteDetail;
import com.northstar.core.task.TaskService;
import com.northstar.core.task.TaskSummary;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Proves the alignment wiring end-to-end minus the network: real task/note data
 * in Postgres, a mocked ChatModel for the commentary. Covers the deterministic
 * facts (an overdue task must be named with its days late), the upsert contract
 * (regenerating refreshes the same Journal note), and the facts-only fallback
 * when the LLM call fails.
 */
@SpringBootTest(properties = "spring.ai.openai.api-key=test-key")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers
class AlignmentServiceIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg18");

    @MockitoBean
    ChatModel chatModel;

    @Autowired
    AlignmentService alignment;

    @Autowired
    TaskService tasks;

    @Autowired
    FinanceService finance;

    private final ZoneId zone = ZoneId.systemDefault();

    private static ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    /** The chain is two calls: step-back observations (prose), then the writer (structured). */
    private void modelReturns(String observations, String commentaryJson) {
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(response(observations), response(commentaryJson));
    }

    @Test
    void dailyReviewCarriesRealFactsAndUpsertsOneJournalNote() {
        LocalDate today = LocalDate.now(zone);
        tasks.create("Nộp essay Chevening", null, today.minusDays(2), null, null);
        TaskSummary doneTask = tasks.create("Ôn từ vựng HSK", null, today, null, null);
        tasks.setDone(doneTask.id(), true);
        modelReturns("- 'Nộp essay Chevening' is 2 days overdue",
                """
                {"commentary": "A tidy day.", "priority": "Nộp essay Chevening."}""");

        NoteDetail first = alignment.generateDaily(zone);

        assertThat(first.folderPath()).isEqualTo("Journal");
        assertThat(first.tags()).contains("alignment", "daily");
        assertThat(first.contentMarkdown())
                .contains("**Tomorrow's priority:** Nộp essay Chevening.")
                .contains("Nộp essay Chevening — overdue 2 days")
                .contains("Ôn từ vựng HSK");

        NoteDetail second = alignment.generateDaily(zone);
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(alignment.findDaily(zone)).map(NoteDetail::id).hasValue(first.id());
    }

    @Test
    void llmFailureStillShipsTheFactsOnlyNote() {
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("LLM down"));

        NoteDetail note = alignment.generateWeekly(zone);

        assertThat(note.contentMarkdown())
                .contains("the raw numbers are below")
                .contains("## The numbers, week");
        assertThat(alignment.findWeekly(zone)).map(NoteDetail::id).hasValue(note.id());
    }

    @Test
    void weeklyReviewSeparatesOrdinaryAndOneOffSpending() {
        LocalDate monday = LocalDate.now(zone).with(java.time.DayOfWeek.MONDAY);
        finance.record(new NewTransaction(TransactionType.EXPENSE, 35_000, monday,
                "an sang", "An uong", false), TransactionSource.CAPTURE);
        finance.record(new NewTransaction(TransactionType.EXPENSE, 500_000, monday.plusDays(1),
                "qua cuoi", "Hieu hi", true), TransactionSource.CAPTURE);
        modelReturns("- Spending includes one routine item and one one-off",
                """
                {"commentary":"Spending was split between routine food and a one-off gift.",
                 "priority":"Keep the week focused."}
                """);

        NoteDetail note = alignment.generateWeekly(zone);

        assertThat(note.contentMarkdown())
                .contains("**Money this week")
                .contains("Ordinary spending: 35.000")
                .contains("One-offs (1, total 500.000")
                .contains("qua cuoi 500.000");
    }
}
