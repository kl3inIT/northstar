import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:northstar/data/repositories/assistant_repository.dart';
import 'package:northstar/data/repositories/auth_repository.dart';
import 'package:northstar/domain/models/assistant_models.dart';
import 'package:northstar/domain/models/auth_session.dart';
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

    await tester.tap(find.byIcon(CupertinoIcons.doc_text));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('notes-page')), findsOneWidget);
  });

  testWidgets('opens focused Capture from the Assistant navigation bar', (
    tester,
  ) async {
    _setWindowSize(tester, const Size(390, 844));
    await tester.pumpWidget(_testApp(_signedInRepository()));
    await tester.pumpAndSettle();

    expect(find.bySemanticsLabel('Capture'), findsOneWidget);
    await tester.tap(find.byKey(const Key('assistant-capture-button')));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('capture-page')), findsOneWidget);
    expect(find.text('Capture it now'), findsOneWidget);
    expect(find.byType(CupertinoTabBar), findsNothing);
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

    await tester.tap(find.byIcon(CupertinoIcons.doc_text));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('notes-page')), findsOneWidget);

    tester.view.physicalSize = const Size(1024, 768);
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('northstar-sidebar')), findsOneWidget);
    expect(find.byKey(const Key('notes-page')), findsOneWidget);
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

void _setWindowSize(WidgetTester tester, Size size) {
  tester.view.devicePixelRatio = 1;
  tester.view.physicalSize = size;
  addTearDown(tester.view.resetDevicePixelRatio);
  addTearDown(tester.view.resetPhysicalSize);
}
