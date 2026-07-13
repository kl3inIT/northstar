import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:northstar/data/services/authenticated_api_client.dart';
import 'package:northstar/data/services/today_api.dart';
import 'package:northstar/domain/models/today_models.dart';

void main() {
  test('loads daily endpoints with Bearer auth and an IANA timezone', () async {
    final captured = <http.BaseRequest>[];
    final client = _HandlerClient((request) async {
      captured.add(request);
      if (request.url.path == '/api/tasks/today') {
        return _response(jsonEncode([_taskJson()]));
      }
      if (request.url.path == '/api/tasks/upcoming') {
        return _response('[]');
      }
      if (request.url.path == '/api/habits/today') {
        return _response(jsonEncode([_habitJson()]));
      }
      return _response(jsonEncode([_eventJson()]));
    });
    final api = _api(client);

    await api.listTodayTasks('Asia/Bangkok');
    await api.listUpcomingTasks('Asia/Bangkok', days: 5);
    await api.listTodayHabits('Asia/Bangkok');
    await api.listEvents(
      from: DateTime.utc(2026, 7, 13),
      to: DateTime.utc(2026, 7, 20),
      timezone: 'Asia/Bangkok',
    );

    expect(captured, hasLength(4));
    expect(
      captured.every(
        (request) => request.headers['authorization'] == 'Bearer access-token',
      ),
      isTrue,
    );
    expect(
      captured.every(
        (request) => request.headers['X-Timezone'] == 'Asia/Bangkok',
      ),
      isTrue,
    );
    expect(captured[1].url.queryParameters['days'], '5');
    expect(captured[3].url.queryParameters['from'], contains('2026-07-13'));
  });

  test('sends typed task and habit mutations to current endpoints', () async {
    final captured = <http.Request>[];
    final client = _HandlerClient((request) async {
      captured.add(request as http.Request);
      if (request.url.path.startsWith('/api/tasks/')) {
        return _response(jsonEncode({..._taskJson(), 'status': 'DONE'}));
      }
      return _response(jsonEncode({..._habitJson(), 'todayState': 'DONE'}));
    });
    final api = _api(client);

    final task = await api.setTaskDone('task-1', true, 'Asia/Bangkok');
    final habit = await api.setHabitCheckIn(
      id: 'habit-1',
      date: '2026-07-13',
      status: TodayHabitCheckIn.done,
      timezone: 'Asia/Bangkok',
    );
    await api.clearHabitCheckIn(
      id: 'habit-1',
      date: '2026-07-13',
      timezone: 'Asia/Bangkok',
    );

    expect(task.status, 'DONE');
    expect(habit.state, 'DONE');
    expect(captured.map((request) => request.method), [
      'PATCH',
      'PUT',
      'DELETE',
    ]);
    expect(captured[0].url.path, '/api/tasks/task-1/status');
    expect(jsonDecode(captured[0].body), {'done': true});
    expect(captured[1].url.path, '/api/habits/habit-1/check-ins/2026-07-13');
    expect(jsonDecode(captured[1].body), {'status': 'DONE'});
  });
}

TodayApi _api(http.Client client) {
  return TodayApi(
    authenticatedClient: AuthenticatedApiClient(
      client: client,
      accessToken: () => 'access-token',
      refreshAccessToken: () async => null,
      onUnauthorized: () {},
    ),
    baseUrl: Uri.parse('https://northstar.example'),
  );
}

Map<String, Object?> _taskJson() => {
  'id': 'task-1',
  'title': 'Review vocabulary',
  'status': 'OPEN',
  'createdAt': '2026-07-12T01:00:00Z',
};

Map<String, Object?> _habitJson() => {
  'habit': {'id': 'habit-1', 'title': 'Read', 'color': 'INDIGO'},
  'todayState': 'OPEN',
  'dueToday': true,
  'completedThisWeek': 2,
  'targetThisWeek': 5,
  'consistency30': 80,
  'consistency90': 75,
  'currentStreak': 3,
  'bestStreak': 12,
};

Map<String, Object?> _eventJson() => {
  'id': 'event-1',
  'title': 'IELTS class',
  'startAt': '2026-07-13T12:00:00Z',
  'endAt': '2026-07-13T13:00:00Z',
  'allDay': false,
  'color': 'BLUE',
};

http.StreamedResponse _response(String body) {
  return http.StreamedResponse(Stream.value(utf8.encode(body)), 200);
}

class _HandlerClient extends http.BaseClient {
  _HandlerClient(this._handler);

  final Future<http.StreamedResponse> Function(http.BaseRequest request)
  _handler;

  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) {
    return _handler(request);
  }
}
