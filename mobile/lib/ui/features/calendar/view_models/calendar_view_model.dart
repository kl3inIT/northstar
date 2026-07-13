import 'package:flutter/foundation.dart';
import 'package:northstar/data/repositories/calendar_repository.dart';
import 'package:northstar/data/services/device_timezone.dart';
import 'package:northstar/domain/models/calendar_models.dart';

enum CalendarPhase { idle, loading, ready, error }

class CalendarViewModel extends ChangeNotifier {
  factory CalendarViewModel({
    required CalendarRepository repository,
    required DeviceTimezoneProvider timezoneProvider,
    DateTime Function()? clock,
  }) {
    final now = (clock ?? DateTime.now)();
    return CalendarViewModel._(
      repository,
      timezoneProvider,
      clock ?? DateTime.now,
      DateTime(now.year, now.month, now.day),
    );
  }

  CalendarViewModel._(
    this._repository,
    this._timezoneProvider,
    this._clock,
    this._anchor,
  );

  static const rangeDays = 14;

  final CalendarRepository _repository;
  final DeviceTimezoneProvider _timezoneProvider;
  final DateTime Function() _clock;

  CalendarPhase _phase = CalendarPhase.idle;
  DateTime _anchor;
  List<CalendarAgendaEvent> _events = const [];
  String? _errorMessage;
  String? _timezone;

  CalendarPhase get phase => _phase;
  DateTime get anchor => _anchor;
  DateTime get through => _anchor.add(const Duration(days: rangeDays));
  List<CalendarAgendaEvent> get events => _events;
  String? get errorMessage => _errorMessage;

  Future<void> initialize() async {
    if (_phase != CalendarPhase.idle) return;
    await load();
  }

  Future<void> load() async {
    _phase = CalendarPhase.loading;
    _errorMessage = null;
    notifyListeners();
    try {
      final timezone = _timezone ??= await _timezoneProvider
          .currentIdentifier();
      _events = await _repository.agenda(
        from: _anchor,
        to: through,
        timezone: timezone,
      );
      _phase = CalendarPhase.ready;
    } on Object catch (error) {
      _phase = CalendarPhase.error;
      _errorMessage = _messageFor(error);
    }
    notifyListeners();
  }

  Future<void> previousRange() async {
    _anchor = _anchor.subtract(const Duration(days: rangeDays));
    await load();
  }

  Future<void> nextRange() async {
    _anchor = _anchor.add(const Duration(days: rangeDays));
    await load();
  }

  Future<void> goToToday() async {
    final now = _clock();
    _anchor = DateTime(now.year, now.month, now.day);
    await load();
  }

  String _messageFor(Object error) {
    final text = error.toString().replaceFirst(
      RegExp(r'^\w+Exception:\s*'),
      '',
    );
    return text.isEmpty ? 'Calendar is unavailable.' : text;
  }
}
