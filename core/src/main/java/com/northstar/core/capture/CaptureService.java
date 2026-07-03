package com.northstar.core.capture;

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

    private static final String SYSTEM_PROMPT = """
            You are the capture inbox of a personal knowledge base + task manager.
            Classify the captured text and shape it, keeping the language of the source.

            Today is %s (%s).

            CLASSIFY as TASK when the text is primarily something the user must do —
            an action, errand or deadline ("phải làm", "nộp", "deadline", "remember to").
            Then fill `task` only:
            - title: short imperative phrase (drop filler like "hôm nay tôi phải")
            - dueDate: ISO date. Resolve relative words against today ("hôm nay"=today,
              "mai"=tomorrow, "thứ 6"=the next Friday). Omit if no time reference.
            - dueTime: ISO time only when the text names a clock time.
            - notes: extra detail beyond the title, or omit.

            CLASSIFY as NOTE when the text is knowledge worth keeping (an idea, a
            learning, reference material). Then fill `note` only:
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
    private final ZoneId zone;

    public CaptureService(ChatClient chat, NoteService notes, ZoneId zone) {
        this.chat = chat;
        this.notes = notes;
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
        List<NoteSummary> existing = notes.list();
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
        LocalDate today = LocalDate.now(zone);
        String system = SYSTEM_PROMPT.formatted(
                today, today.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                bulleted(folders), bulleted(tags), bulleted(titles));
        if (forcedKind != null) {
            system += """

                    The user already chose the kind: %s. Do NOT reclassify — set kind
                    to %s and shape the matching draft following the rules above.
                    """.formatted(forcedKind, forcedKind);
        }
        return chat.prompt()
                .system(system)
                .user(rawText)
                .call()
                .entity(CaptureDraft.class);
    }

    private static String bulleted(Set<String> values) {
        return values.isEmpty() ? "(none yet)"
                : String.join("\n", values.stream().map(v -> "- " + v).toList());
    }
}
