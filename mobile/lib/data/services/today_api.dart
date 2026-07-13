import 'package:northstar/data/models/today_dtos.dart';
import 'package:northstar/data/services/authenticated_json_client.dart';
import 'package:northstar/domain/models/today_models.dart';

abstract interface class TodayDataSource {
  Future<List<TodayTaskDto>> listTodayTasks(String timezone);

  Future<List<TodayTaskDto>> listUpcomingTasks(String timezone, {int days = 7});

  Future<TodayTaskDto> setTaskDone(String id, bool done, String timezone);

  Future<List<TodayHabitDto>> listTodayHabits(String timezone);

  Future<TodayHabitDto> setHabitCheckIn({
    required String id,
    required String date,
    required TodayHabitCheckIn status,
    required String timezone,
  });

  Future<TodayHabitDto> clearHabitCheckIn({
    required String id,
    required String date,
    required String timezone,
  });

  Future<List<TodayCalendarEventDto>> listEvents({
    required DateTime from,
    required DateTime to,
    required String timezone,
  });
}

class TodayApi implements TodayDataSource {
  const TodayApi(this._client);

  final AuthenticatedJsonClient _client;

  @override
  Future<List<TodayTaskDto>> listTodayTasks(String timezone) async {
    final json = await _client.getList('/api/tasks/today', timezone: timezone);
    return json.map(TodayTaskDto.fromJson).toList(growable: false);
  }

  @override
  Future<List<TodayTaskDto>> listUpcomingTasks(
    String timezone, {
    int days = 7,
  }) async {
    final json = await _client.getList(
      '/api/tasks/upcoming',
      timezone: timezone,
      query: {'days': '$days'},
    );
    return json.map(TodayTaskDto.fromJson).toList(growable: false);
  }

  @override
  Future<TodayTaskDto> setTaskDone(
    String id,
    bool done,
    String timezone,
  ) async {
    final json = await _client.patchObject(
      '/api/tasks/${Uri.encodeComponent(id)}/status',
      timezone: timezone,
      body: {'done': done},
    );
    return TodayTaskDto.fromJson(json);
  }

  @override
  Future<List<TodayHabitDto>> listTodayHabits(String timezone) async {
    final json = await _client.getList('/api/habits/today', timezone: timezone);
    return json.map(TodayHabitDto.fromJson).toList(growable: false);
  }

  @override
  Future<TodayHabitDto> setHabitCheckIn({
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
  Future<TodayHabitDto> clearHabitCheckIn({
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

  @override
  Future<List<TodayCalendarEventDto>> listEvents({
    required DateTime from,
    required DateTime to,
    required String timezone,
  }) async {
    final json = await _client.getList(
      '/api/calendar/events',
      timezone: timezone,
      query: {
        'from': from.toUtc().toIso8601String(),
        'to': to.toUtc().toIso8601String(),
      },
    );
    return json.map(TodayCalendarEventDto.fromJson).toList(growable: false);
  }
}
