class CalendarAgendaEvent {
  const CalendarAgendaEvent({
    required this.id,
    required this.title,
    required this.startAt,
    required this.endAt,
    required this.allDay,
    required this.color,
    this.notes,
    this.disciplineId,
    this.rrule,
  });

  final String id;
  final String title;
  final DateTime startAt;
  final DateTime endAt;
  final bool allDay;
  final String color;
  final String? notes;
  final String? disciplineId;
  final String? rrule;
}
