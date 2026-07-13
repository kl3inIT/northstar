import 'package:flutter_test/flutter_test.dart';
import 'package:northstar/data/repositories/today_repository.dart';
import 'package:northstar/data/services/device_timezone.dart';
import 'package:northstar/domain/models/today_models.dart';
import 'package:northstar/ui/features/today/view_models/today_view_model.dart';

void main() {
  test('loads Today with the device IANA timezone', () async {
    final repository = _TodayRepository();
    final viewModel = _viewModel(repository);

    await viewModel.initialize();

    expect(viewModel.phase, TodayLoadPhase.ready);
    expect(repository.loadedTimezone, 'Asia/Bangkok');
    expect(viewModel.todayTasks.single.id, 'task-1');
    expect(viewModel.habits.single.id, 'habit-1');
  });

  test('optimistically completes a task and rolls back on failure', () async {
    final repository = _TodayRepository(taskFailures: 1);
    final viewModel = _viewModel(repository);
    await viewModel.initialize();

    final mutation = viewModel.toggleTask('task-1');
    expect(viewModel.todayTasks.single.isDone, isTrue);
    expect(viewModel.isTaskPending('task-1'), isTrue);
    await mutation;

    expect(viewModel.todayTasks.single.isDone, isFalse);
    expect(viewModel.isTaskPending('task-1'), isFalse);
    expect(viewModel.actionError, contains('Task update failed'));

    await viewModel.retryLastAction();
    expect(viewModel.todayTasks.single.isDone, isTrue);
    expect(repository.taskCalls, 2);
  });

  test('optimistically checks in a habit and rolls back on failure', () async {
    final repository = _TodayRepository(habitFailures: 1);
    final viewModel = _viewModel(repository);
    await viewModel.initialize();

    final mutation = viewModel.setHabitCheckIn(
      'habit-1',
      TodayHabitCheckIn.done,
    );
    expect(viewModel.habits.single.state, TodayHabitState.done);
    expect(viewModel.habits.single.completedThisWeek, 3);
    await mutation;

    expect(viewModel.habits.single.state, TodayHabitState.open);
    expect(viewModel.habits.single.completedThisWeek, 2);
    expect(viewModel.actionError, contains('Habit update failed'));

    await viewModel.retryLastAction();
    expect(viewModel.habits.single.state, TodayHabitState.done);
    expect(repository.lastHabitDate, '2026-07-13');
    expect(repository.lastHabitTimezone, 'Asia/Bangkok');
  });

  test('keeps stale daily data visible when a refresh fails', () async {
    final repository = _TodayRepository();
    final viewModel = _viewModel(repository);
    await viewModel.initialize();
    repository.loadFailures = 1;

    await viewModel.refresh();

    expect(viewModel.phase, TodayLoadPhase.ready);
    expect(viewModel.todayTasks.single.id, 'task-1');
    expect(viewModel.actionError, contains('Refresh failed'));
  });
}

TodayViewModel _viewModel(_TodayRepository repository) {
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

class _TodayRepository implements TodayRepository {
  _TodayRepository({this.taskFailures = 0, this.habitFailures = 0});

  int taskFailures;
  int habitFailures;
  int loadFailures = 0;
  int taskCalls = 0;
  String? loadedTimezone;
  String? lastHabitDate;
  String? lastHabitTimezone;

  @override
  Future<TodaySnapshot> load({
    required DateTime now,
    required String timezone,
  }) async {
    loadedTimezone = timezone;
    if (loadFailures > 0) {
      loadFailures -= 1;
      throw StateError('Refresh failed');
    }
    return TodaySnapshot(
      todayTasks: [_task()],
      upcomingTasks: [_task()],
      habits: [_habit()],
      nextEvent: _event(),
    );
  }

  @override
  Future<TodayTask> setTaskDone({
    required String id,
    required bool done,
    required String timezone,
  }) async {
    taskCalls += 1;
    await Future<void>.delayed(Duration.zero);
    if (taskFailures > 0) {
      taskFailures -= 1;
      throw StateError('Task update failed');
    }
    return _task(done: done);
  }

  @override
  Future<TodayHabit> setHabitCheckIn({
    required String id,
    required String date,
    required TodayHabitCheckIn status,
    required String timezone,
  }) async {
    lastHabitDate = date;
    lastHabitTimezone = timezone;
    await Future<void>.delayed(Duration.zero);
    if (habitFailures > 0) {
      habitFailures -= 1;
      throw StateError('Habit update failed');
    }
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

TodayCalendarEvent _event() => TodayCalendarEvent(
  id: 'event-1',
  title: 'IELTS class',
  startAt: DateTime.utc(2026, 7, 13, 12),
  endAt: DateTime.utc(2026, 7, 13, 13),
  allDay: false,
  color: 'BLUE',
);
