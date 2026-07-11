package com.northstar.core.assistant;

import com.northstar.core.study.SpeakingFeedbackSummary;
import com.northstar.core.study.SpeakingService;
import java.util.List;
import java.util.UUID;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
class SpeakingTools implements NorthstarTool {

    private static final String LIST = """
            Every speaking-practice attempt, newest first: question, transcript,
            measured 0-100 delivery scores and provider, unofficial AI content scores,
            verbatim quote-to-fix errors, and summary. Never translate delivery scores
            into an IELTS band. Use this history to discuss trends and retrieve the UUID
            required by delete_speaking_feedback.
            """;

    private static final String DELETE = """
            Permanently delete one speaking-practice feedback row by UUID. Audio was
            never stored. Resolve the exact attempt with list_speaking_feedback first
            and only call on explicit user intent.
            """;

    private final SpeakingService speaking;

    SpeakingTools(SpeakingService speaking) {
        this.speaking = speaking;
    }

    @Tool(name = "list_speaking_feedback", description = LIST)
    @McpTool(name = "list_speaking_feedback", description = LIST,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    openWorldHint = false))
    List<SpeakingFeedbackSummary> listSpeakingFeedback() {
        return speaking.list();
    }

    @Tool(name = "delete_speaking_feedback", description = DELETE)
    @McpTool(name = "delete_speaking_feedback", description = DELETE,
            annotations = @McpTool.McpAnnotations(destructiveHint = true, openWorldHint = false))
    String deleteSpeakingFeedback(
            @ToolParam(description = "The speaking feedback UUID")
            @McpToolParam(description = "The speaking feedback UUID", required = true) String feedbackId) {
        UUID id = UUID.fromString(feedbackId);
        SpeakingFeedbackSummary victim = speaking.find(id);
        speaking.delete(id);
        return "Deleted speaking feedback: " + victim.question();
    }
}
