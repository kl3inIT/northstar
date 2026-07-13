enum TodayTaskStatus { open, done }

class TodayTask {
  const TodayTask({
    required this.id,
    required this.title,
    required this.status,
    required this.createdAt,
    this.notes,
    this.dueDate,
    this.dueTime,
    this.plannedDate,
    this.completedAt,
    this.disciplineId,
    this.projectId,
  });

  final String id;
  final String title;
  final TodayTaskStatus status;
  final DateTime createdAt;
  final String? notes;
  final String? dueDate;
  final String? dueTime;
  final String? plannedDate;
  final DateTime? completedAt;
  final String? disciplineId;
  final String? projectId;

  bool get isDone => status == TodayTaskStatus.done;

  TodayTask withDone(bool done) {
    return TodayTask(
      id: id,
      title: title,
      status: done ? TodayTaskStatus.done : TodayTaskStatus.open,
      createdAt: createdAt,
      notes: notes,
      dueDate: dueDate,
      dueTime: dueTime,
      plannedDate: plannedDate,
      completedAt: done ? completedAt : null,
      disciplineId: disciplineId,
      projectId: projectId,
    );
  }
}

enum TodayHabitState { done, excused, open, missed, paused, notScheduled }

enum TodayHabitCheckIn { done, excused }

class TodayHabit {
  const TodayHabit({
    required this.id,
    required this.title,
    required this.color,
    required this.state,
    required this.dueToday,
    required this.completedThisWeek,
    required this.targetThisWeek,
    required this.consistency30,
    required this.consistency90,
    required this.currentStreak,
    required this.bestStreak,
    this.cue,
    this.notes,
  });

  final String id;
  final String title;
  final String color;
  final TodayHabitState state;
  final bool dueToday;
  final int completedThisWeek;
  final int targetThisWeek;
  final int consistency30;
  final int consistency90;
  final int currentStreak;
  final int bestStreak;
  final String? cue;
  final String? notes;

  TodayHabit withOptimisticState(TodayHabitState next) {
    final wasDone = state == TodayHabitState.done;
    final willBeDone = next == TodayHabitState.done;
    final nextCompleted = switch ((wasDone, willBeDone)) {
      (false, true) => completedThisWeek + 1,
      (true, false) => completedThisWeek > 0 ? completedThisWeek - 1 : 0,
      _ => completedThisWeek,
    };
    return TodayHabit(
      id: id,
      title: title,
      color: color,
      state: next,
      dueToday: dueToday,
      completedThisWeek: nextCompleted,
      targetThisWeek: targetThisWeek,
      consistency30: consistency30,
      consistency90: consistency90,
      currentStreak: currentStreak,
      bestStreak: bestStreak,
      cue: cue,
      notes: notes,
    );
  }
}

class TodayCalendarEvent {
  const TodayCalendarEvent({
    required this.id,
    required this.title,
    required this.startAt,
    required this.endAt,
    required this.allDay,
    required this.color,
    this.notes,
    this.disciplineId,
    this.rrule,
  });

  final String id;
  final String title;
  final DateTime startAt;
  final DateTime endAt;
  final bool allDay;
  final String color;
  final String? notes;
  final String? disciplineId;
  final String? rrule;
}

class TodaySnapshot {
  const TodaySnapshot({
    required this.todayTasks,
    required this.upcomingTasks,
    required this.habits,
    required this.nextEvent,
  });

  final List<TodayTask> todayTasks;
  final List<TodayTask> upcomingTasks;
  final List<TodayHabit> habits;
  final TodayCalendarEvent? nextEvent;
}
