import 'dart:async';

import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_markdown_plus/flutter_markdown_plus.dart';
import 'package:northstar/data/repositories/assistant_repository.dart';
import 'package:northstar/domain/models/assistant_models.dart';
import 'package:northstar/ui/core/design_system/northstar_tokens.dart';
import 'package:northstar/ui/features/assistant/view_models/assistant_view_model.dart';
import 'package:northstar/ui/features/assistant/views/assistant_landing_view.dart';

void main() {
  testWidgets('renders a visible waiting state before the first SSE event', (
    tester,
  ) async {
    _setWindowSize(tester, const Size(390, 844));
    final repository = _WidgetAssistantRepository();
    final viewModel = AssistantViewModel(repository);

    await tester.pumpWidget(_testApp(viewModel));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const Key('assistant-composer')),
      'Plan my day',
    );
    await tester.tap(find.byKey(const Key('assistant-send-button')));
    await tester.pump();

    expect(
      find.byKey(const Key('assistant-waiting-indicator')),
      findsOneWidget,
    );
    expect(find.byKey(const Key('assistant-stop-button')), findsOneWidget);

    repository.stream.add(const AssistantTextDeltaEvent('Start with focus.'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 16));

    expect(find.text('Start with focus.'), findsOneWidget);
    expect(find.byKey(const Key('assistant-waiting-indicator')), findsNothing);

    await repository.stream.close();
    await tester.pumpAndSettle();
    await tester.pump(const Duration(milliseconds: 300));
  });

  testWidgets('shows tool progress and retry for a failed turn', (
    tester,
  ) async {
    _setWindowSize(tester, const Size(390, 844));
    final repository = _WidgetAssistantRepository();
    final viewModel = AssistantViewModel(repository);

    await tester.pumpWidget(_testApp(viewModel));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const Key('assistant-composer')),
      'Check tasks',
    );
    await tester.tap(find.byKey(const Key('assistant-send-button')));
    await tester.pump();

    repository.stream.add(
      const AssistantToolEvent(
        id: 'tool-1',
        name: 'listTasks',
        status: AssistantToolStatus.running,
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 16));
    expect(find.text('listTasks'), findsOneWidget);

    repository.stream.addError(StateError('Network interrupted'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));
    expect(find.byKey(const Key('assistant-message-error')), findsOneWidget);
    expect(find.byKey(const Key('assistant-retry-button')), findsOneWidget);
  });

  testWidgets('shows persistent conversation navigation at expanded width', (
    tester,
  ) async {
    _setWindowSize(tester, const Size(1024, 768));
    final repository = _WidgetAssistantRepository(
      conversations: [
        AssistantConversation(
          id: 'conversation-1',
          title: 'Weekly planning',
          lastAt: DateTime(2026, 7, 11),
          messageCount: 0,
        ),
      ],
    );

    await tester.pumpWidget(_testApp(AssistantViewModel(repository)));
    await tester.pumpAndSettle();

    expect(
      find.byKey(const Key('assistant-conversation-sidebar')),
      findsOneWidget,
    );
    expect(find.text('Weekly planning'), findsOneWidget);
  });

  testWidgets('uses readable Cupertino Markdown colors in dark mode', (
    tester,
  ) async {
    _setWindowSize(tester, const Size(390, 844));
    final repository = _WidgetAssistantRepository(
      conversations: [
        AssistantConversation(
          id: 'conversation-1',
          title: 'Dark mode',
          lastAt: DateTime(2026, 7, 11),
          messageCount: 1,
        ),
      ],
      historyMessages: const [
        AssistantMessage(
          id: 'assistant-response',
          role: AssistantRole.assistant,
          text: '**Visible response**',
        ),
      ],
    );

    await tester.pumpWidget(
      CupertinoApp(
        theme: const CupertinoThemeData(brightness: Brightness.dark),
        home: AssistantLandingView(viewModel: AssistantViewModel(repository)),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));

    final markdown = tester.widget<MarkdownBody>(find.byType(MarkdownBody));
    final markdownContext = tester.element(find.byType(MarkdownBody));
    final textColor = markdown.styleSheet?.p?.color;
    final background = NorthstarColors.elevatedSurface.resolveFrom(
      markdownContext,
    );

    expect(markdown.styleSheetTheme, MarkdownStyleSheetBaseTheme.cupertino);
    expect(textColor, isNotNull);
    expect(textColor, isNot(background));
    expect(textColor!.computeLuminance(), greaterThan(0.5));
  });
}

Widget _testApp(AssistantViewModel viewModel) {
  return CupertinoApp(home: AssistantLandingView(viewModel: viewModel));
}

class _WidgetAssistantRepository implements AssistantRepository {
  _WidgetAssistantRepository({
    this.conversations = const [],
    this.historyMessages = const [],
  });

  final List<AssistantConversation> conversations;
  final List<AssistantMessage> historyMessages;
  final StreamController<AssistantTurnEvent> stream =
      StreamController<AssistantTurnEvent>();

  @override
  Future<List<AssistantConversation>> listConversations() async =>
      conversations;

  @override
  Future<List<AssistantMessage>> history(String conversationId) async =>
      historyMessages;

  @override
  Stream<AssistantTurnEvent> streamTurn({
    required String conversationId,
    required String message,
  }) => stream.stream;
}

void _setWindowSize(WidgetTester tester, Size size) {
  tester.view.devicePixelRatio = 1;
  tester.view.physicalSize = size;
  addTearDown(tester.view.resetDevicePixelRatio);
  addTearDown(tester.view.resetPhysicalSize);
}
