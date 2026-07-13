import 'package:northstar/data/models/today_dtos.dart';
import 'package:northstar/data/services/authenticated_json_client.dart';
import 'package:northstar/domain/models/today_models.dart';

abstract interface class HabitsDataSource {
  Future<List<TodayHabitDto>> list(String timezone);

  Future<TodayHabitDto> setCheckIn({
    required String id,
    required String date,
    required TodayHabitCheckIn status,
    required String timezone,
  });

  Future<TodayHabitDto> clearCheckIn({
    required String id,
    required String date,
    required String timezone,
  });
}

class HabitsApi implements HabitsDataSource {
  const HabitsApi(this._client);

  final AuthenticatedJsonClient _client;

  @override
  Future<List<TodayHabitDto>> list(String timezone) async {
    final json = await _client.getList('/api/habits/today', timezone: timezone);
    return json.map(TodayHabitDto.fromJson).toList(growable: false);
  }

  @override
  Future<TodayHabitDto> setCheckIn({
    required String id,
    required String date,
    required TodayHabitCheckIn status,
    required String timezone,
  }) async {
    final json = await _client.putObject(
      '/api/habits/${Uri.encodeComponent(id)}/check-ins/'
      '${Uri.encodeComponent(date)}',
      timezone: timezone,
      body: {'status': status.name.toUpperCase()},
    );
    return TodayHabitDto.fromJson(json);
  }

  @override
  Future<TodayHabitDto> clearCheckIn({
    required String id,
    required String date,
    required String timezone,
  }) async {
    final json = await _client.deleteObject(
      '/api/habits/${Uri.encodeComponent(id)}/check-ins/'
      '${Uri.encodeComponent(date)}',
      timezone: timezone,
    );
    return TodayHabitDto.fromJson(json);
  }
}
