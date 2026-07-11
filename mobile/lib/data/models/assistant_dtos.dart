class AssistantConversationDto {
  const AssistantConversationDto({
    required this.id,
    required this.title,
    required this.lastAt,
    required this.messages,
  });

  factory AssistantConversationDto.fromJson(Map<String, Object?> json) {
    return AssistantConversationDto(
      id: _requiredString(json, 'id'),
      title: _requiredString(json, 'title'),
      lastAt: DateTime.parse(_requiredString(json, 'lastAt')),
      messages: _requiredInt(json, 'messages'),
    );
  }

  final String id;
  final String title;
  final DateTime lastAt;
  final int messages;
}

class AssistantHistoryMessageDto {
  const AssistantHistoryMessageDto({
    required this.role,
    required this.text,
    required this.parts,
  });

  factory AssistantHistoryMessageDto.fromJson(Map<String, Object?> json) {
    final rawParts = json['parts'];
    final parts = <AssistantHistoryPartDto>[];
    if (rawParts is List<Object?>) {
      for (final rawPart in rawParts) {
        if (rawPart is! Map<Object?, Object?>) {
          continue;
        }
        final normalized = rawPart.map(
          (key, value) => MapEntry(key.toString(), value),
        );
        final parsed = AssistantHistoryPartDto.tryParse(normalized);
        if (parsed != null) {
          parts.add(parsed);
        }
      }
    }
    return AssistantHistoryMessageDto(
      role: _requiredString(json, 'role'),
      text: json['text'] is String ? json['text']! as String : '',
      parts: List.unmodifiable(parts),
    );
  }

  final String role;
  final String text;
  final List<AssistantHistoryPartDto> parts;
}

sealed class AssistantHistoryPartDto {
  const AssistantHistoryPartDto();

  static AssistantHistoryPartDto? tryParse(Map<String, Object?> json) {
    final type = json['type'];
    if (type == 'text' && json['text'] is String) {
      return AssistantHistoryTextDto(json['text']! as String);
    }
    if (type is String && type.startsWith('tool-')) {
      final toolCallId = json['toolCallId'];
      final state = json['state'];
      if (toolCallId is String && state is String) {
        return AssistantHistoryToolDto(
          toolCallId: toolCallId,
          toolName: type.substring('tool-'.length),
          state: state,
          errorText: json['errorText'] is String
              ? json['errorText']! as String
              : null,
        );
      }
    }
    if (type == 'source-url') {
      final sourceId = json['sourceId'];
      final url = json['url'];
      if (sourceId is String && url is String) {
        return AssistantHistorySourceDto(
          sourceId: sourceId,
          title: json['title'] is String ? json['title']! as String : url,
          uri: url,
          document: false,
        );
      }
    }
    if (type == 'source-document') {
      final sourceId = json['sourceId'];
      final title = json['title'];
      if (sourceId is String && title is String) {
        return AssistantHistorySourceDto(
          sourceId: sourceId,
          title: title,
          uri: sourceId,
          document: true,
        );
      }
    }
    return null;
  }
}

final class AssistantHistoryTextDto extends AssistantHistoryPartDto {
  const AssistantHistoryTextDto(this.text);

  final String text;
}

final class AssistantHistoryToolDto extends AssistantHistoryPartDto {
  const AssistantHistoryToolDto({
    required this.toolCallId,
    required this.toolName,
    required this.state,
    this.errorText,
  });

  final String toolCallId;
  final String toolName;
  final String state;
  final String? errorText;
}

final class AssistantHistorySourceDto extends AssistantHistoryPartDto {
  const AssistantHistorySourceDto({
    required this.sourceId,
    required this.title,
    required this.uri,
    required this.document,
  });

  final String sourceId;
  final String title;
  final String uri;
  final bool document;
}

sealed class AssistantStreamFrame {
  const AssistantStreamFrame();

  factory AssistantStreamFrame.fromJson(Map<String, Object?> json) {
    final type = _requiredString(json, 'type');
    return switch (type) {
      'start' => AssistantStartFrame(_requiredString(json, 'messageId')),
      'text-start' => AssistantTextStartFrame(_requiredString(json, 'id')),
      'text-delta' => AssistantTextDeltaFrame(
        id: _requiredString(json, 'id'),
        delta: _requiredString(json, 'delta'),
      ),
      'text-end' => AssistantTextEndFrame(_requiredString(json, 'id')),
      'tool-input-start' => AssistantToolInputStartFrame(
        toolCallId: _requiredString(json, 'toolCallId'),
        toolName: _requiredString(json, 'toolName'),
      ),
      'tool-input-available' => AssistantToolInputAvailableFrame(
        toolCallId: _requiredString(json, 'toolCallId'),
        toolName: _requiredString(json, 'toolName'),
      ),
      'tool-output-available' => AssistantToolOutputAvailableFrame(
        _requiredString(json, 'toolCallId'),
      ),
      'tool-output-error' => AssistantToolOutputErrorFrame(
        toolCallId: _requiredString(json, 'toolCallId'),
        errorText: _requiredString(json, 'errorText'),
      ),
      'source-url' => AssistantSourceUrlFrame(
        sourceId: _requiredString(json, 'sourceId'),
        url: _requiredString(json, 'url'),
        title: json['title'] is String
            ? json['title']! as String
            : _requiredString(json, 'url'),
      ),
      'source-document' => AssistantSourceDocumentFrame(
        sourceId: _requiredString(json, 'sourceId'),
        title: _requiredString(json, 'title'),
      ),
      'error' => AssistantErrorFrame(_requiredString(json, 'errorText')),
      'abort' => AssistantAbortFrame(_requiredString(json, 'reason')),
      'finish' => const AssistantFinishFrame(),
      _ => AssistantUnknownFrame(type),
    };
  }
}

final class AssistantStartFrame extends AssistantStreamFrame {
  const AssistantStartFrame(this.messageId);
  final String messageId;
}

final class AssistantTextStartFrame extends AssistantStreamFrame {
  const AssistantTextStartFrame(this.id);
  final String id;
}

final class AssistantTextDeltaFrame extends AssistantStreamFrame {
  const AssistantTextDeltaFrame({required this.id, required this.delta});
  final String id;
  final String delta;
}

final class AssistantTextEndFrame extends AssistantStreamFrame {
  const AssistantTextEndFrame(this.id);
  final String id;
}

final class AssistantToolInputStartFrame extends AssistantStreamFrame {
  const AssistantToolInputStartFrame({
    required this.toolCallId,
    required this.toolName,
  });
  final String toolCallId;
  final String toolName;
}

final class AssistantToolInputAvailableFrame extends AssistantStreamFrame {
  const AssistantToolInputAvailableFrame({
    required this.toolCallId,
    required this.toolName,
  });
  final String toolCallId;
  final String toolName;
}

final class AssistantToolOutputAvailableFrame extends AssistantStreamFrame {
  const AssistantToolOutputAvailableFrame(this.toolCallId);
  final String toolCallId;
}

final class AssistantToolOutputErrorFrame extends AssistantStreamFrame {
  const AssistantToolOutputErrorFrame({
    required this.toolCallId,
    required this.errorText,
  });

  final String toolCallId;
  final String errorText;
}

final class AssistantSourceUrlFrame extends AssistantStreamFrame {
  const AssistantSourceUrlFrame({
    required this.sourceId,
    required this.url,
    required this.title,
  });

  final String sourceId;
  final String url;
  final String title;
}

final class AssistantSourceDocumentFrame extends AssistantStreamFrame {
  const AssistantSourceDocumentFrame({
    required this.sourceId,
    required this.title,
  });

  final String sourceId;
  final String title;
}

final class AssistantErrorFrame extends AssistantStreamFrame {
  const AssistantErrorFrame(this.message);
  final String message;
}

final class AssistantAbortFrame extends AssistantStreamFrame {
  const AssistantAbortFrame(this.reason);
  final String reason;
}

final class AssistantFinishFrame extends AssistantStreamFrame {
  const AssistantFinishFrame();
}

final class AssistantDoneFrame extends AssistantStreamFrame {
  const AssistantDoneFrame();
}

final class AssistantUnknownFrame extends AssistantStreamFrame {
  const AssistantUnknownFrame(this.type);
  final String type;
}

String _requiredString(Map<String, Object?> json, String key) {
  final value = json[key];
  if (value is! String) {
    throw FormatException('Expected a string at $key.');
  }
  return value;
}

class AssistantModelSelectionDto {
  const AssistantModelSelectionDto({
    required this.gatewayId,
    required this.modelId,
  });

  factory AssistantModelSelectionDto.fromJson(Map<String, Object?> json) =>
      AssistantModelSelectionDto(
        gatewayId: _requiredString(json, 'gatewayId'),
        modelId: _requiredString(json, 'modelId'),
      );

  final String gatewayId;
  final String modelId;
}

class AssistantModelOptionDto {
  const AssistantModelOptionDto({required this.id, required this.displayName});

  factory AssistantModelOptionDto.fromJson(Map<String, Object?> json) =>
      AssistantModelOptionDto(
        id: _requiredString(json, 'id'),
        displayName: _requiredString(json, 'displayName'),
      );

  final String id;
  final String displayName;
}

int _requiredInt(Map<String, Object?> json, String key) {
  final value = json[key];
  if (value is! num) {
    throw FormatException('Expected a number at $key.');
  }
  return value.toInt();
}
