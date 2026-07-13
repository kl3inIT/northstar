import 'package:flutter/foundation.dart';
import 'package:northstar/data/repositories/today_repository.dart';
import 'package:northstar/data/services/device_timezone.dart';
import 'package:northstar/data/services/interaction_telemetry.dart';
import 'package:northstar/domain/models/today_models.dart';

enum TodayLoadPhase { idle, loading, ready, error }

class TodayViewModel extends ChangeNotifier {
  factory TodayViewModel({
    required TodayRepository repository,
    required DeviceTimezoneProvider timezoneProvider,
    InteractionTelemetry telemetry = const NoopInteractionTelemetry(),
    DateTime Function()? clock,
  }) {
    return TodayViewModel._(
      repository,
      timezoneProvider,
      telemetry,
      clock ?? DateTime.now,
    );
  }

  TodayViewModel._(
    this._repository,
    this._timezoneProvider,
    this._telemetry,
    this._clock,
  );

  final TodayRepository _repository;
  final DeviceTimezoneProvider _timezoneProvider;
  final InteractionTelemetry _telemetry;
  final DateTime Function() _clock;

  TodayLoadPhase _phase = TodayLoadPhase.idle;
  List<TodayTask> _todayTasks = const [];
  List<TodayTask> _upcomingTasks = const [];
  List<TodayHabit> _habits = const [];
  TodayCalendarEvent? _nextEvent;
  String? _loadError;
  String? _actionError;
  String? _timezone;
  bool _refreshing = false;
  Future<void> Function()? _retryAction;
  final Set<String> _pendingTaskIds = {};
  final Set<String> _pendingHabitIds = {};

  TodayLoadPhase get phase => _phase;
  List<TodayTask> get todayTasks => _todayTasks;
  List<TodayTask> get upcomingTasks => _upcomingTasks;
  List<TodayHabit> get habits => _habits;
  TodayCalendarEvent? get nextEvent => _nextEvent;
  String? get loadError => _loadError;
  String? get actionError => _actionError;
  bool get isRefreshing => _refreshing;
  bool get hasData => _phase == TodayLoadPhase.ready;

  bool isTaskPending(String id) => _pendingTaskIds.contains(id);

  bool isHabitPending(String id) => _pendingHabitIds.contains(id);

  Future<void> initialize() async {
    if (_phase != TodayLoadPhase.idle) {
      return;
    }
    await refresh();
  }

  Future<void> refresh() async {
    if (_refreshing) {
      return;
    }
    final hadData = hasData;
    _refreshing = true;
    _loadError = null;
    _actionError = null;
    _retryAction = null;
    if (!hadData) {
      _phase = TodayLoadPhase.loading;
    }
    notifyListeners();
    try {
      final timezone = await _resolveTimezone();
      final snapshot = await _repository.load(
        now: _clock(),
        timezone: timezone,
      );
      _todayTasks = snapshot.todayTasks;
      _upcomingTasks = snapshot.upcomingTasks;
      _habits = snapshot.habits;
      _nextEvent = snapshot.nextEvent;
      _phase = TodayLoadPhase.ready;
    } on Object catch (error) {
      _loadError = _messageFor(error);
      if (!hadData) {
        _phase = TodayLoadPhase.error;
      } else {
        _actionError = _loadError;
        _retryAction = refresh;
      }
    } finally {
      _refreshing = false;
      notifyListeners();
    }
  }

  Future<void> toggleTask(String id) async {
    final task = _findTask(id);
    if (task == null) {
      return;
    }
    await _setTaskDone(id, !task.isDone);
  }

  Future<void> _setTaskDone(
    String id,
    bool done, {
    bool rememberFailure = true,
  }) async {
    if (_pendingTaskIds.contains(id)) {
      return;
    }
    final originalToday = _todayTasks;
    final originalUpcoming = _upcomingTasks;
    if (_findTask(id) == null) {
      return;
    }
    _pendingTaskIds.add(id);
    _actionError = null;
    _retryAction = null;
    _todayTasks = _replaceTask(_todayTasks, id, (task) => task.withDone(done));
    _upcomingTasks = _replaceTask(
      _upcomingTasks,
      id,
      (task) => task.withDone(done),
    );
    notifyListeners();
    try {
      final saved = await _repository.setTaskDone(
        id: id,
        done: done,
        timezone: await _resolveTimezone(),
      );
      _todayTasks = _replaceTask(_todayTasks, id, (_) => saved);
      _upcomingTasks = _replaceTask(_upcomingTasks, id, (_) => saved);
      _telemetry.record(done ? 'task.completed' : 'task.reopened');
    } on Object catch (error) {
      _todayTasks = originalToday;
      _upcomingTasks = originalUpcoming;
      _actionError = _messageFor(error);
      if (rememberFailure) {
        _retryAction = () => _setTaskDone(id, done, rememberFailure: false);
      }
    } finally {
      _pendingTaskIds.remove(id);
      notifyListeners();
    }
  }

  Future<void> setHabitCheckIn(String id, TodayHabitCheckIn? checkIn) async {
    await _setHabitCheckIn(id, checkIn);
  }

  Future<void> _setHabitCheckIn(
    String id,
    TodayHabitCheckIn? checkIn, {
    bool rememberFailure = true,
  }) async {
    if (_pendingHabitIds.contains(id)) {
      return;
    }
    final index = _habits.indexWhere((habit) => habit.id == id);
    if (index < 0) {
      return;
    }
    final original = _habits;
    final optimisticState = switch (checkIn) {
      TodayHabitCheckIn.done => TodayHabitState.done,
      TodayHabitCheckIn.excused => TodayHabitState.excused,
      null => TodayHabitState.open,
    };
    _pendingHabitIds.add(id);
    _actionError = null;
    _retryAction = null;
    _habits = _replaceHabit(
      _habits,
      id,
      (habit) => habit.withOptimisticState(optimisticState),
    );
    notifyListeners();
    try {
      final date = _dateString(_clock());
      final timezone = await _resolveTimezone();
      final saved = checkIn == null
          ? await _repository.clearHabitCheckIn(
              id: id,
              date: date,
              timezone: timezone,
            )
          : await _repository.setHabitCheckIn(
              id: id,
              date: date,
              status: checkIn,
              timezone: timezone,
            );
      _habits = _replaceHabit(_habits, id, (_) => saved);
      _telemetry.record(
        checkIn == null ? 'habit.cleared' : 'habit.${checkIn.name}',
      );
    } on Object catch (error) {
      _habits = original;
      _actionError = _messageFor(error);
      if (rememberFailure) {
        _retryAction = () =>
            _setHabitCheckIn(id, checkIn, rememberFailure: false);
      }
    } finally {
      _pendingHabitIds.remove(id);
      notifyListeners();
    }
  }

  Future<void> retryLastAction() async {
    final retry = _retryAction;
    if (retry == null) {
      return;
    }
    await retry();
  }

  void clearActionError() {
    _actionError = null;
    _retryAction = null;
    notifyListeners();
  }

  TodayTask? _findTask(String id) {
    for (final task in _todayTasks) {
      if (task.id == id) {
        return task;
      }
    }
    for (final task in _upcomingTasks) {
      if (task.id == id) {
        return task;
      }
    }
    return null;
  }

  Future<String> _resolveTimezone() async {
    return _timezone ??= await _timezoneProvider.currentIdentifier();
  }

  List<TodayTask> _replaceTask(
    List<TodayTask> source,
    String id,
    TodayTask Function(TodayTask task) update,
  ) {
    return List.unmodifiable(
      source.map((task) => task.id == id ? update(task) : task),
    );
  }

  List<TodayHabit> _replaceHabit(
    List<TodayHabit> source,
    String id,
    TodayHabit Function(TodayHabit habit) update,
  ) {
    return List.unmodifiable(
      source.map((habit) => habit.id == id ? update(habit) : habit),
    );
  }

  String _dateString(DateTime value) {
    String twoDigits(int number) => number.toString().padLeft(2, '0');
    return '${value.year}-${twoDigits(value.month)}-${twoDigits(value.day)}';
  }

  String _messageFor(Object error) {
    final text = error.toString().replaceFirst(
      RegExp(r'^\w+Exception:\s*'),
      '',
    );
    return text.isEmpty ? 'Something went wrong. Try again.' : text;
  }
}
