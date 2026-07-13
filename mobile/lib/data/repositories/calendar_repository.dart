import 'package:northstar/data/services/calendar_api.dart';
import 'package:northstar/domain/models/calendar_models.dart';

abstract interface class CalendarRepository {
  Future<List<CalendarAgendaEvent>> agenda({
    required DateTime from,
    required DateTime to,
    required String timezone,
  });
}

class RemoteCalendarRepository implements CalendarRepository {
  const RemoteCalendarRepository(this._api);

  final CalendarDataSource _api;

  @override
  Future<List<CalendarAgendaEvent>> agenda({
    required DateTime from,
    required DateTime to,
    required String timezone,
  }) async {
    final items = await _api.agenda(from: from, to: to, timezone: timezone);
    final events = items.map((item) => item.toDomain()).toList()
      ..sort((a, b) => a.startAt.compareTo(b.startAt));
    return List.unmodifiable(events);
  }
}
