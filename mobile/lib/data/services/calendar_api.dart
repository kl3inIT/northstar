import 'package:northstar/data/models/calendar_dtos.dart';
import 'package:northstar/data/services/authenticated_json_client.dart';

abstract interface class CalendarDataSource {
  Future<List<CalendarAgendaEventDto>> agenda({
    required DateTime from,
    required DateTime to,
    required String timezone,
  });
}

class CalendarApi implements CalendarDataSource {
  const CalendarApi(this._client);

  final AuthenticatedJsonClient _client;

  @override
  Future<List<CalendarAgendaEventDto>> agenda({
    required DateTime from,
    required DateTime to,
    required String timezone,
  }) async {
    final json = await _client.getList(
      '/api/calendar/events',
      query: {
        'from': from.toUtc().toIso8601String(),
        'to': to.toUtc().toIso8601String(),
      },
      timezone: timezone,
    );
    return json.map(CalendarAgendaEventDto.new).toList(growable: false);
  }
}
