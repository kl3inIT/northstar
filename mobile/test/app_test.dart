import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:northstar/data/repositories/assistant_repository.dart';
import 'package:northstar/data/repositories/auth_repository.dart';
import 'package:northstar/data/repositories/finance_repository.dart';
import 'package:northstar/data/repositories/study_review_repository.dart';
import 'package:northstar/data/repositories/today_repository.dart';
import 'package:northstar/data/services/device_timezone.dart';
import 'package:northstar/domain/models/assistant_models.dart';
import 'package:northstar/domain/models/auth_session.dart';
import 'package:northstar/domain/models/finance_models.dart';
import 'package:northstar/domain/models/study_review_models.dart';
import 'package:northstar/domain/models/today_models.dart';
import 'package:northstar/ui/core/northstar_app.dart';

void main() {
  testWidgets('uses Cupertino tab navigation on a compact window', (
    tester,
  ) async {
    _setWindowSize(tester, const Size(390, 844));

    await tester.pumpWidget(_testApp(_signedInRepository()));
    await tester.pumpAndSettle();

    expect(find.byType(CupertinoApp), findsOneWidget);
    expect(find.byType(CupertinoTabBar), findsOneWidget);
    expect(find.byKey(const Key('northstar-sidebar')), findsNothing);
    expect(find.byKey(const Key('assistant-page')), findsOneWidget);

    await tester.tap(find.byIcon(CupertinoIcons.book));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 250));

    expect(find.byKey(const Key('study-page')), findsOneWidget);
  });

  testWidgets('opens focused Capture from the Assistant composer add menu', (
    tester,
  ) async {
    _setWindowSize(tester, const Size(390, 844));
    await tester.pumpWidget(_testApp(_signedInRepository()));
    await tester.pumpAndSettle();

    expect(find.bySemanticsLabel('Add to Northstar'), findsOneWidget);
    await tester.tap(find.byKey(const Key('assistant-add-button')));
    await tester.pumpAndSettle();

    expect(find.byType(CupertinoActionSheet), findsOneWidget);
    await tester.tap(find.byKey(const Key('assistant-add-quick-capture')));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('capture-page')), findsOneWidget);
    expect(find.text('Capture it now'), findsOneWidget);
    expect(find.byType(CupertinoTabBar), findsNothing);
  });

  testWidgets('opens the integrated Today daily-actions destination', (
    tester,
  ) async {
    _setWindowSize(tester, const Size(390, 844));
    await tester.pumpWidget(_testApp(_signedInRepository()));
    await tester.pumpAndSettle();

    await tester.tap(find.byIcon(CupertinoIcons.check_mark_circled));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 250));

    expect(find.byKey(const Key('today-page')), findsOneWidget);
    expect(find.byKey(const Key('today-tasks-empty')), findsOneWidget);
    expect(find.text('Today'), findsWidgets);
  });

  testWidgets('opens the receipt source picker from the Assistant add menu', (
    tester,
  ) async {
    _setWindowSize(tester, const Size(390, 844));
    await tester.pumpWidget(_testApp(_signedInRepository()));
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const Key('assistant-add-button')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('assistant-add-receipt')));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('capture-page')), findsOneWidget);
    expect(find.text('Scan receipt'), findsOneWidget);
    expect(find.byKey(const Key('capture-receipt-library')), findsOneWidget);
  });

  testWidgets('uses the Cupertino sidebar on an expanded window', (
    tester,
  ) async {
    _setWindowSize(tester, const Size(1024, 768));

    await tester.pumpWidget(_testApp(_signedInRepository()));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('northstar-sidebar')), findsOneWidget);
    expect(find.byType(CupertinoTabBar), findsNothing);

    await tester.tap(find.byKey(const Key('destination-finance')));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('finance-page')), findsOneWidget);
  });

  testWidgets('preserves the selected destination when the window expands', (
    tester,
  ) async {
    _setWindowSize(tester, const Size(390, 844));
    await tester.pumpWidget(_testApp(_signedInRepository()));
    await tester.pumpAndSettle();

    await tester.tap(find.byIcon(CupertinoIcons.book));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 250));
    expect(find.byKey(const Key('study-page')), findsOneWidget);

    tester.view.physicalSize = const Size(1024, 768);
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('northstar-sidebar')), findsOneWidget);
    expect(find.byKey(const Key('study-page')), findsOneWidget);
  });

  testWidgets('explains unfinished More destinations instead of dead-ending', (
    tester,
  ) async {
    _setWindowSize(tester, const Size(390, 844));
    await tester.pumpWidget(_testApp(_signedInRepository()));
    await tester.pumpAndSettle();

    await tester.tap(find.byIcon(CupertinoIcons.ellipsis_circle));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Calendar'));
    await tester.pumpAndSettle();

    expect(find.byType(CupertinoAlertDialog), findsOneWidget);
    expect(
      find.text('This area is planned for a later mobile increment.'),
      findsOneWidget,
    );
  });

  testWidgets('redirects signed-out users to the Cupertino login screen', (
    tester,
  ) async {
    _setWindowSize(tester, const Size(390, 844));
    await tester.pumpWidget(_testApp(_FakeAuthRepository()));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('login-page')), findsOneWidget);
    expect(find.byKey(const Key('assistant-page')), findsNothing);
  });

  testWidgets('signs in and lets go_router open the protected shell', (
    tester,
  ) async {
    _setWindowSize(tester, const Size(390, 844));
    final repository = _FakeAuthRepository();
    await tester.pumpWidget(_testApp(repository));
    await tester.pumpAndSettle();

    await tester.enterText(find.byKey(const Key('username-field')), 'datph');
    await tester.enterText(find.byKey(const Key('password-field')), 'password');
    await tester.tap(find.byKey(const Key('login-button')));
    await tester.pumpAndSettle();

    expect(repository.lastUsername, 'datph');
    expect(repository.lastPassword, 'password');
    expect(find.byKey(const Key('assistant-page')), findsOneWidget);
    expect(find.byKey(const Key('login-page')), findsNothing);
  });

  testWidgets('sign out clears auth state and redirects back to login', (
    tester,
  ) async {
    _setWindowSize(tester, const Size(390, 844));
    final repository = _signedInRepository();
    await tester.pumpWidget(_testApp(repository));
    await tester.pumpAndSettle();

    await tester.tap(find.byIcon(CupertinoIcons.ellipsis_circle));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('sign-out-button')));
    await tester.pumpAndSettle();

    expect(repository.logoutCalls, 1);
    expect(find.byKey(const Key('login-page')), findsOneWidget);
  });
}

_FakeAuthRepository _signedInRepository() {
  return _FakeAuthRepository(
    restoredSession: const AuthSession(username: 'datph'),
  );
}

NorthstarApp _testApp(AuthRepository authRepository) {
  return NorthstarApp(
    authRepository: authRepository,
    assistantRepository: _FakeAssistantRepository(),
    todayRepository: _FakeTodayRepository(),
    timezoneProvider: _FakeTimezoneProvider(),
    studyReviewRepository: _FakeStudyReviewRepository(),
    financeRepository: _FakeFinanceRepository(),
  );
}

class _FakeAuthRepository implements AuthRepository {
  _FakeAuthRepository({this.restoredSession});

  final AuthSession? restoredSession;

  String? lastUsername;
  String? lastPassword;
  int logoutCalls = 0;

  @override
  String? get accessToken => restoredSession == null ? null : 'access-token';

  @override
  Future<String?> refreshAccessToken() async => accessToken;

  @override
  Future<AuthSession?> restore() async => restoredSession;

  @override
  Future<AuthSession> login({
    required String username,
    required String password,
  }) async {
    lastUsername = username;
    lastPassword = password;
    return AuthSession(username: username);
  }

  @override
  Future<void> logout() async => logoutCalls += 1;
}

class _FakeAssistantRepository implements AssistantRepository {
  @override
  Future<List<AssistantConversation>> listConversations() async => const [];

  @override
  Future<List<AssistantMessage>> history(String conversationId) async =>
      const [];

  @override
  Stream<AssistantTurnEvent> streamTurn({
    required String conversationId,
    required String message,
  }) => const Stream.empty();
}

class _FakeTodayRepository implements TodayRepository {
  @override
  Future<TodaySnapshot> load({
    required DateTime now,
    required String timezone,
  }) async {
    return const TodaySnapshot(
      todayTasks: [],
      upcomingTasks: [],
      habits: [],
      nextEvent: null,
    );
  }

  @override
  Future<TodayTask> setTaskDone({
    required String id,
    required bool done,
    required String timezone,
  }) {
    throw UnimplementedError();
  }

  @override
  Future<TodayHabit> setHabitCheckIn({
    required String id,
    required String date,
    required TodayHabitCheckIn status,
    required String timezone,
  }) {
    throw UnimplementedError();
  }

  @override
  Future<TodayHabit> clearHabitCheckIn({
    required String id,
    required String date,
    required String timezone,
  }) {
    throw UnimplementedError();
  }
}

class _FakeTimezoneProvider implements DeviceTimezoneProvider {
  @override
  Future<String> currentIdentifier() async => 'Asia/Bangkok';
}

class _FakeStudyReviewRepository implements StudyReviewRepository {
  @override
  Future<List<VocabReviewCard>> reviewQueue({
    required VocabLanguage language,
    String? deck,
    int limit = 20,
  }) async => const [];

  @override
  Future<void> recordReview({
    required VocabReviewCard card,
    required VocabRating rating,
    required String timezone,
  }) async {}
}

class _FakeFinanceRepository implements FinanceRepository {
  @override
  Future<FinanceGlance> glance({
    required String month,
    required String timezone,
  }) async {
    return FinanceGlance(
      summary: FinanceMonthSummary(
        month: month,
        expenseTotal: 0,
        incomeTotal: 0,
        net: 0,
        exceptionalTotal: 0,
        exceptionalCount: 0,
        previousMonthExpenseTotal: 0,
        categories: const [],
      ),
      transactions: const [],
    );
  }
}

void _setWindowSize(WidgetTester tester, Size size) {
  tester.view.devicePixelRatio = 1;
  tester.view.physicalSize = size;
  addTearDown(tester.view.resetDevicePixelRatio);
  addTearDown(tester.view.resetPhysicalSize);
}
