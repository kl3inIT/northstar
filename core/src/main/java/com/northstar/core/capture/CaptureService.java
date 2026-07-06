package com.northstar.core.capture;

import com.northstar.core.discipline.DisciplineService;
import com.northstar.core.discipline.DisciplineSummary;
import com.northstar.core.note.NoteService;
import com.northstar.core.note.NoteSummary;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * Classifies raw captured text (task vs note) and shapes it into a reviewable
 * draft with one LLM call. The prompt carries today's date (so "hôm nay/mai/thứ 6"
 * resolve to real dates) and the existing folders, tags and note titles (so note
 * drafts land in the user's real organisation and wiki-links point at notes that
 * exist).
 *
 * <p>Deliberately NOT a component: the delivering app defines the bean and its
 * {@link ChatClient} (see the api's CaptureConfig), so mcp/worker boot without an
 * LLM configured.
 */
public class CaptureService {

    private static final int MAX_CONTEXT_TITLES = 100;
    private static final int MAX_CONTEXT_NOTES = 300;

    private static final String SYSTEM_PROMPT = """
            You are the capture inbox of a personal knowledge base + task manager.
            Classify the captured text and shape it, keeping the language of the source.

            Today is %s (%s).

            Classify by INTENT, never by surface keywords. The single test (GTD
            "is it actionable?"): after this item is saved, is it WAITING FOR THE
            USER TO ACT (task) or WAITING TO BE LOOKED UP (note)?
            - TASK: a commitment or intention to do something that has not
              happened yet — even with no deadline (an undated task is fine).
              A bare verb+topic with no substance is an intention, so a TASK.
            - NOTE: the text already CONTAINS the knowledge — a fact, insight,
              summary, idea, quote. It informs; it does not wait to be done.
            - Tie-breaker: intention without content -> TASK; content, even when
              it opens with a verb, -> NOTE.

            Contrastive examples (input -> kind — why):
            - "research về memoryOS" -> TASK — only an intention, no knowledge
              content yet; no time reference, so omit dueDate.
            - "MemoryOS: memory 3 tầng cho LLM agent, mô phỏng cách OS quản lý
              RAM/disk" -> NOTE — the knowledge is already in the text.
            - "hôm nay học được cách dùng 把 trong câu chữ Hán" -> NOTE — opens
              with a verb, but it records something learned.
            - "nộp form học bổng trước thứ 6" -> TASK — a commitment with a
              deadline; resolve "thứ 6" to a date.

            In `reasoning`, argue the user's intent in one short sentence BEFORE
            choosing `kind`.

            For a TASK fill `task` only:
            - title: short imperative phrase (drop filler like "hôm nay tôi phải")
            - dueDate: ISO date. Resolve relative words against today ("hôm nay"=today,
              "mai"=tomorrow, "thứ 6"=the next Friday). Omit if no time reference.
            - dueTime: ISO time ONLY when the text names a clock time ("5pm", "17h").
              NEVER invent one — "hôm nay"/"mai" are dates, not times; most tasks
              have no dueTime.
            - notes: extra detail beyond the title, or omit.
            - disciplineName: the ONE existing discipline (exact name from the list
              below) this task clearly trains, else omit. Never invent a new one.

            Existing disciplines:
            %s

            For a NOTE fill `note` only:
            - Clean the text into Markdown WITHOUT inventing facts or padding:
              a short capture stays short — never restate the title as a heading,
              never turn a single sentence into bullet points.
            - title: short and specific. folderPath: best-fitting existing folder below,
              else a sensible new path. tags: 1-4 lowercase, reusing existing ones.
            - Reference existing notes inline as [[Exact Title]] ONLY when the text
              clearly relates to them — never link just because a title exists.

            Existing folders:
            %s

            Existing tags:
            %s

            Existing note titles:
            %s
            """;

    private final ChatClient chat;
    private final NoteService notes;
    private final DisciplineService disciplines;
    private final ZoneId zone;

    public CaptureService(ChatClient chat, NoteService notes, DisciplineService disciplines, ZoneId zone) {
        this.chat = chat;
        this.notes = notes;
        this.disciplines = disciplines;
        this.zone = zone;
    }

    /** One LLM round-trip: raw text in, classified reviewable draft out. */
    public CaptureDraft draft(String rawText) {
        return draft(rawText, null);
    }

    /**
     * Like {@link #draft(String)}, but when {@code forcedKind} is non-null the
     * user already chose task-vs-note — the model only shapes the draft.
     */
    public CaptureDraft draft(String rawText, CaptureDraft.Kind forcedKind) {
        List<NoteSummary> existing = notes
                .list(PageRequest.of(0, MAX_CONTEXT_NOTES, Sort.by(Sort.Direction.DESC, "updatedAt")))
                .getContent();
        Set<String> folders = new LinkedHashSet<>();
        Set<String> tags = new LinkedHashSet<>();
        Set<String> titles = new LinkedHashSet<>();
        for (NoteSummary note : existing) {
            if (!note.folderPath().isBlank()) {
                folders.add(note.folderPath());
            }
            tags.addAll(note.tags());
            if (titles.size() < MAX_CONTEXT_TITLES) {
                titles.add(note.title());
            }
        }
        Set<String> disciplineNames = new LinkedHashSet<>();
        for (DisciplineSummary discipline : disciplines.list()) {
            disciplineNames.add(discipline.name());
        }
        LocalDate today = LocalDate.now(zone);
        String system = SYSTEM_PROMPT.formatted(
                today, today.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                bulleted(disciplineNames), bulleted(folders), bulleted(tags), bulleted(titles));
        if (forcedKind != null) {
            system += """

                    The user already chose the kind: %s. Do NOT reclassify — set kind
                    to %s and shape the matching draft following the rules above.
                    """.formatted(forcedKind, forcedKind);
        }
        // Hardened structured output: the schema rides as an API-level constraint
        // (OpenAI structured output guarantees conformant JSON), instead of being
        // prompt text the model may drift from. Client-side validateSchema() is
        // deliberately NOT added: it is the fallback for providers without native
        // structured output, and its retry loop would just burn tokens here.
        return chat.prompt()
                .system(system)
                .user(rawText)
                .call()
                .entity(CaptureDraft.class, ChatClient.EntityParamSpec::useProviderStructuredOutput);
    }

    private static String bulleted(Set<String> values) {
        return values.isEmpty() ? "(none yet)"
                : String.join("\n", values.stream().map(v -> "- " + v).toList());
    }
}
