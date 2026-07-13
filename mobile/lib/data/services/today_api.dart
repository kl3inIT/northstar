import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:northstar/data/models/today_dtos.dart';
import 'package:northstar/data/services/authenticated_api_client.dart';
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
  factory TodayApi({
    required AuthenticatedApiClient authenticatedClient,
    required Uri baseUrl,
  }) {
    return TodayApi._(authenticatedClient, baseUrl);
  }

  const TodayApi._(this._authenticated, this._baseUrl);

  final AuthenticatedApiClient _authenticated;
  final Uri _baseUrl;

  @override
  Future<List<TodayTaskDto>> listTodayTasks(String timezone) async {
    final json = await _getList('/api/tasks/today', timezone: timezone);
    return json.map(TodayTaskDto.fromJson).toList(growable: false);
  }

  @override
  Future<List<TodayTaskDto>> listUpcomingTasks(
    String timezone, {
    int days = 7,
  }) async {
    final json = await _getList(
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
    final json = await _sendObject(
      'PATCH',
      '/api/tasks/${Uri.encodeComponent(id)}/status',
      timezone: timezone,
      body: {'done': done},
    );
    return TodayTaskDto.fromJson(json);
  }

  @override
  Future<List<TodayHabitDto>> listTodayHabits(String timezone) async {
    final json = await _getList('/api/habits/today', timezone: timezone);
    return json.map(TodayHabitDto.fromJson).toList(growable: false);
  }

  @override
  Future<TodayHabitDto> setHabitCheckIn({
    required String id,
    required String date,
    required TodayHabitCheckIn status,
    required String timezone,
  }) async {
    final json = await _sendObject(
      'PUT',
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
    final json = await _sendObject(
      'DELETE',
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
    final json = await _getList(
      '/api/calendar/events',
      timezone: timezone,
      query: {
        'from': from.toUtc().toIso8601String(),
        'to': to.toUtc().toIso8601String(),
      },
    );
    return json.map(TodayCalendarEventDto.fromJson).toList(growable: false);
  }

  Future<List<Map<String, Object?>>> _getList(
    String path, {
    required String timezone,
    Map<String, String>? query,
  }) async {
    final response = await _authenticated.send(
      (token) => _request('GET', path, token, timezone: timezone, query: query),
    );
    final value = await _readJson(response);
    if (value is! List) {
      throw const FormatException('Expected a JSON array.');
    }
    return value
        .map((item) {
          if (item is Map<String, Object?>) {
            return item;
          }
          if (item is Map) {
            return item.map((key, value) => MapEntry(key.toString(), value));
          }
          throw const FormatException('Expected a JSON object in the array.');
        })
        .toList(growable: false);
  }

  Future<Map<String, Object?>> _sendObject(
    String method,
    String path, {
    required String timezone,
    Map<String, Object?>? body,
  }) async {
    final response = await _authenticated.send(
      (token) => _request(method, path, token, timezone: timezone, body: body),
    );
    final value = await _readJson(response);
    if (value is Map<String, Object?>) {
      return value;
    }
    if (value is Map) {
      return value.map((key, value) => MapEntry(key.toString(), value));
    }
    throw const FormatException('Expected a JSON object.');
  }

  http.BaseRequest _request(
    String method,
    String path,
    String token, {
    required String timezone,
    Map<String, String>? query,
    Map<String, Object?>? body,
  }) {
    final resolved = _baseUrl.resolve(path);
    final uri = query == null
        ? resolved
        : resolved.replace(queryParameters: query);
    final request = http.Request(method, uri)
      ..headers['authorization'] = 'Bearer $token'
      ..headers['accept'] = 'application/json'
      ..headers['X-Timezone'] = timezone;
    if (body != null) {
      request.headers['content-type'] = 'application/json';
      request.body = jsonEncode(body);
    }
    return request;
  }

  Future<Object?> _readJson(http.StreamedResponse response) async {
    final body = await utf8.decoder.bind(response.stream).join();
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw TodayApiException(response.statusCode, _problemMessage(body));
    }
    if (body.trim().isEmpty) {
      throw const FormatException('Expected a JSON response body.');
    }
    return jsonDecode(body);
  }

  String _problemMessage(String body) {
    try {
      final value = jsonDecode(body);
      if (value is Map) {
        for (final key in ['detail', 'message', 'title']) {
          final message = value[key];
          if (message is String && message.trim().isNotEmpty) {
            return message;
          }
        }
      }
    } on FormatException {
      // Fall through to a stable user-facing message.
    }
    return 'Northstar could not complete the request.';
  }
}

class TodayApiException implements Exception {
  const TodayApiException(this.statusCode, this.message);

  final int statusCode;
  final String message;

  @override
  String toString() => message;
}
