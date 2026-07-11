enum AssistantRole { user, assistant }

enum AssistantMessageStatus { complete, streaming, failed, stopped }

enum AssistantToolStatus { preparing, running, complete, failed }

class AssistantModelSelection {
  const AssistantModelSelection({
    required this.gatewayId,
    required this.modelId,
  });

  final String gatewayId;
  final String modelId;
}

class AssistantModelOption {
  const AssistantModelOption({required this.id, required this.displayName});

  final String id;
  final String displayName;
}

class AssistantConversation {
  const AssistantConversation({
    required this.id,
    required this.title,
    required this.lastAt,
    required this.messageCount,
  });

  final String id;
  final String title;
  final DateTime lastAt;
  final int messageCount;
}

class AssistantToolActivity {
  const AssistantToolActivity({
    required this.id,
    required this.name,
    required this.status,
  });

  final String id;
  final String name;
  final AssistantToolStatus status;

  AssistantToolActivity copyWith({AssistantToolStatus? status}) {
    return AssistantToolActivity(
      id: id,
      name: name,
      status: status ?? this.status,
    );
  }
}

class AssistantMessage {
  const AssistantMessage({
    required this.id,
    required this.role,
    required this.text,
    this.tools = const [],
    this.status = AssistantMessageStatus.complete,
    this.errorMessage,
  });

  final String id;
  final AssistantRole role;
  final String text;
  final List<AssistantToolActivity> tools;
  final AssistantMessageStatus status;
  final String? errorMessage;

  bool get hasVisibleOutput =>
      text.trim().isNotEmpty || tools.isNotEmpty || errorMessage != null;

  AssistantMessage copyWith({
    String? text,
    List<AssistantToolActivity>? tools,
    AssistantMessageStatus? status,
    String? errorMessage,
  }) {
    return AssistantMessage(
      id: id,
      role: role,
      text: text ?? this.text,
      tools: tools ?? this.tools,
      status: status ?? this.status,
      errorMessage: errorMessage ?? this.errorMessage,
    );
  }
}

sealed class AssistantTurnEvent {
  const AssistantTurnEvent();
}

final class AssistantTextDeltaEvent extends AssistantTurnEvent {
  const AssistantTextDeltaEvent(this.delta);
  final String delta;
}

final class AssistantToolEvent extends AssistantTurnEvent {
  const AssistantToolEvent({required this.id, required this.status, this.name});

  final String id;
  final String? name;
  final AssistantToolStatus status;
}

final class AssistantTurnFinishedEvent extends AssistantTurnEvent {
  const AssistantTurnFinishedEvent();
}
