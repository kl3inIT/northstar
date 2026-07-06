package com.northstar.core.assistant;

import com.northstar.core.alignment.AlignmentService;
import com.northstar.core.note.NoteDetail;
import java.util.Locale;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Review tool — drafts the daily/weekly Alignment review on demand from chat.
 * {@link AlignmentService} is only defined by apps that wire an LLM (the api's
 * AlignmentConfig), so it is resolved lazily via {@link ObjectProvider}: in an
 * app without it the tool still registers but answers with a clear error.
 */
@Component
class ReviewTools implements NorthstarTool {

    private static final String DRAFT_REVIEW = """
            Drafts the user's daily or weekly review: real numbers (tasks done/overdue, \
            staging notes, what's next) assembled from the database plus a short honest \
            AI commentary, saved as a Journal note. Use when the user asks to review, \
            summarize or reflect on their day or week. Calling it again the same \
            day/week refreshes the existing note instead of creating a duplicate. \
            Returns the full review markdown — quote its commentary and key numbers \
            in your answer.""";

    private final ObjectProvider<AlignmentService> alignment;

    ReviewTools(ObjectProvider<AlignmentService> alignment) {
        this.alignment = alignment;
    }

    @Tool(name = "draft_review", description = DRAFT_REVIEW)
    @McpTool(name = "draft_review", description = DRAFT_REVIEW,
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false,
                    openWorldHint = false))
    ReviewView draftReview(
            @ToolParam(description = "'daily' for today, 'weekly' for the current ISO week")
            @McpToolParam(description = "'daily' for today, 'weekly' for the current ISO week",
                    required = true) String period) {
        AlignmentService service = alignment.getIfAvailable();
        if (service == null) {
            throw new IllegalStateException(
                    "Review drafting needs an LLM and is not configured in this app");
        }
        String normalized = ToolSupport.required("period", period).strip().toLowerCase(Locale.ROOT);
        NoteDetail note = switch (normalized) {
            case "daily" -> service.generateDaily(ToolSupport.zone());
            case "weekly" -> service.generateWeekly(ToolSupport.zone());
            default -> throw new IllegalArgumentException(
                    "period must be 'daily' or 'weekly', got '" + period + "'");
        };
        return new ReviewView(note.title(), note.slug(), note.contentMarkdown());
    }

    record ReviewView(String noteTitle, String noteSlug, String markdown) {
    }
}
