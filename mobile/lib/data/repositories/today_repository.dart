import 'package:northstar/data/services/today_api.dart';
import 'package:northstar/domain/models/today_models.dart';

abstract interface class TodayRepository {
  Future<TodaySnapshot> load({required DateTime now, required String timezone});

  Future<TodayTask> setTaskDone({
    required String id,
    required bool done,
    required String timezone,
  });

  Future<TodayHabit> setHabitCheckIn({
    required String id,
    required String date,
    required TodayHabitCheckIn status,
    required String timezone,
  });

  Future<TodayHabit> clearHabitCheckIn({
    required String id,
    required String date,
    required String timezone,
  });
}

class RemoteTodayRepository implements TodayRepository {
  const RemoteTodayRepository(this._api);

  final TodayDataSource _api;

  @override
  Future<TodaySnapshot> load({
    required DateTime now,
    required String timezone,
  }) async {
    late final List<TodayTask> todayTasks;
    late final List<TodayTask> upcomingTasks;
    late final List<TodayHabit> habits;
    late final List<TodayCalendarEvent> events;
    await Future.wait<void>([
      _api.listTodayTasks(timezone).then((items) {
        todayTasks = items
            .map((item) => item.toDomain())
            .toList(growable: false);
      }),
      _api.listUpcomingTasks(timezone).then((items) {
        upcomingTasks = items
            .map((item) => item.toDomain())
            .toList(growable: false);
      }),
      _api.listTodayHabits(timezone).then((items) {
        habits = items.map((item) => item.toDomain()).toList(growable: false);
      }),
      _api
          .listEvents(
            from: now,
            to: now.add(const Duration(days: 7)),
            timezone: timezone,
          )
          .then((items) {
            events = items
                .map((item) => item.toDomain())
                .toList(growable: false);
          }),
    ]);
    final upcomingEvents =
        events.where((event) => event.endAt.isAfter(now)).toList()
          ..sort((a, b) => a.startAt.compareTo(b.startAt));
    return TodaySnapshot(
      todayTasks: List.unmodifiable(todayTasks),
      upcomingTasks: List.unmodifiable(upcomingTasks),
      habits: List.unmodifiable(habits),
      nextEvent: upcomingEvents.firstOrNull,
    );
  }

  @override
  Future<TodayTask> setTaskDone({
    required String id,
    required bool done,
    required String timezone,
  }) async {
    return (await _api.setTaskDone(id, done, timezone)).toDomain();
  }

  @override
  Future<TodayHabit> setHabitCheckIn({
    required String id,
    required String date,
    required TodayHabitCheckIn status,
    required String timezone,
  }) async {
    return (await _api.setHabitCheckIn(
      id: id,
      date: date,
      status: status,
      timezone: timezone,
    )).toDomain();
  }

  @override
  Future<TodayHabit> clearHabitCheckIn({
    required String id,
    required String date,
    required String timezone,
  }) async {
    return (await _api.clearHabitCheckIn(
      id: id,
      date: date,
      timezone: timezone,
    )).toDomain();
  }
}
