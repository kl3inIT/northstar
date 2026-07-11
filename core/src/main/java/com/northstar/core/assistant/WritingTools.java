package com.northstar.core.assistant;

import com.northstar.core.study.GrammarWeakness;
import com.northstar.core.study.WritingFeedbackSummary;
import com.northstar.core.study.WritingService;
import java.util.List;
import java.util.UUID;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Writing-feedback HISTORY tools — read and delete only. Grading itself is
 * grade_writing, an api-only tool (it needs the LLM the mcp app deliberately
 * does not have), so these stay dual-transport while grading does not.
 */
@Component
class WritingTools implements NorthstarTool {

    private static final String LIST = """
            Every graded essay, newest first: task label, unofficial band \
            estimate range (overallMin..overallMax), per-criterion bands with \
            justification (criteria JSON), the recurring-error patterns \
            extracted from it (topErrors JSON), the feedback summary, word \
            count, and which grader model produced it. Use to answer "band \
            writing đang lên hay xuống", to see which error patterns keep \
            recurring across essays, and to resolve the id \
            delete_writing_feedback needs. Estimates are unofficial — never \
            present them as real IELTS scores.""";

    private static final String DELETE = """
            Permanently delete one graded essay and its feedback by UUID (ids \
            come from list_writing_feedback). No undo, and the grading cannot \
            be reproduced (a re-grade may score differently) — only call on \
            explicit user intent, and name the essay you deleted in your \
            reply.""";

    private static final String WEAKNESSES = """
            The user's recurring grammar/lexis error patterns, aggregated from \
            every graded essay and speaking-practice transcript: label, how \
            many feedback rows flagged it, when it \
            was last seen, and recent verbatim quote→fix examples from their \
            own writing. DRILL PROTOCOL when the user wants grammar practice \
            ("luyện ngữ pháp", "drill grammar"): pick the 1-2 most recent \
            patterns (focused practice on few patterns beats covering all), \
            check recent Grammar entries in find_study_sessions to avoid \
            re-drilling what was just practiced, then write 5 NEW short \
            sentences — everyday topics, the user's level, each containing \
            exactly ONE error of the target pattern, never a sentence from \
            their essays or transcripts verbatim. Present ONE at a time and wait for the \
            user's correction; after each answer give the verdict, the \
            corrected sentence, and a ONE-line rule explanation of why. \
            Finish with a tally, then log the drill with log_study_sessions \
            (skill Vocabulary for word-choice patterns, otherwise Grammar; \
            kind PRACTICE; scoreRaw = correct answers, scoreMax = items; \
            sessionNotes naming the patterns drilled, e.g. "drill: articles, \
            SVA 4/5"). If this list is empty, say grammar drills unlock after \
            the first writing or speaking feedback and offer grade_writing or \
            the Speaking tab instead — do not \
            invent weaknesses.""";

    private final WritingService writing;

    WritingTools(WritingService writing) {
        this.writing = writing;
    }

    @Tool(name = "list_writing_feedback", description = LIST)
    @McpTool(name = "list_writing_feedback", description = LIST,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    openWorldHint = false))
    List<WritingFeedbackSummary> listWritingFeedback() {
        return writing.list();
    }

    @Tool(name = "delete_writing_feedback", description = DELETE)
    @McpTool(name = "delete_writing_feedback", description = DELETE,
            annotations = @McpTool.McpAnnotations(destructiveHint = true, openWorldHint = false))
    String deleteWritingFeedback(
            @ToolParam(description = "The writing feedback's UUID")
            @McpToolParam(description = "The writing feedback's UUID",
                    required = true) String feedbackId) {
        UUID id = UUID.fromString(feedbackId);
        WritingFeedbackSummary victim = writing.find(id);
        writing.delete(id);
        return "Deleted writing feedback: " + victim.taskLabel() + " (~" + victim.overallMin()
                + "-" + victim.overallMax() + ")";
    }

    @Tool(name = "grammar_weaknesses", description = WEAKNESSES)
    @McpTool(name = "grammar_weaknesses", description = WEAKNESSES,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    openWorldHint = false))
    List<GrammarWeakness> grammarWeaknesses() {
        return writing.grammarWeaknesses();
    }
}
