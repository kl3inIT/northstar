import 'package:northstar/data/services/habits_api.dart';
import 'package:northstar/domain/models/today_models.dart';

abstract interface class HabitsRepository {
  Future<List<TodayHabit>> list(String timezone);

  Future<TodayHabit> setCheckIn({
    required String id,
    required String date,
    required TodayHabitCheckIn status,
    required String timezone,
  });

  Future<TodayHabit> clearCheckIn({
    required String id,
    required String date,
    required String timezone,
  });
}

class RemoteHabitsRepository implements HabitsRepository {
  const RemoteHabitsRepository(this._api);

  final HabitsDataSource _api;

  @override
  Future<List<TodayHabit>> list(String timezone) async {
    final items = await _api.list(timezone);
    return List.unmodifiable(items.map((item) => item.toDomain()));
  }

  @override
  Future<TodayHabit> setCheckIn({
    required String id,
    required String date,
    required TodayHabitCheckIn status,
    required String timezone,
  }) async {
    return (await _api.setCheckIn(
      id: id,
      date: date,
      status: status,
      timezone: timezone,
    )).toDomain();
  }

  @override
  Future<TodayHabit> clearCheckIn({
    required String id,
    required String date,
    required String timezone,
  }) async {
    return (await _api.clearCheckIn(
      id: id,
      date: date,
      timezone: timezone,
    )).toDomain();
  }
}
