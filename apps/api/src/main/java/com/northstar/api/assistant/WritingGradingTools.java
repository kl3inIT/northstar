package com.northstar.api.assistant;

import com.northstar.core.assistant.NorthstarTool;
import com.northstar.core.study.WritingFeedbackSummary;
import com.northstar.core.study.WritingGrader;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * In-app only: intentionally has no @McpTool annotation — grading needs the
 * LLM this app has and the mcp app deliberately does not (the WebResearchTools
 * precedent). History reads stay dual-transport in core's WritingTools.
 */
@Component
class WritingGradingTools implements NorthstarTool {

    private static final String GRADE = """
            Grade one essay the user wrote against the IELTS writing rubric \
            and save the feedback to their writing history. Pass the essay \
            EXACTLY as the user gave it — never fix, trim, or complete it \
            first, that would grade your text instead of theirs. Returns an \
            UNOFFICIAL band estimate range (e.g. ~6.0-6.5), per-criterion \
            bands with quoted justification, and the top recurring error \
            patterns; the grading also compares against errors from previous \
            essays. When you relay the result: present the range as an \
            unofficial estimate (never a predicted official score), lead with \
            the most band-moving improvement, and mention error patterns that \
            persist from earlier essays. Grading an already-graded essay \
            again creates a second history entry — fine when the user revised \
            it, wasteful otherwise. For "band của tôi đang thế nào" use \
            list_writing_feedback instead; this tool is only for grading NEW \
            essay text.""";

    private final WritingGrader grader;

    WritingGradingTools(WritingGrader grader) {
        this.grader = grader;
    }

    @Tool(name = "grade_writing", description = GRADE)
    WritingFeedbackSummary gradeWriting(
            @ToolParam(description = "What the essay responds to, e.g. \"IELTS Task 2 — remote "
                    + "work\"; pass \"\" when the user did not say") String taskLabel,
            @ToolParam(description = "The user's complete essay text, verbatim") String essayMarkdown) {
        return grader.grade(taskLabel, essayMarkdown);
    }
}
