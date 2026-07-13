import 'dart:async';

import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:northstar/data/repositories/today_repository.dart';
import 'package:northstar/data/services/device_timezone.dart';
import 'package:northstar/domain/models/today_models.dart';
import 'package:northstar/ui/features/today/view_models/today_view_model.dart';
import 'package:northstar/ui/features/today/views/today_view.dart';

void main() {
  testWidgets('renders loading and daily actions on a compact dark screen', (
    tester,
  ) async {
    _setWindowSize(tester, const Size(390, 844));
    final completer = Completer<TodaySnapshot>();
    final repository = _TodayViewRepository(loadCompleter: completer);
    final viewModel = _viewModel(repository);

    await tester.pumpWidget(_app(viewModel, brightness: Brightness.dark));
    await tester.pump();
    expect(find.byKey(const Key('today-loading-indicator')), findsOneWidget);

    completer.complete(_snapshot());
    await _pumpAsync(tester);

    expect(find.byKey(const Key('today-compact-layout')), findsOneWidget);
    expect(find.text('Review vocabulary'), findsOneWidget);
    expect(find.text('IELTS class'), findsOneWidget);
    expect(find.text('Read 20 minutes'), findsOneWidget);

    await tester.tap(find.byKey(const Key('today-task-toggle-task-1')));
    await _pumpAsync(tester);
    expect(repository.taskDone, isTrue);

    await tester.ensureVisible(
      find.byKey(const Key('today-habit-done-habit-1')),
    );
    await tester.tap(find.byKey(const Key('today-habit-done-habit-1')));
    await _pumpAsync(tester);
    expect(repository.habitStatus, TodayHabitCheckIn.done);
  });

  testWidgets('shows a retryable load error and then empty states', (
    tester,
  ) async {
    _setWindowSize(tester, const Size(390, 844));
    final repository = _TodayViewRepository(loadFailures: 1);

    await tester.pumpWidget(_app(_viewModel(repository)));
    await _pumpAsync(tester);

    expect(find.byKey(const Key('today-load-retry')), findsOneWidget);
    expect(find.textContaining('Offline'), findsOneWidget);
    await tester.tap(find.byKey(const Key('today-load-retry')));
    await _pumpAsync(tester);

    expect(find.byKey(const Key('today-tasks-empty')), findsOneWidget);
    expect(find.byKey(const Key('today-habits-empty')), findsOneWidget);
    expect(find.byKey(const Key('today-next-event-empty')), findsOneWidget);
  });

  testWidgets('uses a two-column layout on an expanded screen', (tester) async {
    _setWindowSize(tester, const Size(1024, 768));

    await tester.pumpWidget(_app(_viewModel(_TodayViewRepository())));
    await _pumpAsync(tester);

    expect(find.byKey(const Key('today-expanded-layout')), findsOneWidget);
    expect(find.byKey(const Key('today-compact-layout')), findsNothing);
  });
}

Widget _app(TodayViewModel viewModel, {Brightness? brightness}) {
  return CupertinoApp(
    theme: CupertinoThemeData(brightness: brightness),
    home: TodayView(viewModel: viewModel),
  );
}

TodayViewModel _viewModel(_TodayViewRepository repository) {
  return TodayViewModel(
    repository: repository,
    timezoneProvider: _TimezoneProvider(),
    clock: () => DateTime(2026, 7, 13, 9),
  );
}

class _TimezoneProvider implements DeviceTimezoneProvider {
  @override
  Future<String> currentIdentifier() async => 'Asia/Bangkok';
}

class _TodayViewRepository implements TodayRepository {
  _TodayViewRepository({this.loadCompleter, this.loadFailures = 0});

  final Completer<TodaySnapshot>? loadCompleter;
  int loadFailures;
  bool? taskDone;
  TodayHabitCheckIn? habitStatus;

  @override
  Future<TodaySnapshot> load({
    required DateTime now,
    required String timezone,
  }) async {
    if (loadFailures > 0) {
      loadFailures -= 1;
      throw StateError('Offline');
    }
    if (loadCompleter case final completer?) {
      return completer.future;
    }
    return loadFailures == 0 && taskDone == null && habitStatus == null
        ? _snapshot(empty: true)
        : _snapshot();
  }

  @override
  Future<TodayTask> setTaskDone({
    required String id,
    required bool done,
    required String timezone,
  }) async {
    taskDone = done;
    return _task(done: done);
  }

  @override
  Future<TodayHabit> setHabitCheckIn({
    required String id,
    required String date,
    required TodayHabitCheckIn status,
    required String timezone,
  }) async {
    habitStatus = status;
    return _habit(
      state: status == TodayHabitCheckIn.done
          ? TodayHabitState.done
          : TodayHabitState.excused,
    );
  }

  @override
  Future<TodayHabit> clearHabitCheckIn({
    required String id,
    required String date,
    required String timezone,
  }) async => _habit();
}

TodaySnapshot _snapshot({bool empty = false}) {
  return TodaySnapshot(
    todayTasks: empty ? const [] : [_task()],
    upcomingTasks: const [],
    habits: empty ? const [] : [_habit()],
    nextEvent: empty
        ? null
        : TodayCalendarEvent(
            id: 'event-1',
            title: 'IELTS class',
            startAt: DateTime.utc(2026, 7, 13, 12),
            endAt: DateTime.utc(2026, 7, 13, 13),
            allDay: false,
            color: 'BLUE',
          ),
  );
}

TodayTask _task({bool done = false}) => TodayTask(
  id: 'task-1',
  title: 'Review vocabulary',
  status: done ? TodayTaskStatus.done : TodayTaskStatus.open,
  createdAt: DateTime.utc(2026, 7, 12),
  dueDate: '2026-07-13',
);

TodayHabit _habit({TodayHabitState state = TodayHabitState.open}) => TodayHabit(
  id: 'habit-1',
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

void _setWindowSize(WidgetTester tester, Size size) {
  tester.view.devicePixelRatio = 1;
  tester.view.physicalSize = size;
  addTearDown(tester.view.resetDevicePixelRatio);
  addTearDown(tester.view.resetPhysicalSize);
}

Future<void> _pumpAsync(WidgetTester tester) async {
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 250));
}
