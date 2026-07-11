import 'package:northstar/data/models/assistant_dtos.dart';
import 'package:northstar/data/services/assistant_api.dart';
import 'package:northstar/domain/models/assistant_models.dart';

abstract interface class AssistantRepository {
  Future<List<AssistantConversation>> listConversations();

  Future<List<AssistantMessage>> history(String conversationId);

  Stream<AssistantTurnEvent> streamTurn({
    required String conversationId,
    required String message,
  });
}

class RemoteAssistantRepository implements AssistantRepository {
  const RemoteAssistantRepository(this._api);

  final AssistantApi _api;

  @override
  Future<List<AssistantConversation>> listConversations() async {
    final conversations = await _api.listConversations();
    return conversations
        .map(
          (item) => AssistantConversation(
            id: item.id,
            title: item.title,
            lastAt: item.lastAt,
            messageCount: item.messages,
          ),
        )
        .toList(growable: false);
  }

  @override
  Future<List<AssistantMessage>> history(String conversationId) async {
    final history = await _api.history(conversationId);
    return history.indexed
        .map((entry) {
          final (index, item) = entry;
          final textParts = item.parts.whereType<AssistantHistoryTextDto>();
          final renderedText = textParts.isEmpty
              ? item.text
              : textParts.map((part) => part.text).join();
          final tools = item.parts
              .whereType<AssistantHistoryToolDto>()
              .map((part) {
                return AssistantToolActivity(
                  id: part.toolCallId,
                  name: part.toolName,
                  status: _historyToolStatus(part),
                );
              })
              .toList(growable: false);
          return AssistantMessage(
            id: 'history-$conversationId-$index',
            role: item.role == 'user'
                ? AssistantRole.user
                : AssistantRole.assistant,
            text: renderedText,
            tools: tools,
          );
        })
        .toList(growable: false);
  }

  @override
  Stream<AssistantTurnEvent> streamTurn({
    required String conversationId,
    required String message,
  }) async* {
    await for (final frame in _api.streamTurn(
      conversationId: conversationId,
      message: message,
    )) {
      switch (frame) {
        case AssistantTextDeltaFrame(:final delta):
          yield AssistantTextDeltaEvent(delta);
        case AssistantToolInputStartFrame(:final toolCallId, :final toolName):
          yield AssistantToolEvent(
            id: toolCallId,
            name: toolName,
            status: AssistantToolStatus.preparing,
          );
        case AssistantToolInputAvailableFrame(
          :final toolCallId,
          :final toolName,
        ):
          yield AssistantToolEvent(
            id: toolCallId,
            name: toolName,
            status: AssistantToolStatus.running,
          );
        case AssistantToolOutputAvailableFrame(:final toolCallId):
          yield AssistantToolEvent(
            id: toolCallId,
            status: AssistantToolStatus.complete,
          );
        case AssistantErrorFrame(:final message):
          throw AssistantApiException(message, statusCode: 502);
        case AssistantDoneFrame():
          yield const AssistantTurnFinishedEvent();
        case AssistantStartFrame() ||
            AssistantTextStartFrame() ||
            AssistantTextEndFrame() ||
            AssistantFinishFrame() ||
            AssistantUnknownFrame():
          break;
      }
    }
  }

  AssistantToolStatus _historyToolStatus(AssistantHistoryToolDto part) {
    if (part.errorText != null) {
      return AssistantToolStatus.failed;
    }
    return switch (part.state) {
      'output-available' => AssistantToolStatus.complete,
      'input-available' => AssistantToolStatus.running,
      _ => AssistantToolStatus.preparing,
    };
  }
}
