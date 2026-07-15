package com.northstar.core.habit;

import com.northstar.core.shared.ColorName;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public API for repeated behaviour definitions and local-date evidence. */
@Service
public class HabitService {

    private static final int MAX_ANALYTICS_DAYS = 366;

    private final HabitRepository habits;
    private final HabitScheduleRepository schedules;
    private final HabitCheckInRepository checkIns;
    private final HabitPauseRepository pauses;

    HabitService(HabitRepository habits, HabitScheduleRepository schedules,
            HabitCheckInRepository checkIns, HabitPauseRepository pauses) {
        this.habits = habits;
        this.schedules = schedules;
        this.checkIns = checkIns;
        this.pauses = pauses;
    }

    @Transactional
    public HabitSummary create(String title, String cue, String notes, ColorName color,
            ZoneId zone, HabitFrequencyType frequencyType, Set<DayOfWeek> days,
            int weeklyTarget, LocalDate effectiveFrom) {
        validate(title, cue, color, zone, frequencyType, days, weeklyTarget);
        // A habit's first schedule must cover the present: a future effectiveFrom
        // would leave today with no applicable schedule, which then breaks the
        // whole today()/list() collection reads for every habit. (update() may
        // legitimately add a future schedule version because the current one
        // still covers today, so this guard belongs only here.)
        if (effectiveFrom != null && effectiveFrom.isAfter(LocalDate.now(zone))) {
            throw new IllegalArgumentException("effectiveFrom cannot be in the future");
        }
        Habit habit = habits.save(new Habit(UUID.randomUUID(), cleanRequired(title), clean(cue),
                clean(notes), color, zone.getId()));
        HabitSchedule schedule = schedules.save(new HabitSchedule(UUID.randomUUID(), habit.getId(),
                effectiveFrom, frequencyType, days, weeklyTarget));
        return summary(habit, schedule, false);
    }

    @Transactional
    public HabitSummary update(UUID id, String title, String cue, String notes, ColorName color,
            ZoneId zone, HabitFrequencyType frequencyType, Set<DayOfWeek> days,
            int weeklyTarget, LocalDate effectiveFrom) {
        validate(title, cue, color, zone, frequencyType, days, weeklyTarget);
        Habit habit = require(id);
        habit.edit(cleanRequired(title), clean(cue), clean(notes), color, zone.getId());
        List<HabitSchedule> history = scheduleHistory(id);
        HabitSchedule current = scheduleAt(history, effectiveFrom);
        if (current == null) {
            throw new IllegalArgumentException("effectiveFrom predates this habit");
        }
        boolean futureExists = history.stream().anyMatch(item -> item.effectiveFrom().isAfter(effectiveFrom));
        if (futureExists) {
            throw new IllegalArgumentException("cannot edit before a future schedule version");
        }
        if (current.effectiveFrom().equals(effectiveFrom)) {
            current.revise(frequencyType, days, weeklyTarget);
        } else {
            current.closeBefore(effectiveFrom);
            current = schedules.save(new HabitSchedule(UUID.randomUUID(), id, effectiveFrom,
                    frequencyType, days, weeklyTarget));
        }
        return summary(habit, current, isPaused(id, effectiveFrom));
    }

    @Transactional(readOnly = true)
    public HabitSummary find(UUID id, LocalDate on) {
        Habit habit = require(id);
        HabitSchedule schedule = requireSchedule(id, on);
        return summary(habit, schedule, isPaused(id, on));
    }

    @Transactional(readOnly = true)
    public List<HabitSummary> list(boolean includeArchived, LocalDate on) {
        return habits.findAllByOrderByCreatedAtAsc().stream()
                .filter(habit -> includeArchived || habit.status() == HabitStatus.ACTIVE)
                .map(habit -> summary(habit, requireSchedule(habit.getId(), on),
                        isPaused(habit.getId(), on)))
                .toList();
    }

    @Transactional
    public HabitTodaySummary checkIn(UUID id, LocalDate date, HabitCheckInStatus status,
            ZoneId callerZone) {
        Habit habit = requireActive(id);
        LocalDate today = LocalDate.now(callerZone);
        if (date.isAfter(today)) {
            throw new IllegalArgumentException("cannot check in a future date");
        }
        if (isPaused(id, date)) {
            throw new IllegalArgumentException("cannot check in while the habit is paused");
        }
        checkIns.findByHabitIdAndLocalDate(id, date)
                .ifPresentOrElse(row -> row.setStatus(status),
                        () -> checkIns.save(new HabitCheckIn(UUID.randomUUID(), id, date, status)));
        return todaySummary(habit, today);
    }

    @Transactional
    public HabitTodaySummary clearCheckIn(UUID id, LocalDate date, ZoneId callerZone) {
        Habit habit = require(id);
        checkIns.findByHabitIdAndLocalDate(id, date).ifPresent(checkIns::delete);
        return todaySummary(habit, LocalDate.now(callerZone));
    }

    @Transactional
    public HabitSummary pause(UUID id, LocalDate startDate) {
        Habit habit = requireActive(id);
        openPause(id).ifPresent(existing -> {
            throw new IllegalArgumentException("habit is already paused");
        });
        pauses.save(new HabitPause(UUID.randomUUID(), id, startDate));
        return summary(habit, requireSchedule(id, startDate), true);
    }

    @Transactional
    public HabitSummary resume(UUID id, LocalDate resumeDate) {
        Habit habit = requireActive(id);
        HabitPause pause = openPause(id)
                .orElseThrow(() -> new IllegalArgumentException("habit is not paused"));
        if (resumeDate.isBefore(pause.startDate())) {
            throw new IllegalArgumentException("resume date must not precede pause date");
        }
        if (resumeDate.equals(pause.startDate())) {
            pauses.delete(pause);
        } else {
            pause.close(resumeDate.minusDays(1));
        }
        return summary(habit, requireSchedule(id, resumeDate), false);
    }

    @Transactional
    public HabitSummary setArchived(UUID id, boolean archived, LocalDate on) {
        Habit habit = require(id);
        habit.setArchived(archived);
        if (archived) {
            openPause(id).ifPresent(pause -> {
                if (on.equals(pause.startDate())) {
                    pauses.delete(pause);
                } else if (on.isAfter(pause.startDate())) {
                    pause.close(on.minusDays(1));
                }
            });
        }
        return summary(habit, requireSchedule(id, on), false);
    }

    @Transactional(readOnly = true)
    public List<HabitTodaySummary> today(ZoneId zone) {
        LocalDate today = LocalDate.now(zone);
        return habits.findAllByOrderByCreatedAtAsc().stream()
                .filter(habit -> habit.status() == HabitStatus.ACTIVE)
                .map(habit -> todaySummary(habit, today))
                .toList();
    }

    @Transactional(readOnly = true)
    public HabitInsights insights(LocalDate from, LocalDate to, boolean includeArchived) {
        if (to.isBefore(from) || from.plusDays(MAX_ANALYTICS_DAYS - 1L).isBefore(to)) {
            throw new IllegalArgumentException("insight range must contain 1-366 days");
        }
        List<HabitInsightSummary> rows = habits.findAllByOrderByCreatedAtAsc().stream()
                .filter(habit -> includeArchived || habit.status() == HabitStatus.ACTIVE)
                .map(habit -> insight(habit, from, to))
                .toList();
        return new HabitInsights(from, to, rows);
    }

    @Transactional(readOnly = true)
    public HabitInsightSummary insight(UUID id, LocalDate from, LocalDate to) {
        return insight(require(id), from, to);
    }

    private HabitTodaySummary todaySummary(Habit habit, LocalDate today) {
        LocalDate from = today.minusDays(89);
        Calculation ninety = calculate(habit, from, today);
        Calculation thirty = calculate(habit, today.minusDays(29), today);
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Calculation week = calculate(habit, weekStart, today);
        HabitSchedule schedule = requireSchedule(habit.getId(), today);
        List<HabitDaySummary> recent = ninety.days().stream()
                .filter(day -> !day.date().isBefore(today.minusDays(6))).toList();
        HabitDayState todayState = recent.getLast().state();
        if (schedule.frequencyType() == HabitFrequencyType.WEEKLY_TARGET
                && !isPaused(habit.getId(), today)
                && week.completed() < schedule.weeklyTarget()
                && todayState == HabitDayState.NOT_SCHEDULED) {
            todayState = HabitDayState.OPEN;
            recent = new ArrayList<>(recent);
            recent.set(recent.size() - 1, new HabitDaySummary(today, todayState));
            recent = List.copyOf(recent);
        }
        boolean due = todayState == HabitDayState.OPEN || todayState == HabitDayState.MISSED;
        int target = schedule.frequencyType() == HabitFrequencyType.WEEKLY_TARGET
                ? schedule.weeklyTarget() : week.expected();
        return new HabitTodaySummary(summary(habit, schedule, isPaused(habit.getId(), today)),
                todayState, due, week.completed(), target, thirty.rate(), ninety.rate(),
                ninety.currentStreak(), ninety.bestStreak(), recent);
    }

    private HabitInsightSummary insight(Habit habit, LocalDate from, LocalDate to) {
        Calculation value = calculate(habit, from, to);
        LocalDate summaryDate = to;
        HabitSchedule schedule = scheduleAt(scheduleHistory(habit.getId()), summaryDate);
        if (schedule == null) {
            schedule = scheduleHistory(habit.getId()).getFirst();
        }
        return new HabitInsightSummary(summary(habit, schedule, isPaused(habit.getId(), to)),
                value.expected(), value.completed(), value.excused(), value.rate(),
                value.currentStreak(), value.bestStreak(), value.days());
    }

    private Calculation calculate(Habit habit, LocalDate from, LocalDate to) {
        UUID id = habit.getId();
        List<HabitSchedule> history = scheduleHistory(id);
        List<HabitPause> pauseHistory = pauses.findByHabitIdOrderByStartDateAsc(id);
        Map<LocalDate, HabitCheckInStatus> evidence = new HashMap<>();
        checkIns.findByHabitIdAndLocalDateBetweenOrderByLocalDateAsc(id, from, to)
                .forEach(row -> evidence.put(row.localDate(), row.status()));

        List<HabitDaySummary> days = new ArrayList<>();
        int expected = 0;
        int completed = 0;
        int excused = 0;
        Map<LocalDate, WeeklyBucket> weekly = new LinkedHashMap<>();

        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            LocalDate currentDay = day;
            HabitSchedule schedule = scheduleAt(history, currentDay);
            boolean paused = pauseHistory.stream().anyMatch(item -> item.includes(currentDay));
            HabitCheckInStatus check = evidence.get(currentDay);
            HabitDayState state;
            if (paused) {
                state = HabitDayState.PAUSED;
            } else if (schedule == null) {
                state = HabitDayState.NOT_SCHEDULED;
            } else if (check == HabitCheckInStatus.DONE) {
                state = HabitDayState.DONE;
            } else if (check == HabitCheckInStatus.EXCUSED) {
                state = HabitDayState.EXCUSED;
                excused++;
            } else if (schedule.frequencyType() == HabitFrequencyType.ON_DAYS
                    && schedule.includes(day.getDayOfWeek())) {
                state = currentDay.equals(to) ? HabitDayState.OPEN : HabitDayState.MISSED;
            } else {
                state = HabitDayState.NOT_SCHEDULED;
            }
            days.add(new HabitDaySummary(currentDay, state));

            if (schedule == null || paused) {
                continue;
            }
            if (schedule.frequencyType() == HabitFrequencyType.ON_DAYS) {
                if (schedule.includes(day.getDayOfWeek()) && check != HabitCheckInStatus.EXCUSED) {
                    expected++;
                    if (check == HabitCheckInStatus.DONE) completed++;
                }
            } else {
                LocalDate monday = currentDay.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                WeeklyBucket bucket = weekly.computeIfAbsent(monday,
                        ignored -> new WeeklyBucket(schedule.weeklyTarget()));
                if (check != HabitCheckInStatus.EXCUSED) bucket.eligible++;
                if (check == HabitCheckInStatus.DONE) bucket.completed++;
            }
        }
        for (WeeklyBucket bucket : weekly.values()) {
            int opportunity = Math.min(bucket.target, bucket.eligible);
            expected += opportunity;
            completed += Math.min(bucket.completed, opportunity);
        }

        Streaks streaks = streaks(days, history);
        int rate = expected == 0 ? 0 : (int) Math.round(completed * 100.0 / expected);
        return new Calculation(expected, completed, excused, rate,
                streaks.current(), streaks.best(), List.copyOf(days));
    }

    private static Streaks streaks(List<HabitDaySummary> days, List<HabitSchedule> schedules) {
        int run = 0;
        int best = 0;
        for (HabitDaySummary day : days) {
            HabitSchedule schedule = scheduleAt(schedules, day.date());
            if (schedule == null || schedule.frequencyType() == HabitFrequencyType.WEEKLY_TARGET
                    || day.state() == HabitDayState.PAUSED || day.state() == HabitDayState.EXCUSED
                    || day.state() == HabitDayState.NOT_SCHEDULED) {
                continue;
            }
            if (day.state() == HabitDayState.DONE) {
                run++;
                best = Math.max(best, run);
            } else if (day.state() != HabitDayState.OPEN) {
                run = 0;
            }
        }
        return new Streaks(run, best);
    }

    private HabitSummary summary(Habit habit, HabitSchedule schedule, boolean paused) {
        return new HabitSummary(habit.getId(), habit.title(), habit.cue(), habit.notes(),
                habit.color(), habit.status(), habit.timezone(),
                new HabitScheduleSummary(schedule.frequencyType(), schedule.days(),
                        schedule.weeklyTarget(), schedule.effectiveFrom()),
                paused, habit.getCreatedAt(), habit.getVersion());
    }

    private Habit require(UUID id) {
        return habits.findById(id).orElseThrow(() -> new HabitNotFoundException(id));
    }

    private Habit requireActive(UUID id) {
        Habit habit = require(id);
        if (habit.status() == HabitStatus.ARCHIVED) {
            throw new IllegalArgumentException("archived habits cannot be changed");
        }
        return habit;
    }

    private HabitSchedule requireSchedule(UUID id, LocalDate date) {
        HabitSchedule schedule = scheduleAt(scheduleHistory(id), date);
        if (schedule == null) throw new IllegalArgumentException("habit has no schedule on " + date);
        return schedule;
    }

    private List<HabitSchedule> scheduleHistory(UUID id) {
        return schedules.findByHabitIdOrderByEffectiveFromAsc(id);
    }

    private boolean isPaused(UUID id, LocalDate date) {
        return pauses.findByHabitIdOrderByStartDateAsc(id).stream().anyMatch(item -> item.includes(date));
    }

    private java.util.Optional<HabitPause> openPause(UUID id) {
        return pauses.findByHabitIdOrderByStartDateAsc(id).stream()
                .filter(item -> item.endDate() == null).findFirst();
    }

    private static HabitSchedule scheduleAt(List<HabitSchedule> schedules, LocalDate date) {
        return schedules.stream().filter(item -> item.appliesOn(date))
                .max(Comparator.comparing(HabitSchedule::effectiveFrom)).orElse(null);
    }

    private static void validate(String title, String cue, ColorName color, ZoneId zone,
            HabitFrequencyType type, Set<DayOfWeek> days, int weeklyTarget) {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("title is required");
        if (title.strip().length() > 120) throw new IllegalArgumentException("title must be at most 120 characters");
        if (cue != null && cue.strip().length() > 255) throw new IllegalArgumentException("cue must be at most 255 characters");
        if (color == null || zone == null || type == null) throw new IllegalArgumentException("color, timezone and frequency are required");
        if (type == HabitFrequencyType.ON_DAYS && (days == null || days.isEmpty())) {
            throw new IllegalArgumentException("ON_DAYS requires at least one weekday");
        }
        if (type == HabitFrequencyType.WEEKLY_TARGET && (weeklyTarget < 1 || weeklyTarget > 7)) {
            throw new IllegalArgumentException("weekly target must be between 1 and 7");
        }
    }

    private static String cleanRequired(String value) { return value.strip(); }
    private static String clean(String value) { return value == null || value.isBlank() ? null : value.strip(); }

    private static final class WeeklyBucket {
        private final int target;
        private int eligible;
        private int completed;
        private WeeklyBucket(int target) { this.target = target; }
    }

    private record Streaks(int current, int best) { }
    private record Calculation(int expected, int completed, int excused, int rate,
            int currentStreak, int bestStreak, List<HabitDaySummary> days) { }
}
