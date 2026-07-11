package com.northstar.core.assistant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import com.northstar.core.automation.AutomationDefinitionSummary;
import com.northstar.core.automation.AutomationTrigger;
import com.northstar.core.automation.AutomationTriggerKind;
import com.northstar.core.brief.MorningBriefHandler;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AutomationToolsTests {

    @Test
    void normalizesTypedMorningBriefScheduleAndConfig() {
        AutomationTrigger trigger = AutomationTools.trigger("07:30",
                List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), "Asia/Bangkok", 240);
        Map<String, Object> config = AutomationTools.morningBriefConfig("vi", 24, 5,
                List.of(" AI agents ", "AI agents"), List.of(), List.of(" Example.COM "), true);

        assertThat(trigger.kind()).isEqualTo(AutomationTriggerKind.DAILY);
        assertThat(trigger.localTime()).isEqualTo(LocalTime.of(7, 30));
        assertThat(trigger.daysOfWeek()).containsExactlyInAnyOrder(
                DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY);
        assertThat(trigger.timezone()).isEqualTo("Asia/Bangkok");
        assertThat(trigger.catchUpWindowMinutes()).isEqualTo(240);
        assertThat(config.get("topics")).isEqualTo(List.of("AI agents"));
        assertThat(config.get("blockedDomains")).isEqualTo(List.of("example.com"));
        assertThat(config.get("maxItems")).isEqualTo(5);
        assertThat(config.get("saveAsNote")).isEqualTo(true);
    }

    @Test
    void refusesToOverwriteAnotherAutomationTypeWithMorningBriefConfig() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> AutomationTools.requireMorningBrief(
                definition(id, "telegram-digest.v1", true, 3)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not morning-brief.v1");
    }

    @Test
    void acceptsMorningBriefType() {
        AutomationTools.requireMorningBrief(
                definition(UUID.randomUUID(), MorningBriefHandler.TYPE, true, 7));
    }

    @Test
    void rejectsInvalidLocalTimeBeforeWriting() {
        assertThatThrownBy(() -> AutomationTools.trigger("7am",
                List.of(DayOfWeek.MONDAY), "Asia/Bangkok", 240))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("localTime must be HH:mm");
    }

    private static AutomationDefinitionSummary definition(UUID id, String type,
            boolean enabled, long version) {
        Instant now = Instant.parse("2026-07-11T06:00:00Z");
        AutomationTrigger trigger = new AutomationTrigger(AutomationTriggerKind.DAILY,
                LocalTime.of(7, 0), Set.of(DayOfWeek.MONDAY), "Asia/Bangkok", 240);
        return new AutomationDefinitionSummary(id, type, "Daily AI", enabled, trigger,
                Map.of("topics", List.of("AI")), 1, 2, 2, true, null, now, now, version);
    }
}
