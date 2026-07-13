import 'package:flutter_test/flutter_test.dart';
import 'package:northstar/data/models/today_dtos.dart';
import 'package:northstar/data/repositories/today_repository.dart';
import 'package:northstar/data/services/today_api.dart';
import 'package:northstar/domain/models/today_models.dart';

void main() {
  test(
    'loads daily sources concurrently and selects the next live event',
    () async {
      final api = _TodayDataSource();
      final repository = RemoteTodayRepository(api);
      final now = DateTime.utc(2026, 7, 13, 10);

      final snapshot = await repository.load(
        now: now,
        timezone: 'Asia/Bangkok',
      );

      expect(snapshot.todayTasks.single.id, 'task-today');
      expect(snapshot.upcomingTasks.single.id, 'task-upcoming');
      expect(snapshot.habits.single.id, 'habit-1');
      expect(snapshot.nextEvent?.id, 'event-next');
      expect(api.eventFrom, now);
      expect(api.eventTo, now.add(const Duration(days: 7)));
    },
  );
}

class _TodayDataSource implements TodayDataSource {
  DateTime? eventFrom;
  DateTime? eventTo;

  @override
  Future<List<TodayTaskDto>> listTodayTasks(String timezone) async => [
    _task('task-today'),
  ];

  @override
  Future<List<TodayTaskDto>> listUpcomingTasks(
    String timezone, {
    int days = 7,
  }) async => [_task('task-upcoming')];

  @override
  Future<List<TodayHabitDto>> listTodayHabits(String timezone) async => [
    _habit(),
  ];

  @override
  Future<List<TodayCalendarEventDto>> listEvents({
    required DateTime from,
    required DateTime to,
    required String timezone,
  }) async {
    eventFrom = from;
    eventTo = to;
    return [
      _event(
        'event-ended',
        DateTime.utc(2026, 7, 13, 8),
        DateTime.utc(2026, 7, 13, 9),
      ),
      _event(
        'event-later',
        DateTime.utc(2026, 7, 13, 14),
        DateTime.utc(2026, 7, 13, 15),
      ),
      _event(
        'event-next',
        DateTime.utc(2026, 7, 13, 11),
        DateTime.utc(2026, 7, 13, 12),
      ),
    ];
  }

  @override
  Future<TodayTaskDto> setTaskDone(
    String id,
    bool done,
    String timezone,
  ) async => _task(id, done: done);

  @override
  Future<TodayHabitDto> setHabitCheckIn({
    required String id,
    required String date,
    required TodayHabitCheckIn status,
    required String timezone,
  }) async =>
      _habit(state: status == TodayHabitCheckIn.done ? 'DONE' : 'EXCUSED');

  @override
  Future<TodayHabitDto> clearHabitCheckIn({
    required String id,
    required String date,
    required String timezone,
  }) async => _habit();
}

TodayTaskDto _task(String id, {bool done = false}) => TodayTaskDto(
  id: id,
  title: id,
  status: done ? 'DONE' : 'OPEN',
  createdAt: '2026-07-12T01:00:00Z',
);

TodayHabitDto _habit({String state = 'OPEN'}) => TodayHabitDto(
  id: 'habit-1',
  title: 'Read',
  color: 'INDIGO',
  state: state,
  dueToday: true,
  completedThisWeek: state == 'DONE' ? 3 : 2,
  targetThisWeek: 5,
  consistency30: 80,
  consistency90: 75,
  currentStreak: 3,
  bestStreak: 12,
);

TodayCalendarEventDto _event(String id, DateTime start, DateTime end) {
  return TodayCalendarEventDto(
    id: id,
    title: id,
    startAt: start.toIso8601String(),
    endAt: end.toIso8601String(),
    allDay: false,
    color: 'BLUE',
  );
}
