import 'package:northstar/domain/models/calendar_models.dart';

class CalendarAgendaEventDto {
  const CalendarAgendaEventDto(this.json);

  final Map<String, Object?> json;

  CalendarAgendaEvent toDomain() {
    return CalendarAgendaEvent(
      id: _requiredString('id'),
      title: _requiredString('title'),
      startAt: DateTime.parse(_requiredString('startAt')),
      endAt: DateTime.parse(_requiredString('endAt')),
      allDay: _requiredBool('allDay'),
      color: _requiredString('color'),
      notes: _optionalString('notes'),
      disciplineId: _optionalString('disciplineId'),
      rrule: _optionalString('rrule'),
    );
  }

  String _requiredString(String key) {
    final value = json[key];
    if (value is String && value.isNotEmpty) return value;
    throw FormatException('Expected string field "$key".');
  }

  String? _optionalString(String key) {
    final value = json[key];
    if (value == null) return null;
    if (value is String) return value;
    throw FormatException('Expected optional string field "$key".');
  }

  bool _requiredBool(String key) {
    final value = json[key];
    if (value is bool) return value;
    throw FormatException('Expected boolean field "$key".');
  }
}
