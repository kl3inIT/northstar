import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:northstar/data/services/authenticated_api_client.dart';
import 'package:northstar/data/services/authenticated_json_client.dart';
import 'package:northstar/data/services/calendar_api.dart';
import 'package:northstar/data/services/habits_api.dart';
import 'package:northstar/data/services/note_detail_api.dart';
import 'package:northstar/domain/models/today_models.dart';

void main() {
  test('loads the Calendar agenda with its UTC range and timezone', () async {
    late http.Request captured;
    final api = CalendarApi(
      _jsonClient(
        _HandlerClient((request) async {
          captured = request as http.Request;
          return _response(jsonEncode([_eventJson()]));
        }),
      ),
    );

    final events = await api.agenda(
      from: DateTime.parse('2026-07-13T00:00:00+07:00'),
      to: DateTime.parse('2026-07-27T00:00:00+07:00'),
      timezone: 'Asia/Bangkok',
    );

    expect(captured.method, 'GET');
    expect(captured.url.path, '/api/calendar/events');
    expect(captured.url.queryParameters, {
      'from': '2026-07-12T17:00:00.000Z',
      'to': '2026-07-26T17:00:00.000Z',
    });
    expect(captured.headers['X-Timezone'], 'Asia/Bangkok');
    expect(captured.headers['authorization'], 'Bearer access-token');
    expect(events.single.toDomain().rrule, 'FREQ=WEEKLY');
  });

  test('uses current Habits endpoints for list, check-in, and clear', () async {
    final captured = <http.Request>[];
    final api = HabitsApi(
      _jsonClient(
        _HandlerClient((request) async {
          captured.add(request as http.Request);
          if (request.method == 'GET') {
            return _response(jsonEncode([_habitJson()]));
          }
          final state = request.method == 'DELETE' ? 'OPEN' : 'DONE';
          return _response(jsonEncode(_habitJson(state: state)));
        }),
      ),
    );

    await api.list('Asia/Bangkok');
    final saved = await api.setCheckIn(
      id: 'habit-1',
      date: '2026-07-13',
      status: TodayHabitCheckIn.done,
      timezone: 'Asia/Bangkok',
    );
    final cleared = await api.clearCheckIn(
      id: 'habit-1',
      date: '2026-07-13',
      timezone: 'Asia/Bangkok',
    );

    expect(captured.map((request) => request.method), ['GET', 'PUT', 'DELETE']);
    expect(captured[0].url.path, '/api/habits/today');
    expect(captured[1].url.path, '/api/habits/habit-1/check-ins/2026-07-13');
    expect(jsonDecode(captured[1].body), {'status': 'DONE'});
    expect(
      captured.every(
        (request) => request.headers['X-Timezone'] == 'Asia/Bangkok',
      ),
      isTrue,
    );
    expect(saved.toDomain().state, TodayHabitState.done);
    expect(cleared.toDomain().state, TodayHabitState.open);
  });

  test('loads an encoded Note detail as a typed DTO', () async {
    late http.Request captured;
    final api = NoteDetailApi(
      _jsonClient(
        _HandlerClient((request) async {
          captured = request as http.Request;
          return _response(jsonEncode(_noteJson()));
        }),
      ),
    );

    final note = (await api.get('mobile behavior')).toDomain();

    expect(captured.method, 'GET');
    expect(captured.url.toString(), contains('/api/notes/mobile%20behavior'));
    expect(note.title, 'Mobile behavior');
    expect(note.tags, ['mobile', 'research']);
  });
}

AuthenticatedJsonClient _jsonClient(http.Client client) {
  return AuthenticatedJsonClient(
    authenticatedClient: AuthenticatedApiClient(
      client: client,
      accessToken: () => 'access-token',
      refreshAccessToken: () async => null,
      onUnauthorized: () {},
    ),
    baseUrl: Uri.parse('https://northstar.example'),
  );
}

Map<String, Object?> _eventJson() => {
  'id': 'event-1',
  'title': 'IELTS class',
  'startAt': '2026-07-13T12:00:00Z',
  'endAt': '2026-07-13T13:00:00Z',
  'allDay': false,
  'color': 'BLUE',
  'notes': 'Room 2',
  'rrule': 'FREQ=WEEKLY',
};

Map<String, Object?> _habitJson({String state = 'OPEN'}) => {
  'habit': {
    'id': 'habit-1',
    'title': 'Read',
    'color': 'INDIGO',
    'cue': 'After dinner',
  },
  'todayState': state,
  'dueToday': true,
  'completedThisWeek': state == 'DONE' ? 3 : 2,
  'targetThisWeek': 5,
  'consistency30': 80,
  'consistency90': 75,
  'currentStreak': 3,
  'bestStreak': 12,
};

Map<String, Object?> _noteJson() => {
  'id': 'note-1',
  'title': 'Mobile behavior',
  'slug': 'mobile-behavior',
  'folderPath': 'research',
  'contentMarkdown': '# Mobile behavior',
  'tags': ['mobile', 'research'],
  'status': 'ACTIVE',
  'updatedAt': '2026-07-13T01:00:00Z',
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
