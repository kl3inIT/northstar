import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:northstar/data/repositories/calendar_repository.dart';
import 'package:northstar/data/repositories/habits_repository.dart';
import 'package:northstar/data/repositories/note_detail_repository.dart';
import 'package:northstar/data/services/device_timezone.dart';
import 'package:northstar/domain/models/calendar_models.dart';
import 'package:northstar/domain/models/note_detail.dart';
import 'package:northstar/domain/models/today_models.dart';
import 'package:northstar/ui/features/calendar/view_models/calendar_view_model.dart';
import 'package:northstar/ui/features/calendar/views/calendar_view.dart';
import 'package:northstar/ui/features/habits/view_models/habits_view_model.dart';
import 'package:northstar/ui/features/habits/views/habits_view.dart';
import 'package:northstar/ui/features/notes/views/note_detail_view.dart';

void main() {
  testWidgets('renders a recurring Calendar agenda on a compact dark screen', (
    tester,
  ) async {
    _setWindowSize(tester, const Size(390, 844));
    final viewModel = CalendarViewModel(
      repository: _CalendarViewRepository(),
      timezoneProvider: _TimezoneProvider(),
      clock: () => DateTime(2026, 7, 13),
    );

    await tester.pumpWidget(
      CupertinoApp(
        theme: const CupertinoThemeData(brightness: Brightness.dark),
        home: CalendarView(viewModel: viewModel),
      ),
    );
    await _pumpAsync(tester);

    expect(find.byKey(const Key('calendar-agenda')), findsOneWidget);
    expect(find.text('IELTS class'), findsOneWidget);
    expect(find.byIcon(CupertinoIcons.repeat), findsOneWidget);
    expect(find.text('13 Jul – 26 Jul'), findsOneWidget);
  });

  testWidgets('shows a Habits error, retries, and performs a quick check-in', (
    tester,
  ) async {
    _setWindowSize(tester, const Size(390, 844));
    final repository = _HabitsViewRepository(loadFailures: 1);
    final viewModel = HabitsViewModel(
      repository: repository,
      timezoneProvider: _TimezoneProvider(),
      clock: () => DateTime(2026, 7, 13),
    );

    await tester.pumpWidget(
      CupertinoApp(home: HabitsView(viewModel: viewModel)),
    );
    await _pumpAsync(tester);
    expect(find.byKey(const Key('habits-load-error')), findsOneWidget);

    await tester.tap(find.byKey(const Key('habits-message-action')));
    await _pumpAsync(tester);
    expect(find.byKey(const Key('habits-list')), findsOneWidget);

    await tester.tap(find.byKey(const Key('habits-done-habit-1')));
    await _pumpAsync(tester);
    expect(repository.status, TodayHabitCheckIn.done);
    expect(find.byKey(const Key('habits-clear-habit-1')), findsOneWidget);
  });

  testWidgets(
    'renders Assistant note Markdown without exposing edit controls',
    (tester) async {
      _setWindowSize(tester, const Size(390, 844));
      await tester.pumpWidget(
        CupertinoApp(
          theme: const CupertinoThemeData(brightness: Brightness.dark),
          home: NoteDetailView(
            repository: _NoteViewRepository(),
            slug: 'mobile-behavior',
          ),
        ),
      );
      await _pumpAsync(tester);

      expect(find.byKey(const Key('note-detail-markdown')), findsOneWidget);
      expect(find.text('Mobile behavior'), findsOneWidget);
      expect(find.text('Retrieved from Assistant.'), findsOneWidget);
      expect(find.byType(CupertinoTextField), findsNothing);
    },
  );
}

class _TimezoneProvider implements DeviceTimezoneProvider {
  @override
  Future<String> currentIdentifier() async => 'Asia/Bangkok';
}

class _CalendarViewRepository implements CalendarRepository {
  @override
  Future<List<CalendarAgendaEvent>> agenda({
    required DateTime from,
    required DateTime to,
    required String timezone,
  }) async => [
    CalendarAgendaEvent(
      id: 'event-1',
      title: 'IELTS class',
      startAt: DateTime(2026, 7, 13, 12),
      endAt: DateTime(2026, 7, 13, 13),
      allDay: false,
      color: 'BLUE',
      notes: 'Room 2',
      rrule: 'FREQ=WEEKLY',
    ),
  ];
}

class _HabitsViewRepository implements HabitsRepository {
  _HabitsViewRepository({this.loadFailures = 0});

  int loadFailures;
  TodayHabitCheckIn? status;

  @override
  Future<List<TodayHabit>> list(String timezone) async {
    if (loadFailures > 0) {
      loadFailures -= 1;
      throw StateError('Offline');
    }
    return [_habit()];
  }

  @override
  Future<TodayHabit> setCheckIn({
    required String id,
    required String date,
    required TodayHabitCheckIn status,
    required String timezone,
  }) async {
    this.status = status;
    return _habit(state: TodayHabitState.done);
  }

  @override
  Future<TodayHabit> clearCheckIn({
    required String id,
    required String date,
    required String timezone,
  }) async => _habit();
}

class _NoteViewRepository implements NoteDetailRepository {
  @override
  Future<NoteDetail> get(String slug) async => NoteDetail(
    id: 'note-1',
    title: 'Mobile behavior',
    slug: slug,
    folderPath: 'research',
    contentMarkdown: 'Retrieved from **Assistant**.',
    tags: const ['mobile'],
    status: 'ACTIVE',
    updatedAt: DateTime.utc(2026, 7, 13),
  );
}

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
