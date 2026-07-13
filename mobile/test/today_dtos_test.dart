import 'package:flutter_test/flutter_test.dart';
import 'package:northstar/data/models/today_dtos.dart';
import 'package:northstar/domain/models/today_models.dart';

void main() {
  test('maps current task, habit, and calendar response contracts', () {
    final task = TodayTaskDto.fromJson({
      'id': 'task-1',
      'title': 'Review vocabulary',
      'status': 'OPEN',
      'dueDate': '2026-07-13',
      'dueTime': '09:00:00',
      'createdAt': '2026-07-12T01:00:00Z',
    }).toDomain();
    final habit = TodayHabitDto.fromJson({
      'habit': {
        'id': 'habit-1',
        'title': 'Read 20 minutes',
        'color': 'INDIGO',
        'cue': 'After dinner',
      },
      'todayState': 'OPEN',
      'dueToday': true,
      'completedThisWeek': 2,
      'targetThisWeek': 5,
      'consistency30': 80,
      'consistency90': 75,
      'currentStreak': 3,
      'bestStreak': 12,
    }).toDomain();
    final event = TodayCalendarEventDto.fromJson({
      'id': 'event-1',
      'title': 'IELTS class',
      'startAt': '2026-07-13T12:00:00Z',
      'endAt': '2026-07-13T13:00:00Z',
      'allDay': false,
      'color': 'BLUE',
    }).toDomain();

    expect(task.status, TodayTaskStatus.open);
    expect(task.dueTime, '09:00:00');
    expect(habit.state, TodayHabitState.open);
    expect(habit.completedThisWeek, 2);
    expect(event.startAt, DateTime.utc(2026, 7, 13, 12));
  });

  test('rejects unknown server enum values explicitly', () {
    expect(
      () => TodayTaskDto.fromJson({
        'id': 'task-1',
        'title': 'Unknown',
        'status': 'BLOCKED',
        'createdAt': '2026-07-12T01:00:00Z',
      }).toDomain(),
      throwsFormatException,
    );
  });
}
