package com.northstar.api.note;

import static org.assertj.core.api.Assertions.assertThat;

import com.northstar.core.note.NoteDetail;
import com.northstar.core.note.NoteRef;
import com.northstar.core.note.NoteService;
import com.northstar.core.note.NoteSummary;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Phase 1 acceptance for the note module, exercised against a real Postgres:
 * wiki links resolve into backlinks (including a link created before its target),
 * and keyword search finds notes by title and body. This is the behaviour Gate 2's
 * context-load test cannot cover — it proves the module actually works, not just
 * that it boots.
 */
@SpringBootTest(properties = "spring.ai.openai.api-key=test-key")
@Testcontainers
class NoteServiceIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg18");

    @Autowired
    NoteService notes;

    @Test
    void wikiLinkCreatesBacklinkBothWays() {
        NoteDetail target = notes.create("Linking words", "English/IELTS",
                "Connectors like however, moreover, in addition.", List.of("ielts"));
        NoteDetail source = notes.create("Writing Cohesion", "English/IELTS",
                "Use [[Linking words]] to connect ideas across sentences.", List.of("ielts", "writing"));

        NoteDetail sourceReloaded = notes.getBySlug(source.slug()).orElseThrow();
        assertThat(sourceReloaded.outgoingLinks())
                .anyMatch(ref -> ref.title().equals("Linking words") && ref.resolved());
        assertThat(sourceReloaded.folderPath()).isEqualTo("English/IELTS");
        assertThat(sourceReloaded.tags()).containsExactlyInAnyOrder("ielts", "writing");

        NoteDetail targetReloaded = notes.getBySlug(target.slug()).orElseThrow();
        assertThat(targetReloaded.backlinks()).extracting(NoteRef::title).contains("Writing Cohesion");
    }

    @Test
    void danglingLinkResolvesWhenTargetIsCreatedLater() {
        NoteDetail source = notes.create("Essay plan", "",
                "Study [[Band 8 sample essays]] for structure.", List.of());
        assertThat(notes.getBySlug(source.slug()).orElseThrow().outgoingLinks())
                .anyMatch(ref -> ref.title().equals("Band 8 sample essays") && !ref.resolved());

        NoteDetail target = notes.create("Band 8 sample essays", "English", "Worked examples.", List.of());
        assertThat(notes.getBySlug(target.slug()).orElseThrow().backlinks())
                .extracting(NoteRef::title).contains("Essay plan");
    }

    @Test
    void keywordSearchMatchesTitleAndBody() {
        notes.create("Coherence and cohesion", "English", "referencing and substitution devices.", List.of());

        assertThat(notes.search("coherence")).extracting(NoteSummary::title).contains("Coherence and cohesion");
        assertThat(notes.search("referencing")).isNotEmpty();
        assertThat(notes.search("nonexistentxyz")).isEmpty();
    }

    @Test
    void searchSnippetHighlightsTheMatchedFragment() {
        notes.create("Band descriptors", "English",
                "Cohesion and coherence are key criteria in the writing band descriptors.", List.of());

        NoteSummary hit = notes.search("descriptors").stream()
                .filter(n -> n.title().equals("Band descriptors")).findFirst().orElseThrow();
        assertThat(hit.snippet()).contains("<mark>descriptors</mark>");
    }
}
