package com.northstar.core.brief;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.northstar.core.automation.AutomationExecutionContext;
import com.northstar.core.automation.AutomationHandlerResult;
import com.northstar.core.automation.AutomationRunKind;
import com.northstar.core.note.NoteDetail;
import com.northstar.core.note.NoteService;
import com.northstar.core.note.NoteStatus;
import com.northstar.core.web.WebResearchService;
import com.northstar.core.web.WebSearchRequest;
import com.northstar.core.web.WebSearchResult;
import com.northstar.core.web.WebSource;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MorningBriefHandlerTests {

    @Test
    void createsOneStagingNoteAndDeduplicatesTrackingUrls() {
        WebResearchService research = mock(WebResearchService.class);
        NoteService notes = mock(NoteService.class);
        MorningBriefHandler handler = new MorningBriefHandler(research, notes);
        UUID noteId = UUID.randomUUID();
        NoteDetail note = mock(NoteDetail.class);
        when(note.id()).thenReturn(noteId);
        when(notes.findByTitle("Morning Brief - Daily technology brief - 2026-07-11")).thenReturn(Optional.empty());
        when(notes.create(any(), any(), any(), any(), eq(NoteStatus.STAGING))).thenReturn(note);
        when(research.search(any())).thenAnswer(invocation -> result(invocation.getArgument(0)));

        AutomationExecutionContext context = new AutomationExecutionContext(
                UUID.randomUUID(), "Daily technology brief", UUID.randomUUID(),
                Instant.parse("2026-07-11T00:15:00Z"), ZoneId.of("Asia/Bangkok"),
                AutomationRunKind.SCHEDULED, 1);
        MorningBriefConfig config = new MorningBriefConfig(
                "vi", 24, 5, List.of(), List.of("Java news", "AI agent news"), List.of(), true);

        AutomationHandlerResult output = handler.execute(context, config);

        ArgumentCaptor<String> markdown = ArgumentCaptor.forClass(String.class);
        verify(notes).create(eq("Morning Brief - Daily technology brief - 2026-07-11"), eq("Briefs"), markdown.capture(),
                eq(List.of("morning-brief", "research")), eq(NoteStatus.STAGING));
        assertThat(markdown.getValue())
                .contains("# Morning Brief", "## Java news", "## AI agent news")
                .containsOnlyOnce("https://example.com/release?utm_source=mail")
                .doesNotContain("https://example.com/release?ref=home");
        assertThat(output.outputType()).isEqualTo("NOTE");
        assertThat(output.outputId()).isEqualTo(noteId);
        assertThat(output.metrics()).containsEntry("queries", 2).containsEntry("sources", 1);
    }

    @Test
    void rerunUpdatesTheExistingAutomationDayNote() {
        WebResearchService research = mock(WebResearchService.class);
        NoteService notes = mock(NoteService.class);
        MorningBriefHandler handler = new MorningBriefHandler(research, notes);
        NoteDetail existing = mock(NoteDetail.class);
        when(existing.id()).thenReturn(UUID.randomUUID());
        when(existing.version()).thenReturn(3L);
        when(notes.findByTitle("Morning Brief - Java brief - 2026-07-11"))
                .thenReturn(Optional.of(existing));
        when(notes.update(any(), any(), any(), any(), any(), any())).thenReturn(existing);
        when(research.search(any())).thenAnswer(invocation -> result(invocation.getArgument(0)));
        AutomationExecutionContext context = new AutomationExecutionContext(
                UUID.randomUUID(), "Java brief", UUID.randomUUID(),
                Instant.parse("2026-07-11T00:15:00Z"), ZoneId.of("Asia/Bangkok"),
                AutomationRunKind.MANUAL, 1);
        MorningBriefConfig config = new MorningBriefConfig(
                "vi", 24, 5, List.of("Java"), List.of(), List.of(), true);

        handler.execute(context, config);

        verify(notes).update(eq(existing.id()), eq("Morning Brief - Java brief - 2026-07-11"),
                eq("Briefs"), any(), eq(List.of("morning-brief", "research")), eq(3L));
    }

    private static WebSearchResult result(WebSearchRequest request) {
        boolean java = request.query().startsWith("Java news");
        String url = java
                ? "https://example.com/release?utm_source=mail"
                : "https://example.com/release?ref=home";
        return new WebSearchResult(request.query(), "test", java ? "Java update" : "Agent update",
                List.of(new WebSource("Release", url, "Summary", Instant.parse("2026-07-10T12:00:00Z"))),
                Instant.parse("2026-07-11T00:00:00Z"), null);
    }
}
