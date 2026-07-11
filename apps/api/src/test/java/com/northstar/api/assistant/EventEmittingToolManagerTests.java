package com.northstar.api.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EventEmittingToolManagerTests {

    @Test
    void extractsWebSourcesFromSearchResults() {
        List<Part> sources = EventEmittingToolManager.sources(
                "search_web",
                "call-web",
                Map.of("sources", List.of(
                        Map.of("url", "https://example.com/article", "title", "Example article"),
                        Map.of("title", "Missing URL"))));

        assertThat(sources).containsExactly(
                new Part.SourceUrl("call-web-0", "https://example.com/article", "Example article"));
    }

    @Test
    void extractsNotesAndFilesAsDocumentSources() {
        List<Part> sources = EventEmittingToolManager.sources(
                "search_knowledge",
                "call-knowledge",
                List.of(
                        Map.of(
                                "source", "note",
                                "title", "Northstar App Behavior",
                                "slug", "northstar-app-behavior",
                                "url", "/notes/northstar-app-behavior"),
                        Map.of(
                                "source", "file",
                                "title", "Research PDF",
                                "url", "/api/files/00000000-0000-0000-0000-000000000001")));

        assertThat(sources).containsExactly(
                new Part.SourceDocument(
                        "/notes/northstar-app-behavior",
                        "text/markdown",
                        "Northstar App Behavior",
                        "northstar-app-behavior.md"),
                new Part.SourceDocument(
                        "/api/files/00000000-0000-0000-0000-000000000001",
                        "application/octet-stream",
                        "Research PDF",
                        "Research PDF"));
    }
}
