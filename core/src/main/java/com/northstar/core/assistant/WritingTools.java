package com.northstar.core.assistant;

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
}
