package com.northstar.api.capture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.northstar.core.capture.CaptureDraft;
import com.northstar.core.capture.CaptureService;
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
@SpringBootTest(properties = "spring.ai.openai.api-key=test-key")
@Testcontainers
class CaptureServiceIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg18");

    @MockitoBean
    ChatModel chatModel;

    @Autowired
    CaptureService capture;

    private void modelReturns(String json) {
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(json)))));
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
}
