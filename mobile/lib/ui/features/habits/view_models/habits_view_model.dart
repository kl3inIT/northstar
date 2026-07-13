import 'package:flutter/foundation.dart';
import 'package:northstar/data/repositories/habits_repository.dart';
import 'package:northstar/data/services/device_timezone.dart';
import 'package:northstar/data/services/interaction_telemetry.dart';
import 'package:northstar/domain/models/today_models.dart';

enum HabitsPhase { idle, loading, ready, error }

class HabitsViewModel extends ChangeNotifier {
  factory HabitsViewModel({
    required HabitsRepository repository,
    required DeviceTimezoneProvider timezoneProvider,
    InteractionTelemetry telemetry = const NoopInteractionTelemetry(),
    DateTime Function()? clock,
  }) {
    return HabitsViewModel._(
      repository,
      timezoneProvider,
      telemetry,
      clock ?? DateTime.now,
    );
  }

  HabitsViewModel._(
    this._repository,
    this._timezoneProvider,
    this._telemetry,
    this._clock,
  );

  final HabitsRepository _repository;
  final DeviceTimezoneProvider _timezoneProvider;
  final InteractionTelemetry _telemetry;
  final DateTime Function() _clock;

  HabitsPhase _phase = HabitsPhase.idle;
  List<TodayHabit> _habits = const [];
  String? _errorMessage;
  String? _timezone;
  final Set<String> _pendingIds = {};

  HabitsPhase get phase => _phase;
  List<TodayHabit> get habits => _habits;
  String? get errorMessage => _errorMessage;
  bool isPending(String id) => _pendingIds.contains(id);

  Future<void> initialize() async {
    if (_phase != HabitsPhase.idle) return;
    await load();
  }

  Future<void> load() async {
    _phase = HabitsPhase.loading;
    _errorMessage = null;
    notifyListeners();
    try {
      final timezone = _timezone ??= await _timezoneProvider
          .currentIdentifier();
      _habits = await _repository.list(timezone);
      _phase = HabitsPhase.ready;
    } on Object catch (error) {
      _phase = HabitsPhase.error;
      _errorMessage = _messageFor(error);
    }
    notifyListeners();
  }

  Future<void> setCheckIn(String id, TodayHabitCheckIn? checkIn) async {
    if (_pendingIds.contains(id)) return;
    final original = _find(id);
    if (original == null) return;
    final optimistic = switch (checkIn) {
      TodayHabitCheckIn.done => TodayHabitState.done,
      TodayHabitCheckIn.excused => TodayHabitState.excused,
      null => TodayHabitState.open,
    };
    _pendingIds.add(id);
    _errorMessage = null;
    _habits = _replace(id, (habit) => habit.withOptimisticState(optimistic));
    final optimisticHabit = _find(id);
    notifyListeners();
    try {
      final timezone = _timezone ??= await _timezoneProvider
          .currentIdentifier();
      final date = _dateString(_clock());
      final saved = checkIn == null
          ? await _repository.clearCheckIn(
              id: id,
              date: date,
              timezone: timezone,
            )
          : await _repository.setCheckIn(
              id: id,
              date: date,
              status: checkIn,
              timezone: timezone,
            );
      _habits = _replace(id, (_) => saved);
      _telemetry.record(
        checkIn == null ? 'habit.cleared' : 'habit.${checkIn.name}',
      );
    } on Object catch (error) {
      _habits = _restore(id, expected: optimisticHabit, original: original);
      _errorMessage = _messageFor(error);
    } finally {
      _pendingIds.remove(id);
      notifyListeners();
    }
  }

  List<TodayHabit> _replace(String id, TodayHabit Function(TodayHabit) update) {
    return List.unmodifiable(
      _habits.map((habit) => habit.id == id ? update(habit) : habit),
    );
  }

  TodayHabit? _find(String id) {
    for (final habit in _habits) {
      if (habit.id == id) return habit;
    }
    return null;
  }

  List<TodayHabit> _restore(
    String id, {
    required TodayHabit? expected,
    required TodayHabit original,
  }) {
    if (expected == null) return _habits;
    return List.unmodifiable(
      _habits.map(
        (habit) =>
            habit.id == id && identical(habit, expected) ? original : habit,
      ),
    );
  }

  String _dateString(DateTime value) {
    String two(int part) => part.toString().padLeft(2, '0');
    return '${value.year}-${two(value.month)}-${two(value.day)}';
  }

  String _messageFor(Object error) {
    final text = error.toString().replaceFirst(
      RegExp(r'^\w+Exception:\s*'),
      '',
    );
    return text.isEmpty ? 'Habits are unavailable.' : text;
  }
}
