import 'package:northstar/domain/models/today_models.dart';

class TodayTaskDto {
  const TodayTaskDto({
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

  factory TodayTaskDto.fromJson(Map<String, Object?> json) {
    return TodayTaskDto(
      id: _requiredString(json, 'id'),
      title: _requiredString(json, 'title'),
      status: _enumString(json, 'status'),
      createdAt: _requiredString(json, 'createdAt'),
      notes: _optionalString(json, 'notes'),
      dueDate: _optionalString(json, 'dueDate'),
      dueTime: _optionalString(json, 'dueTime'),
      plannedDate: _optionalString(json, 'plannedDate'),
      completedAt: _optionalString(json, 'completedAt'),
      disciplineId: _optionalString(json, 'disciplineId'),
      projectId: _optionalString(json, 'projectId'),
    );
  }

  final String id;
  final String title;
  final String status;
  final String createdAt;
  final String? notes;
  final String? dueDate;
  final String? dueTime;
  final String? plannedDate;
  final String? completedAt;
  final String? disciplineId;
  final String? projectId;

  TodayTask toDomain() {
    return TodayTask(
      id: id,
      title: title,
      status: switch (status) {
        'OPEN' => TodayTaskStatus.open,
        'DONE' => TodayTaskStatus.done,
        _ => throw FormatException('Unsupported task status: $status'),
      },
      createdAt: DateTime.parse(createdAt),
      notes: notes,
      dueDate: dueDate,
      dueTime: dueTime,
      plannedDate: plannedDate,
      completedAt: completedAt == null ? null : DateTime.parse(completedAt!),
      disciplineId: disciplineId,
      projectId: projectId,
    );
  }
}

class TodayHabitDto {
  const TodayHabitDto({
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

  factory TodayHabitDto.fromJson(Map<String, Object?> json) {
    final habit = _requiredMap(json, 'habit');
    return TodayHabitDto(
      id: _requiredString(habit, 'id'),
      title: _requiredString(habit, 'title'),
      color: _enumString(habit, 'color'),
      state: _enumString(json, 'todayState'),
      dueToday: _requiredBool(json, 'dueToday'),
      completedThisWeek: _requiredInt(json, 'completedThisWeek'),
      targetThisWeek: _requiredInt(json, 'targetThisWeek'),
      consistency30: _requiredInt(json, 'consistency30'),
      consistency90: _requiredInt(json, 'consistency90'),
      currentStreak: _requiredInt(json, 'currentStreak'),
      bestStreak: _requiredInt(json, 'bestStreak'),
      cue: _optionalString(habit, 'cue'),
      notes: _optionalString(habit, 'notes'),
    );
  }

  final String id;
  final String title;
  final String color;
  final String state;
  final bool dueToday;
  final int completedThisWeek;
  final int targetThisWeek;
  final int consistency30;
  final int consistency90;
  final int currentStreak;
  final int bestStreak;
  final String? cue;
  final String? notes;

  TodayHabit toDomain() {
    return TodayHabit(
      id: id,
      title: title,
      color: color,
      state: switch (state) {
        'DONE' => TodayHabitState.done,
        'EXCUSED' => TodayHabitState.excused,
        'OPEN' => TodayHabitState.open,
        'MISSED' => TodayHabitState.missed,
        'PAUSED' => TodayHabitState.paused,
        'NOT_SCHEDULED' => TodayHabitState.notScheduled,
        _ => throw FormatException('Unsupported habit state: $state'),
      },
      dueToday: dueToday,
      completedThisWeek: completedThisWeek,
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

class TodayCalendarEventDto {
  const TodayCalendarEventDto({
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

  factory TodayCalendarEventDto.fromJson(Map<String, Object?> json) {
    return TodayCalendarEventDto(
      id: _requiredString(json, 'id'),
      title: _requiredString(json, 'title'),
      startAt: _requiredString(json, 'startAt'),
      endAt: _requiredString(json, 'endAt'),
      allDay: _requiredBool(json, 'allDay'),
      color: _enumString(json, 'color'),
      notes: _optionalString(json, 'notes'),
      disciplineId: _optionalString(json, 'disciplineId'),
      rrule: _optionalString(json, 'rrule'),
    );
  }

  final String id;
  final String title;
  final String startAt;
  final String endAt;
  final bool allDay;
  final String color;
  final String? notes;
  final String? disciplineId;
  final String? rrule;

  TodayCalendarEvent toDomain() {
    return TodayCalendarEvent(
      id: id,
      title: title,
      startAt: DateTime.parse(startAt),
      endAt: DateTime.parse(endAt),
      allDay: allDay,
      color: color,
      notes: notes,
      disciplineId: disciplineId,
      rrule: rrule,
    );
  }
}

Map<String, Object?> _requiredMap(Map<String, Object?> json, String key) {
  final value = json[key];
  if (value is Map<String, Object?>) {
    return value;
  }
  if (value is Map) {
    return value.map((key, value) => MapEntry(key.toString(), value));
  }
  throw FormatException('Expected object field "$key".');
}

String _requiredString(Map<String, Object?> json, String key) {
  final value = json[key];
  if (value is String && value.isNotEmpty) {
    return value;
  }
  throw FormatException('Expected string field "$key".');
}

String _enumString(Map<String, Object?> json, String key) {
  return _requiredString(json, key).toUpperCase();
}

String? _optionalString(Map<String, Object?> json, String key) {
  final value = json[key];
  if (value == null) {
    return null;
  }
  if (value is String) {
    return value;
  }
  throw FormatException('Expected optional string field "$key".');
}

bool _requiredBool(Map<String, Object?> json, String key) {
  final value = json[key];
  if (value is bool) {
    return value;
  }
  throw FormatException('Expected boolean field "$key".');
}

int _requiredInt(Map<String, Object?> json, String key) {
  final value = json[key];
  if (value is int) {
    return value;
  }
  if (value is num) {
    return value.toInt();
  }
  throw FormatException('Expected integer field "$key".');
}
