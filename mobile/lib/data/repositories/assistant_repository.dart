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

abstract interface class AssistantModelRepository {
  Future<AssistantModelSelection> conversationModel(String conversationId);

  Future<List<AssistantModelOption>> models(String gatewayId);

  Future<AssistantModelSelection> updateConversationModel(
    String conversationId,
    AssistantModelSelection selection,
  );
}

class RemoteAssistantRepository
    implements AssistantRepository, AssistantModelRepository {
  const RemoteAssistantRepository(this._api);

  final AssistantApi _api;

  @override
  Future<AssistantModelSelection> conversationModel(
    String conversationId,
  ) async {
    final value = await _api.conversationModel(conversationId);
    return AssistantModelSelection(
      gatewayId: value.gatewayId,
      modelId: value.modelId,
    );
  }

  @override
  Future<List<AssistantModelOption>> models(String gatewayId) async {
    final values = await _api.models(gatewayId);
    return values
        .map(
          (value) => AssistantModelOption(
            id: value.id,
            displayName: value.displayName,
          ),
        )
        .toList(growable: false);
  }

  @override
  Future<AssistantModelSelection> updateConversationModel(
    String conversationId,
    AssistantModelSelection selection,
  ) async {
    final value = await _api.updateConversationModel(
      conversationId,
      AssistantModelSelectionDto(
        gatewayId: selection.gatewayId,
        modelId: selection.modelId,
      ),
    );
    return AssistantModelSelection(
      gatewayId: value.gatewayId,
      modelId: value.modelId,
    );
  }

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
          final sources = item.parts
              .whereType<AssistantHistorySourceDto>()
              .map(_sourceFromHistory)
              .toList(growable: false);
          return AssistantMessage(
            id: 'history-$conversationId-$index',
            role: item.role == 'user'
                ? AssistantRole.user
                : AssistantRole.assistant,
            text: renderedText,
            tools: tools,
            sources: sources,
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
        case AssistantToolOutputErrorFrame(:final toolCallId):
          yield AssistantToolEvent(
            id: toolCallId,
            status: AssistantToolStatus.failed,
          );
        case AssistantSourceUrlFrame(:final sourceId, :final title, :final url):
          yield AssistantSourceEvent(
            AssistantSource(
              id: sourceId,
              title: title,
              uri: url,
              kind: AssistantSourceKind.url,
            ),
          );
        case AssistantSourceDocumentFrame(:final sourceId, :final title):
          yield AssistantSourceEvent(
            AssistantSource(
              id: sourceId,
              title: title,
              uri: sourceId,
              kind: AssistantSourceKind.document,
            ),
          );
        case AssistantErrorFrame(:final message):
          throw AssistantApiException(message, statusCode: 502);
        case AssistantAbortFrame(:final reason):
          throw AssistantApiException(reason, statusCode: 408);
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

  AssistantSource _sourceFromHistory(AssistantHistorySourceDto part) {
    return AssistantSource(
      id: part.sourceId,
      title: part.title,
      uri: part.uri,
      kind: part.document
          ? AssistantSourceKind.document
          : AssistantSourceKind.url,
    );
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
