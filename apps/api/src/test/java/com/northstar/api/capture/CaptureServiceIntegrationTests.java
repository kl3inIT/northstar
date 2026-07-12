package com.northstar.api.capture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.northstar.core.ai.AiClientRouter;
import com.northstar.core.ai.AiRoute;
import com.northstar.core.ai.AiTask;
import com.northstar.core.capture.CaptureDraft;
import com.northstar.core.capture.CaptureService;
import com.northstar.core.capture.VoiceTranscriber;
import com.northstar.core.finance.FinanceService;
import com.northstar.core.finance.NewTransaction;
import com.northstar.core.finance.TransactionSource;
import com.northstar.core.finance.TransactionType;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.ai.chat.client.ChatClient;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.Resource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Proves the capture wiring end-to-end minus the network: a mocked ChatModel
 * returns the JSON the real model would, and the ChatClient structured-output
 * path must map it into a {@link CaptureDraft}. Catches broken prompt plumbing,
 * bean wiring and entity conversion without spending tokens.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers
class CaptureServiceIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg18");

    @MockitoBean
    ChatModel chatModel;

    @MockitoBean
    AiClientRouter ai;

    @MockitoBean
    TranscriptionModel transcriptionModel;

    @Autowired
    CaptureService capture;

    @Autowired
    FinanceService finance;

    @Autowired
    VoiceTranscriber transcriber;

    @BeforeEach
    void routeMockModel() {
        AiRoute route = new AiRoute("test", "test-model");
        when(ai.route(any(AiTask.class))).thenReturn(route);
        when(ai.client(any(AiRoute.class))).thenReturn(ChatClient.create(chatModel));
    }

    private void modelReturns(String json) {
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(json)))));
    }

    @Test
    void voiceTranscriberDelegatesAndKeepsTheContainerFilename() {
        when(transcriptionModel.transcribe(org.mockito.ArgumentMatchers.any(Resource.class)))
                .thenReturn("nghiên cứu về memory os");

        String text = transcriber.transcribe(new byte[] {1, 2, 3}, "capture.webm");

        assertThat(text).isEqualTo("nghiên cứu về memory os");
        // The filename's extension is how the provider learns the container format.
        ArgumentCaptor<Resource> resource = ArgumentCaptor.forClass(Resource.class);
        verify(transcriptionModel).transcribe(resource.capture());
        assertThat(resource.getValue().getFilename()).isEqualTo("capture.webm");
    }

    @Test
    void noteCaptureMapsIntoNoteDraft() {
        modelReturns("""
                {"kind":"NOTE",
                 "note":{"title":"HSK 4 listening tips","folderPath":"English/HSK",
                         "tags":["hsk","listening"],
                         "contentMarkdown":"Practice with [[HSK 4 Grammar]] daily."}}
                """);

        CaptureDraft draft = capture.draft("mẹo luyện nghe hsk4: nghe mỗi ngày, học ngữ pháp");

        assertThat(draft.kind()).isEqualTo(CaptureDraft.Kind.NOTE);
        assertThat(draft.note().title()).isEqualTo("HSK 4 listening tips");
        assertThat(draft.note().contentMarkdown()).contains("[[HSK 4 Grammar]]");
    }

    @Test
    void forcedKindSkipsClassificationInThePrompt() {
        modelReturns("""
                {"kind":"TASK","task":{"title":"Mua bút dạ quang","dueDate":"2026-07-04"}}
                """);

        CaptureDraft draft = capture.draft("mua bút dạ quang, hạn ngày mai", CaptureDraft.Kind.TASK);

        assertThat(draft.kind()).isEqualTo(CaptureDraft.Kind.TASK);
        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(prompt.capture());
        assertThat(prompt.getValue().getSystemMessage().getText())
                .contains("already chose the kind: TASK");
    }

    @Test
    void classificationPromptCarriesTheActionabilityRubricAndReasoningMaps() {
        modelReturns("""
                {"reasoning":"Chỉ là ý định nghiên cứu, chưa có nội dung kiến thức.",
                 "kind":"TASK","task":{"title":"Research về memoryOS"}}
                """);

        CaptureDraft draft = capture.draft("research về memoryOS");

        assertThat(draft.kind()).isEqualTo(CaptureDraft.Kind.TASK);
        assertThat(draft.reasoning()).contains("ý định");
        assertThat(draft.task().dueDate()).isNull();
        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(prompt.capture());
        String system = prompt.getValue().getSystemMessage().getText();
        // Intent rubric, not keyword lists: the GTD actionability test + the
        // contrastive examples must be what the model classifies with.
        assertThat(system)
                .contains("is it actionable?")
                .contains("WAITING TO BE LOOKED UP")
                .contains("research về memoryOS")
                .contains("Tie-breaker");
    }

    @Test
    void eventCaptureMapsIntoEventDraftAndPromptCarriesTheDeadlineVsEventRubric() {
        modelReturns("""
                {"reasoning":"Cuộc họp chiếm khoảng thời gian đó, user tham dự.",
                 "kind":"EVENT",
                 "event":{"title":"Họp nhóm EXE202","date":"2026-07-07",
                          "startTime":"14:00","endTime":"","notes":"","disciplineName":""}}
                """);

        CaptureDraft draft = capture.draft("họp nhóm EXE202 2h chiều mai");

        assertThat(draft.kind()).isEqualTo(CaptureDraft.Kind.EVENT);
        assertThat(draft.event().title()).isEqualTo("Họp nhóm EXE202");
        assertThat(draft.event().date()).isEqualTo("2026-07-07");
        assertThat(draft.event().startTime()).isEqualTo("14:00");
        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(prompt.capture());
        // The prompt must teach deadline-vs-happening, not time-keyword matching.
        assertThat(prompt.getValue().getSystemMessage().getText())
                .contains("BLOCKING A SPAN OF TIME")
                .contains("nộp form 2h chiều mai")
                .contains("họp nhóm 2h chiều mai");
    }

    @Test
    void taskCaptureMapsIntoTaskDraftWithResolvedDate() {
        modelReturns("""
                {"kind":"TASK",
                 "task":{"title":"Làm docs MLN121","dueDate":"2026-07-03","dueTime":"14:00"}}
                """);

        CaptureDraft draft = capture.draft("hôm nay 2h chiều tôi phải làm docs MLN121");

        assertThat(draft.kind()).isEqualTo(CaptureDraft.Kind.TASK);
        assertThat(draft.task().title()).isEqualTo("Làm docs MLN121");
        assertThat(draft.task().dueDate()).isEqualTo("2026-07-03");
        assertThat(draft.task().dueTime()).isEqualTo("14:00");
    }

    @Test
    void expenseCaptureKeepsEveryAmountAndCarriesTheConstrainedVocabulary() {
        modelReturns("""
                {"reasoning":"Ba khoan chi da xay ra.","kind":"EXPENSE",
                 "expense":{"items":[
                   {"type":"EXPENSE","amount":30000,"description":"an sang",
                    "category":"An uong","occurredOn":"","exceptional":false},
                   {"type":"EXPENSE","amount":45000,"description":"cafe",
                    "category":"Cafe","occurredOn":"","exceptional":false},
                   {"type":"EXPENSE","amount":62000,"description":"grab ve",
                    "category":"Di lai","occurredOn":"","exceptional":false}]}}
                """);

        CaptureDraft draft = capture.draft("nay tieu: sang 30k, cafe 45k, grab ve 62k");

        assertThat(draft.kind()).isEqualTo(CaptureDraft.Kind.EXPENSE);
        assertThat(draft.expense().items()).hasSize(3);
        assertThat(draft.expense().items()).extracting(item -> item.amount())
                .containsExactly(30_000L, 45_000L, 62_000L);
        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(prompt.capture());
        assertThat(prompt.getValue().getSystemMessage().getText())
                .contains("EVERY amount")
                .contains("35k=35000")
                .contains("NEVER treat SD/số dư")
                .contains("TPBank: TK x1234")
                .contains("Expense categories (pick from these)")
                .contains("- Cafe");
    }

    @Test
    void categoryCorrectionBecomesAUserSpecificCaptureExample() {
        LocalDate day = LocalDate.of(2042, 2, 2);
        var transaction = finance.record(new NewTransaction(TransactionType.EXPENSE, 55_000,
                day, "Highlands làm việc", "Ăn uống", false), TransactionSource.CAPTURE);
        finance.update(transaction.id(), transaction.amount(), day, transaction.description(),
                "Cafe", false);
        modelReturns("""
                {"kind":"EXPENSE","expense":{"items":[
                  {"type":"EXPENSE","amount":65000,"description":"Highlands họp team",
                   "category":"Cafe","occurredOn":"","exceptional":false}]}}
                """);

        capture.draft("highlands họp team 65k");

        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(prompt.capture());
        assertThat(prompt.getValue().getSystemMessage().getText())
                .contains("Recent category corrections made by this user")
                .contains("EXPENSE \"Highlands làm việc\" -> \"Cafe\"");
    }

    @Test
    void receiptCaptureUsesTheMultimodalExpensePath() {
        modelReturns("""
                {"kind":"EXPENSE","expense":{"items":[
                  {"type":"EXPENSE","amount":85000,"description":"receipt total",
                   "category":"Khac","occurredOn":"2026-07-10","exceptional":false}]}}
                """);

        CaptureDraft draft = capture.draftFromImage(new byte[] {1, 2, 3}, "image/png");

        assertThat(draft.kind()).isEqualTo(CaptureDraft.Kind.EXPENSE);
        assertThat(draft.expense().items()).singleElement()
                .satisfies(item -> assertThat(item.amount()).isEqualTo(85_000));
        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(prompt.capture());
        assertThat(prompt.getValue().getSystemMessage().getText())
                .contains("already chose the kind: EXPENSE")
                .contains("photo of a receipt/bill");
    }
}
