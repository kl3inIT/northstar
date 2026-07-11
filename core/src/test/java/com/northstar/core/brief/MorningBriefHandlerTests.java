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
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MorningBriefHandlerTests {

    @Test
    void createsOneStagingNoteRanksSectionsAndDeduplicatesTrackingUrls() {
        NoteService notes = mock(NoteService.class);
        MorningBriefHandler handler = new MorningBriefHandler(List.of(
                source("github", "GitHub releases", List.of(candidate(BriefKind.OFFICIAL,
                        "Codex release", "https://example.com/release?utm_source=mail", 0))),
                source("rss", "RSS & Atom", List.of(candidate(BriefKind.PEOPLE,
                        "Release commentary", "https://example.com/release?ref=home", 0))),
                source("hacker-news", "Hacker News", List.of(candidate(BriefKind.COMMUNITY,
                        "Community discussion", "https://news.example/item/42", 180)))), notes);
        UUID noteId = UUID.randomUUID();
        NoteDetail note = mock(NoteDetail.class);
        when(note.id()).thenReturn(noteId);
        when(notes.findByTitle("Morning Brief - Daily technology brief - 2026-07-11")).thenReturn(Optional.empty());
        when(notes.create(any(), any(), any(), any(), eq(NoteStatus.STAGING))).thenReturn(note);

        AutomationExecutionContext context = context("Daily technology brief");
        MorningBriefConfig config = config(List.of("github", "rss", "hacker-news"));

        AutomationHandlerResult output = handler.execute(context, config);

        ArgumentCaptor<String> markdown = ArgumentCaptor.forClass(String.class);
        verify(notes).create(eq("Morning Brief - Daily technology brief - 2026-07-11"), eq("Briefs"), markdown.capture(),
                eq(List.of("morning-brief", "technology", "research")), eq(NoteStatus.STAGING));
        assertThat(markdown.getValue())
                .contains("# Morning Brief", "## Official releases", "## Community radar", "## Source status")
                .containsOnlyOnce("https://example.com/release")
                .doesNotContain("utm_source", "ref=home");
        assertThat(output.outputType()).isEqualTo("NOTE");
        assertThat(output.outputId()).isEqualTo(noteId);
        assertThat(output.metrics()).containsEntry("providers", 3).containsEntry("sources", 2);
    }

    @Test
    void keepsSuccessfulSourcesWhenOneProviderFails() {
        NoteService notes = mock(NoteService.class);
        NoteDetail note = mock(NoteDetail.class);
        when(note.id()).thenReturn(UUID.randomUUID());
        when(notes.findByTitle(any())).thenReturn(Optional.empty());
        when(notes.create(any(), any(), any(), any(), eq(NoteStatus.STAGING))).thenReturn(note);
        MorningBriefHandler handler = new MorningBriefHandler(List.of(
                source("github", "GitHub releases", List.of(candidate(BriefKind.OFFICIAL,
                        "Flutter release", "https://github.com/flutter/flutter/releases/tag/v1", 0))),
                failingSource("firecrawl", "Firecrawl discovery")), notes);

        AutomationHandlerResult output = handler.execute(context("Daily technology brief"),
                config(List.of("github", "firecrawl")));

        assertThat(output.metrics()).containsEntry("successfulProviders", 1L)
                .containsEntry("failedProviders", 1L);
        ArgumentCaptor<String> markdown = ArgumentCaptor.forClass(String.class);
        verify(notes).create(any(), eq("Briefs"), markdown.capture(), any(), eq(NoteStatus.STAGING));
        assertThat(markdown.getValue()).contains("Firecrawl discovery** — unavailable for this run");
    }

    @Test
    void rerunUpdatesTheExistingAutomationDayNote() {
        NoteService notes = mock(NoteService.class);
        MorningBriefHandler handler = new MorningBriefHandler(List.of(source("github", "GitHub releases",
                List.of(candidate(BriefKind.OFFICIAL, "Java release", "https://example.com/java", 0)))), notes);
        NoteDetail existing = mock(NoteDetail.class);
        when(existing.id()).thenReturn(UUID.randomUUID());
        when(existing.version()).thenReturn(3L);
        when(notes.findByTitle("Morning Brief - Java brief - 2026-07-11")).thenReturn(Optional.of(existing));
        when(notes.update(any(), any(), any(), any(), any(), any())).thenReturn(existing);

        handler.execute(context("Java brief"), config(List.of("github")));

        verify(notes).update(eq(existing.id()), eq("Morning Brief - Java brief - 2026-07-11"),
                eq("Briefs"), any(), eq(List.of("morning-brief", "technology", "research")), eq(3L));
    }

    @Test
    void rendersVietnameseSectionAndStatusLabels() {
        NoteService notes = mock(NoteService.class);
        NoteDetail note = mock(NoteDetail.class);
        when(note.id()).thenReturn(UUID.randomUUID());
        when(notes.findByTitle(any())).thenReturn(Optional.empty());
        when(notes.create(any(), any(), any(), any(), eq(NoteStatus.STAGING))).thenReturn(note);
        MorningBriefHandler handler = new MorningBriefHandler(List.of(source("github", "GitHub releases",
                List.of(candidate(BriefKind.OFFICIAL, "Codex release", "https://example.com/codex", 0)))), notes);
        MorningBriefConfig config = new MorningBriefConfig("vi", 24, 5, List.of(), List.of(), List.of(), true,
                List.of("github"), List.of("openai/codex"), List.of(), List.of(), 25);

        handler.execute(context("Daily technology brief"), config);

        ArgumentCaptor<String> markdown = ArgumentCaptor.forClass(String.class);
        verify(notes).create(any(), eq("Briefs"), markdown.capture(), any(), eq(NoteStatus.STAGING));
        assertThat(markdown.getValue()).contains("# Bản tin sáng", "## Phát hành chính thức", "## Trạng thái nguồn");
    }

    @Test
    void rotatesAcrossSourcesBeforeTakingAnotherItemFromTheSameSource() {
        NoteService notes = mock(NoteService.class);
        NoteDetail note = mock(NoteDetail.class);
        when(note.id()).thenReturn(UUID.randomUUID());
        when(notes.findByTitle(any())).thenReturn(Optional.empty());
        when(notes.create(any(), any(), any(), any(), eq(NoteStatus.STAGING))).thenReturn(note);
        MorningBriefHandler handler = new MorningBriefHandler(List.of(source("github", "GitHub releases", List.of(
                candidate(BriefKind.OFFICIAL, "Alpha one", "https://example.com/a1", "Source A", 30),
                candidate(BriefKind.OFFICIAL, "Alpha two", "https://example.com/a2", "Source A", 20),
                candidate(BriefKind.OFFICIAL, "Alpha three", "https://example.com/a3", "Source A", 10),
                candidate(BriefKind.OFFICIAL, "Beta one", "https://example.com/b1", "Source B", 5)))), notes);
        MorningBriefConfig config = new MorningBriefConfig("en", 24, 3, List.of(), List.of(), List.of(), true,
                List.of("github"), List.of(), List.of(), List.of(), 25);

        handler.execute(context("Fair brief"), config);

        ArgumentCaptor<String> markdown = ArgumentCaptor.forClass(String.class);
        verify(notes).create(any(), eq("Briefs"), markdown.capture(), any(), eq(NoteStatus.STAGING));
        assertThat(markdown.getValue())
                .contains("Alpha one", "Beta one", "Alpha two")
                .doesNotContain("Alpha three");
        assertThat(markdown.getValue().indexOf("Beta one"))
                .isLessThan(markdown.getValue().indexOf("Alpha two"));
    }

    private static MorningBriefConfig config(List<String> sources) {
        return new MorningBriefConfig("en", 24, 5, List.of("Java", "AI agents"), List.of(), List.of(), true,
                sources, List.of("openai/codex"), List.of(), List.of(), 25);
    }

    private static AutomationExecutionContext context(String name) {
        return new AutomationExecutionContext(UUID.randomUUID(), name, UUID.randomUUID(),
                Instant.parse("2026-07-11T00:15:00Z"), ZoneId.of("Asia/Bangkok"),
                AutomationRunKind.SCHEDULED, 1);
    }

    private static BriefCandidate candidate(BriefKind kind, String title, String url, int score) {
        return candidate(kind, title, url, "Test source", score);
    }

    private static BriefCandidate candidate(BriefKind kind, String title, String url, String source, int score) {
        return new BriefCandidate(kind, title, url, "Important update.", source, "Author",
                Instant.parse("2026-07-10T12:00:00Z"), score);
    }

    private static BriefSourceProvider source(String id, String displayName, List<BriefCandidate> items) {
        return new BriefSourceProvider() {
            @Override public String id() { return id; }
            @Override public String displayName() { return displayName; }
            @Override public BriefSourceResult collect(BriefCollectionRequest request) {
                return new BriefSourceResult(items, Map.of());
            }
        };
    }

    private static BriefSourceProvider failingSource(String id, String displayName) {
        return new BriefSourceProvider() {
            @Override public String id() { return id; }
            @Override public String displayName() { return displayName; }
            @Override public BriefSourceResult collect(BriefCollectionRequest request) {
                throw new IllegalStateException("unavailable");
            }
        };
    }
}
