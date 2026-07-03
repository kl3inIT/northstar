package com.northstar.core.capture;

import com.northstar.core.note.NoteService;
import com.northstar.core.note.NoteSummary;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.ai.chat.client.ChatClient;

/**
 * Turns raw captured text into a {@link NoteDraft} with one LLM call. The prompt
 * carries the existing folders, tags and note titles so the draft lands in the
 * user's real organisation (and wiki-links point at notes that exist) instead of
 * inventing a parallel one.
 *
 * <p>Deliberately NOT a component: the delivering app defines the bean and its
 * {@link ChatClient} (see the api's CaptureConfig), so mcp/worker boot without an
 * LLM configured.
 */
public class CaptureService {

    private static final int MAX_CONTEXT_TITLES = 100;

    private static final String SYSTEM_PROMPT = """
            You turn raw captured text into a note for the user's personal knowledge base.
            Keep the language of the source text. Clean it up into well-structured Markdown
            (headings, lists) without inventing facts that are not in the text.

            Pick a short, specific title. Choose the best-fitting folder from the existing
            folders below when one fits, otherwise propose a sensible new path (segments
            separated by '/'). Suggest 1-4 lowercase tags, reusing existing tags when they
            apply. Where the text clearly relates to one of the existing note titles below,
            reference it inline as a wiki-link: [[Exact Title]].

            Existing folders:
            %s

            Existing tags:
            %s

            Existing note titles:
            %s
            """;

    private final ChatClient chat;
    private final NoteService notes;

    public CaptureService(ChatClient chat, NoteService notes) {
        this.chat = chat;
        this.notes = notes;
    }

    /** One LLM round-trip: raw text in, reviewed-by-user note draft out. */
    public NoteDraft draft(String rawText) {
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
        return chat.prompt()
                .system(SYSTEM_PROMPT.formatted(
                        bulleted(folders), bulleted(tags), bulleted(titles)))
                .user(rawText)
                .call()
                .entity(NoteDraft.class);
    }

    private static String bulleted(Set<String> values) {
        return values.isEmpty() ? "(none yet)"
                : String.join("\n", values.stream().map(v -> "- " + v).toList());
    }
}
