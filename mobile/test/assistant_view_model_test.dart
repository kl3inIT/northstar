import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:northstar/data/repositories/assistant_repository.dart';
import 'package:northstar/domain/models/assistant_models.dart';
import 'package:northstar/ui/features/assistant/view_models/assistant_view_model.dart';

void main() {
  test('loads the newest conversation and its history once', () async {
    final repository = _FakeAssistantRepository(
      conversations: [
        AssistantConversation(
          id: 'conversation-1',
          title: 'Today',
          lastAt: DateTime(2026, 7, 11),
          messageCount: 1,
        ),
      ],
      historyMessages: const [
        AssistantMessage(
          id: 'history-1',
          role: AssistantRole.assistant,
          text: 'Welcome back',
        ),
      ],
    );
    final viewModel = AssistantViewModel(repository);

    await viewModel.initialize();
    await viewModel.initialize();

    expect(viewModel.conversationId, 'conversation-1');
    expect(viewModel.messages.single.text, 'Welcome back');
    expect(repository.listCalls, 1);
    expect(repository.historyCalls, 1);
  });

  test('shows waiting immediately then applies text and tool events', () async {
    final repository = _FakeAssistantRepository();
    final viewModel = AssistantViewModel(repository);
    await viewModel.initialize();

    final sendFuture = viewModel.send('Plan my day');

    expect(viewModel.isSending, isTrue);
    expect(viewModel.messages.last.status, AssistantMessageStatus.streaming);
    expect(viewModel.messages.last.hasVisibleOutput, isFalse);

    repository.stream.add(const AssistantTextDeltaEvent('Start with '));
    repository.stream.add(
      const AssistantToolEvent(
        id: 'task-tool',
        name: 'listTasks',
        status: AssistantToolStatus.running,
      ),
    );
    await Future<void>.delayed(Duration.zero);

    expect(viewModel.messages.last.text, 'Start with ');
    expect(viewModel.messages.last.tools.single.name, 'listTasks');
    expect(
      viewModel.messages.last.tools.single.status,
      AssistantToolStatus.running,
    );

    repository.stream.add(const AssistantTurnFinishedEvent());
    await repository.stream.close();
    await sendFuture;

    expect(viewModel.isSending, isFalse);
    expect(viewModel.messages.last.status, AssistantMessageStatus.complete);
  });

  test('keeps partial output and exposes retry after stream failure', () async {
    final repository = _FakeAssistantRepository();
    final viewModel = AssistantViewModel(repository);
    await viewModel.initialize();

    final sendFuture = viewModel.send('Summarize notes');
    repository.stream.add(const AssistantTextDeltaEvent('Partial answer'));
    repository.stream.addError(StateError('Connection lost'));
    await sendFuture;

    expect(viewModel.messages.last.text, 'Partial answer');
    expect(viewModel.messages.last.status, AssistantMessageStatus.failed);
    expect(viewModel.messages.last.errorMessage, contains('Connection lost'));
    expect(viewModel.canRetry, isTrue);
  });

  test('stop cancels the active turn and preserves partial text', () async {
    final repository = _FakeAssistantRepository();
    final viewModel = AssistantViewModel(repository);
    await viewModel.initialize();

    final sendFuture = viewModel.send('Keep going');
    repository.stream.add(const AssistantTextDeltaEvent('First part'));
    await Future<void>.delayed(Duration.zero);
    await viewModel.stop();
    await sendFuture;

    expect(viewModel.isSending, isFalse);
    expect(viewModel.messages.last.text, 'First part');
    expect(viewModel.messages.last.status, AssistantMessageStatus.stopped);
  });
}

class _FakeAssistantRepository implements AssistantRepository {
  _FakeAssistantRepository({
    this.conversations = const [],
    this.historyMessages = const [],
  });

  final List<AssistantConversation> conversations;
  final List<AssistantMessage> historyMessages;
  final StreamController<AssistantTurnEvent> stream =
      StreamController<AssistantTurnEvent>();
  int listCalls = 0;
  int historyCalls = 0;

  @override
  Future<List<AssistantConversation>> listConversations() async {
    listCalls += 1;
    return conversations;
  }

  @override
  Future<List<AssistantMessage>> history(String conversationId) async {
    historyCalls += 1;
    return historyMessages;
  }

  @override
  Stream<AssistantTurnEvent> streamTurn({
    required String conversationId,
    required String message,
  }) => stream.stream;
}
