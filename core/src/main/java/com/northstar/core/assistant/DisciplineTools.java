package com.northstar.core.assistant;

import com.northstar.core.discipline.DisciplineService;
import com.northstar.core.discipline.DisciplineSummary;
import com.northstar.core.shared.ColorName;
import java.util.List;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Discipline tools — the LDP spine. Mostly read: other tools accept a
 * disciplineName and resolve it, so the agent needs the list to pick valid
 * names, and occasionally creates a new area on request.
 */
@Component
class DisciplineTools implements NorthstarTool {

    private static final String LIST_DISCIPLINES = """
            The user's disciplines — the life areas everything else hangs off (e.g. \
            'English · IELTS', 'Chinese · HSK'). Use to pick a valid disciplineName \
            before creating or moving tasks, events or projects.""";

    private static final String CREATE_DISCIPLINE = """
            Create a new discipline (life area). Rare — only when the user explicitly \
            starts a new area, not for one-off topics (those are tags or folders).""";

    private final DisciplineService disciplines;

    DisciplineTools(DisciplineService disciplines) {
        this.disciplines = disciplines;
    }

    @Tool(name = "list_disciplines", description = LIST_DISCIPLINES)
    @McpTool(name = "list_disciplines", description = LIST_DISCIPLINES,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    openWorldHint = false))
    List<DisciplineSummary> listDisciplines() {
        return disciplines.list();
    }

    @Tool(name = "create_discipline", description = CREATE_DISCIPLINE)
    @McpTool(name = "create_discipline", description = CREATE_DISCIPLINE,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, openWorldHint = false))
    DisciplineSummary createDiscipline(
            @ToolParam(description = "Discipline name, e.g. 'Japanese · JLPT'")
            @McpToolParam(description = "Discipline name, e.g. 'Japanese · JLPT'",
                    required = true) String name,
            @ToolParam(description = "Display color; defaults to GRAY", required = false)
            @McpToolParam(description = "Display color; defaults to GRAY",
                    required = false) ColorName color) {
        return disciplines.create(name, color == null ? ColorName.GRAY : color);
    }
}
