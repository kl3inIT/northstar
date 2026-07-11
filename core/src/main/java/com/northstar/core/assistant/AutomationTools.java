package com.northstar.core.assistant;

import com.northstar.core.automation.AutomationDefinitionSummary;
import com.northstar.core.automation.AutomationRunSummary;
import com.northstar.core.automation.AutomationService;
import com.northstar.core.automation.AutomationTrigger;
import com.northstar.core.automation.AutomationTriggerKind;
import com.northstar.core.automation.AutomationTypeDescriptor;
import com.northstar.core.brief.MorningBriefHandler;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/** Typed automation tools shared by the in-app Assistant and the MCP server. */
@Component
class AutomationTools implements NorthstarTool {

    private static final String LIST_TYPES = """
            List the automation workflow types currently registered by the server,
            including their stable type id, description, config version, and default
            workflow config. Call this before creating an automation; never invent a
            type that is not returned here.""";

    private static final String LIST_AUTOMATIONS = """
            List active automation definitions with ids, type, schedule, workflow
            config, enabled state, projection-sync state, and optimistic-lock version.
            Call this before updating, pausing, running, or deleting an automation so
            the exact target and current version are known.""";

    private static final String LIST_RUNS = """
            List recent runs for one automation, newest first, including manual versus
            scheduled origin, status, attempt, output reference, and error details.
            Use to answer whether a workflow ran or why it failed.""";

    private static final String SAVE_MORNING_BRIEF = """
            Create or fully replace one Morning Brief automation. Pass id="" and
            version=0 to create. Before updating, call list_automations and pass its
            current id/version plus EVERY field at its intended final value; stale
            versions are rejected. localTime is HH:mm in the supplied IANA timezone,
            and daysOfWeek uses MONDAY..SUNDAY. exact queries replace topic-generated
            queries when non-empty. Only write on explicit user intent.""";

    private static final String RUN_NOW = """
            Queue one immediate manual run by automation UUID. This creates a new run
            every time, so call only when the user explicitly asks to run it now. The
            worker executes it asynchronously; use list_automation_runs for status.""";

    private static final String SET_ENABLED = """
            Pause or enable one automation without changing its schedule or workflow
            config. Immediately before calling, use list_automations and pass the
            current id/version; stale versions are rejected. Only write on explicit
            user intent.""";

    private static final String DELETE_AUTOMATION = """
            Delete one automation definition by UUID and current version after explicit
            user intent. Its future schedule is removed and its run history is retained.
            Resolve exactly one target with list_automations immediately before this
            call; a stale version is rejected.""";

    private final AutomationService automations;

    AutomationTools(AutomationService automations) {
        this.automations = automations;
    }

    @Tool(name = "list_automation_types", description = LIST_TYPES)
    @McpTool(name = "list_automation_types", description = LIST_TYPES,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    openWorldHint = false))
    List<AutomationTypeDescriptor> listAutomationTypes() {
        return automations.supportedTypes();
    }

    @Tool(name = "list_automations", description = LIST_AUTOMATIONS)
    @McpTool(name = "list_automations", description = LIST_AUTOMATIONS,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    openWorldHint = false))
    List<AutomationDefinitionSummary> listAutomations() {
        return automations.list();
    }

    @Tool(name = "list_automation_runs", description = LIST_RUNS)
    @McpTool(name = "list_automation_runs", description = LIST_RUNS,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    openWorldHint = false))
    List<AutomationRunSummary> listAutomationRuns(
            @ToolParam(description = "Automation UUID from list_automations")
            @McpToolParam(description = "Automation UUID from list_automations",
                    required = true) String automationId,
            @ToolParam(description = "Maximum runs to return, 1-100; defaults to 10",
                    required = false)
            @McpToolParam(description = "Maximum runs to return, 1-100; defaults to 10",
                    required = false) Integer limit) {
        return automations.history(UUID.fromString(automationId), limit == null ? 10 : limit);
    }

    @Tool(name = "save_morning_brief_automation", description = SAVE_MORNING_BRIEF)
    @McpTool(name = "save_morning_brief_automation", description = SAVE_MORNING_BRIEF,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, openWorldHint = false))
    AutomationDefinitionSummary saveMorningBriefAutomation(
            @ToolParam(description = "Existing automation UUID, or empty string to create",
                    required = false)
            @McpToolParam(description = "Existing automation UUID, or empty string to create",
                    required = false) String id,
            @ToolParam(description = "Current version from list_automations; 0 only when creating")
            @McpToolParam(description = "Current version from list_automations; 0 only when creating",
                    required = true) long version,
            @ToolParam(description = "Automation display name")
            @McpToolParam(description = "Automation display name", required = true) String name,
            @ToolParam(description = "Whether scheduled runs are enabled")
            @McpToolParam(description = "Whether scheduled runs are enabled", required = true)
            boolean enabled,
            @ToolParam(description = "Local run time as HH:mm")
            @McpToolParam(description = "Local run time as HH:mm", required = true) String localTime,
            @ToolParam(description = "Run weekdays: MONDAY through SUNDAY")
            @McpToolParam(description = "Run weekdays: MONDAY through SUNDAY", required = true)
            List<DayOfWeek> daysOfWeek,
            @ToolParam(description = "IANA timezone, e.g. Asia/Bangkok")
            @McpToolParam(description = "IANA timezone, e.g. Asia/Bangkok", required = true)
            String timezone,
            @ToolParam(description = "Minutes after a missed schedule that catch-up is allowed, 0-1440")
            @McpToolParam(description = "Minutes after a missed schedule that catch-up is allowed, 0-1440",
                    required = true) int catchUpWindowMinutes,
            @ToolParam(description = "Brief language code, usually vi or en")
            @McpToolParam(description = "Brief language code, usually vi or en", required = true)
            String language,
            @ToolParam(description = "Research lookback in hours, 1-168")
            @McpToolParam(description = "Research lookback in hours, 1-168", required = true)
            int lookbackHours,
            @ToolParam(description = "Maximum sources in the brief, 1-10")
            @McpToolParam(description = "Maximum sources in the brief, 1-10", required = true)
            int maxItems,
            @ToolParam(description = "Topics used to generate search queries; up to 12")
            @McpToolParam(description = "Topics used to generate search queries; up to 12",
                    required = true) List<String> topics,
            @ToolParam(description = "Exact search queries; when non-empty they replace topics, up to 6")
            @McpToolParam(description = "Exact search queries; when non-empty they replace topics, up to 6",
                    required = true) List<String> queries,
            @ToolParam(description = "Host names to exclude, without scheme; up to 20")
            @McpToolParam(description = "Host names to exclude, without scheme; up to 20",
                    required = true) List<String> blockedDomains,
            @ToolParam(description = "Save each result as a reviewable Staging note")
            @McpToolParam(description = "Save each result as a reviewable Staging note", required = true)
            boolean saveAsNote) {
        AutomationTrigger trigger = trigger(localTime, daysOfWeek, timezone, catchUpWindowMinutes);
        Map<String, Object> config = morningBriefConfig(language, lookbackHours, maxItems,
                topics, queries, blockedDomains, saveAsNote);
        if (id == null || id.isBlank()) {
            if (version != 0) throw new IllegalArgumentException("version must be 0 when creating");
            return automations.create(MorningBriefHandler.TYPE, name, enabled, trigger, config);
        }
        UUID automationId = UUID.fromString(id);
        AutomationDefinitionSummary current = automations.find(automationId);
        requireMorningBrief(current);
        return automations.update(automationId, name, enabled, trigger, config, version);
    }

    @Tool(name = "run_automation_now", description = RUN_NOW)
    @McpTool(name = "run_automation_now", description = RUN_NOW,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, openWorldHint = false))
    AutomationRunSummary runAutomationNow(
            @ToolParam(description = "Automation UUID from list_automations")
            @McpToolParam(description = "Automation UUID from list_automations",
                    required = true) String automationId) {
        return automations.requestRunNow(UUID.fromString(automationId));
    }

    @Tool(name = "set_automation_enabled", description = SET_ENABLED)
    @McpTool(name = "set_automation_enabled", description = SET_ENABLED,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, openWorldHint = false))
    AutomationDefinitionSummary setAutomationEnabled(
            @ToolParam(description = "Automation UUID from list_automations")
            @McpToolParam(description = "Automation UUID from list_automations",
                    required = true) String automationId,
            @ToolParam(description = "Current version from list_automations")
            @McpToolParam(description = "Current version from list_automations",
                    required = true) long version,
            @ToolParam(description = "true to enable scheduled runs, false to pause")
            @McpToolParam(description = "true to enable scheduled runs, false to pause",
                    required = true) boolean enabled) {
        UUID id = UUID.fromString(automationId);
        AutomationDefinitionSummary current = automations.find(id);
        return automations.update(id, current.name(), enabled, current.trigger(),
                current.workflowConfig(), version);
    }

    @Tool(name = "delete_automation", description = DELETE_AUTOMATION)
    @McpTool(name = "delete_automation", description = DELETE_AUTOMATION,
            annotations = @McpTool.McpAnnotations(destructiveHint = true, openWorldHint = false))
    String deleteAutomation(
            @ToolParam(description = "Automation UUID from list_automations")
            @McpToolParam(description = "Automation UUID from list_automations",
                    required = true) String automationId,
            @ToolParam(description = "Current version from list_automations")
            @McpToolParam(description = "Current version from list_automations",
                    required = true) long version) {
        UUID id = UUID.fromString(automationId);
        AutomationDefinitionSummary current = automations.find(id);
        automations.delete(id, version);
        return "Deleted automation: " + current.name();
    }

    static AutomationTrigger trigger(String localTime, List<DayOfWeek> daysOfWeek,
            String timezone, int catchUpWindowMinutes) {
        if (daysOfWeek == null || daysOfWeek.isEmpty()) {
            throw new IllegalArgumentException("daysOfWeek must contain at least one day");
        }
        LocalTime time;
        try {
            time = LocalTime.parse(localTime == null ? "" : localTime.strip());
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("localTime must be HH:mm, got '" + localTime + "'",
                    exception);
        }
        return new AutomationTrigger(AutomationTriggerKind.DAILY, time,
                EnumSet.copyOf(daysOfWeek), timezone, catchUpWindowMinutes);
    }

    static Map<String, Object> morningBriefConfig(String language, int lookbackHours,
            int maxItems, List<String> topics, List<String> queries, List<String> blockedDomains,
            boolean saveAsNote) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("language", language == null ? "" : language.strip());
        config.put("lookbackHours", lookbackHours);
        config.put("maxItems", maxItems);
        config.put("topics", clean(topics, false));
        config.put("queries", clean(queries, false));
        config.put("blockedDomains", clean(blockedDomains, true));
        config.put("saveAsNote", saveAsNote);
        return Map.copyOf(config);
    }

    static void requireMorningBrief(AutomationDefinitionSummary definition) {
        if (!MorningBriefHandler.TYPE.equals(definition.type())) {
            throw new IllegalArgumentException("Automation " + definition.id() + " is type "
                    + definition.type() + ", not " + MorningBriefHandler.TYPE);
        }
    }

    private static List<String> clean(List<String> values, boolean lowercase) {
        if (values == null) return List.of();
        return values.stream()
                .filter(java.util.Objects::nonNull)
                .map(String::strip)
                .filter(value -> !value.isBlank())
                .map(value -> lowercase ? value.toLowerCase(Locale.ROOT) : value)
                .distinct()
                .toList();
    }
}
