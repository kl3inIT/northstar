import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:northstar/data/repositories/calendar_repository.dart';
import 'package:northstar/data/repositories/habits_repository.dart';
import 'package:northstar/data/repositories/note_detail_repository.dart';
import 'package:northstar/data/services/device_timezone.dart';
import 'package:northstar/data/services/interaction_telemetry.dart';
import 'package:northstar/domain/models/calendar_models.dart';
import 'package:northstar/domain/models/note_detail.dart';
import 'package:northstar/domain/models/today_models.dart';
import 'package:northstar/ui/features/calendar/view_models/calendar_view_model.dart';
import 'package:northstar/ui/features/habits/view_models/habits_view_model.dart';
import 'package:northstar/ui/features/notes/view_models/note_detail_view_model.dart';

void main() {
  test('Calendar moves through exclusive 14-day backend ranges', () async {
    final repository = _CalendarRepository();
    final viewModel = CalendarViewModel(
      repository: repository,
      timezoneProvider: _TimezoneProvider(),
      clock: () => DateTime(2026, 7, 13, 9),
    );

    await viewModel.initialize();
    expect(repository.calls.single, (
      from: DateTime(2026, 7, 13),
      to: DateTime(2026, 7, 27),
      timezone: 'Asia/Bangkok',
    ));

    await viewModel.nextRange();
    expect(viewModel.anchor, DateTime(2026, 7, 27));
    expect(repository.calls.last.to, DateTime(2026, 8, 10));

    await viewModel.previousRange();
    expect(viewModel.anchor, DateTime(2026, 7, 13));
  });

  test(
    'Habits rolls back a failed check-in and records content-free success',
    () async {
      final repository = _HabitsRepository(failures: 1);
      final telemetry = _Telemetry();
      final viewModel = HabitsViewModel(
        repository: repository,
        timezoneProvider: _TimezoneProvider(),
        telemetry: telemetry,
        clock: () => DateTime(2026, 7, 13, 9),
      );
      await viewModel.initialize();

      final failed = viewModel.setCheckIn('habit-1', TodayHabitCheckIn.done);
      expect(viewModel.habits.single.state, TodayHabitState.done);
      await failed;

      expect(viewModel.habits.single.state, TodayHabitState.open);
      expect(viewModel.errorMessage, contains('Check-in failed'));
      expect(telemetry.actions, isEmpty);

      await viewModel.setCheckIn('habit-1', TodayHabitCheckIn.done);
      expect(viewModel.habits.single.state, TodayHabitState.done);
      expect(repository.date, '2026-07-13');
      expect(repository.timezone, 'Asia/Bangkok');
      expect(telemetry.actions, ['habit.done']);
    },
  );

  test('Habits rollback preserves a concurrent check-in', () async {
    final repository = _ConcurrentHabitsRepository();
    final viewModel = HabitsViewModel(
      repository: repository,
      timezoneProvider: _TimezoneProvider(),
      clock: () => DateTime(2026, 7, 13, 9),
    );
    await viewModel.initialize();

    final failed = viewModel.setCheckIn('habit-1', TodayHabitCheckIn.done);
    await viewModel.setCheckIn('habit-2', TodayHabitCheckIn.done);
    repository.failure.completeError(Exception('Check-in failed'));
    await failed;

    expect(_habitById(viewModel.habits, 'habit-1').state, TodayHabitState.open);
    expect(_habitById(viewModel.habits, 'habit-2').state, TodayHabitState.done);
  });

  test(
    'Note detail exposes a stable retryable error and then content',
    () async {
      final repository = _NoteRepository(failures: 1);
      final viewModel = NoteDetailViewModel(
        repository: repository,
        slug: 'mobile-behavior',
      );

      await viewModel.load();
      expect(viewModel.phase, NoteDetailPhase.error);
      expect(viewModel.errorMessage, 'This note is unavailable.');

      await viewModel.load();
      expect(viewModel.phase, NoteDetailPhase.ready);
      expect(viewModel.note?.title, 'Mobile behavior');
    },
  );
}

class _TimezoneProvider implements DeviceTimezoneProvider {
  @override
  Future<String> currentIdentifier() async => 'Asia/Bangkok';
}

class _CalendarRepository implements CalendarRepository {
  final calls = <({DateTime from, DateTime to, String timezone})>[];

  @override
  Future<List<CalendarAgendaEvent>> agenda({
    required DateTime from,
    required DateTime to,
    required String timezone,
  }) async {
    calls.add((from: from, to: to, timezone: timezone));
    return const [];
  }
}

class _HabitsRepository implements HabitsRepository {
  _HabitsRepository({this.failures = 0});

  int failures;
  String? date;
  String? timezone;

  @override
  Future<List<TodayHabit>> list(String timezone) async => [_habit()];

  @override
  Future<TodayHabit> setCheckIn({
    required String id,
    required String date,
    required TodayHabitCheckIn status,
    required String timezone,
  }) async {
    this.date = date;
    this.timezone = timezone;
    await Future<void>.delayed(Duration.zero);
    if (failures > 0) {
      failures -= 1;
      throw StateError('Check-in failed');
    }
    return _habit(state: TodayHabitState.done);
  }

  @override
  Future<TodayHabit> clearCheckIn({
    required String id,
    required String date,
    required String timezone,
  }) async => _habit();
}

class _ConcurrentHabitsRepository implements HabitsRepository {
  final failure = Completer<TodayHabit>();

  @override
  Future<List<TodayHabit>> list(String timezone) async => [
    _habit(id: 'habit-1'),
    _habit(id: 'habit-2'),
  ];

  @override
  Future<TodayHabit> setCheckIn({
    required String id,
    required String date,
    required TodayHabitCheckIn status,
    required String timezone,
  }) {
    return id == 'habit-1'
        ? failure.future
        : Future.value(_habit(id: id, state: TodayHabitState.done));
  }

  @override
  Future<TodayHabit> clearCheckIn({
    required String id,
    required String date,
    required String timezone,
  }) async => _habit(id: id);
}

class _NoteRepository implements NoteDetailRepository {
  _NoteRepository({this.failures = 0});

  int failures;

  @override
  Future<NoteDetail> get(String slug) async {
    if (failures > 0) {
      failures -= 1;
      throw Exception('internal repository detail');
    }
    return _note();
  }
}

class _Telemetry implements InteractionTelemetry {
  final actions = <String>[];

  @override
  void record(String action) => actions.add(action);
}

TodayHabit _habit({
  String id = 'habit-1',
  TodayHabitState state = TodayHabitState.open,
}) => TodayHabit(
  id: id,
  title: 'Read 20 minutes',
  color: 'INDIGO',
  state: state,
  dueToday: true,
  completedThisWeek: state == TodayHabitState.done ? 3 : 2,
  targetThisWeek: 5,
  consistency30: 80,
  consistency90: 75,
  currentStreak: 3,
  bestStreak: 12,
  cue: 'After dinner',
);

NoteDetail _note() => NoteDetail(
  id: 'note-1',
  title: 'Mobile behavior',
  slug: 'mobile-behavior',
  folderPath: 'research',
  contentMarkdown: '# Mobile behavior',
  tags: const ['mobile'],
  status: 'ACTIVE',
  updatedAt: DateTime.utc(2026, 7, 13),
);

TodayHabit _habitById(List<TodayHabit> habits, String id) {
  return habits.singleWhere((habit) => habit.id == id);
}
