import 'package:northstar/domain/models/capture_models.dart';

class CaptureDraftDto {
  const CaptureDraftDto({
    required this.kind,
    this.note,
    this.task,
    this.event,
    this.expense,
    this.study,
    this.vocab,
  });

  factory CaptureDraftDto.fromJson(Map<String, Object?> json) {
    final rawKind = _requiredString(json, 'kind').toUpperCase();
    final kind = switch (rawKind) {
      'NOTE' => CaptureKind.note,
      'TASK' => CaptureKind.task,
      'EVENT' => CaptureKind.event,
      'EXPENSE' => CaptureKind.expense,
      'STUDY' => CaptureKind.study,
      'VOCAB' => CaptureKind.vocab,
      _ => throw UnsupportedCaptureKindException(rawKind),
    };
    return CaptureDraftDto(
      kind: kind,
      note: _map(json['note'], NoteDraftDto.fromJson),
      task: _map(json['task'], TaskDraftDto.fromJson),
      event: _map(json['event'], EventDraftDto.fromJson),
      expense: _map(json['expense'], ExpenseDraftDto.fromJson),
      study: _map(json['study'], StudyDraftDto.fromJson),
      vocab: _map(json['vocab'], VocabDraftDto.fromJson),
    );
  }

  final CaptureKind kind;
  final NoteDraftDto? note;
  final TaskDraftDto? task;
  final EventDraftDto? event;
  final ExpenseDraftDto? expense;
  final StudyDraftDto? study;
  final VocabDraftDto? vocab;
}

class UnsupportedCaptureKindException implements Exception {
  const UnsupportedCaptureKindException(this.kind);

  final String kind;

  @override
  String toString() => 'This Northstar version does not support $kind capture.';
}

class NoteDraftDto {
  const NoteDraftDto({
    required this.title,
    required this.folderPath,
    required this.contentMarkdown,
    required this.tags,
  });

  factory NoteDraftDto.fromJson(Map<String, Object?> json) {
    final rawTags = json['tags'];
    return NoteDraftDto(
      title: _optionalString(json, 'title'),
      folderPath: _optionalString(json, 'folderPath'),
      contentMarkdown: _optionalString(json, 'contentMarkdown'),
      tags: rawTags is List<Object?>
          ? rawTags.whereType<String>().toList(growable: false)
          : const [],
    );
  }

  final String title;
  final String folderPath;
  final String contentMarkdown;
  final List<String> tags;
}

class TaskDraftDto {
  const TaskDraftDto({
    required this.title,
    required this.notes,
    this.dueDate,
    this.dueTime,
    this.disciplineName,
  });

  factory TaskDraftDto.fromJson(Map<String, Object?> json) {
    return TaskDraftDto(
      title: _optionalString(json, 'title'),
      notes: _optionalString(json, 'notes'),
      dueDate: _nullableString(json, 'dueDate'),
      dueTime: _nullableString(json, 'dueTime'),
      disciplineName: _nullableString(json, 'disciplineName'),
    );
  }

  final String title;
  final String notes;
  final String? dueDate;
  final String? dueTime;
  final String? disciplineName;
}

class EventDraftDto {
  const EventDraftDto({
    required this.title,
    required this.notes,
    required this.date,
    this.startTime,
    this.endTime,
    this.disciplineName,
  });

  factory EventDraftDto.fromJson(Map<String, Object?> json) {
    return EventDraftDto(
      title: _optionalString(json, 'title'),
      notes: _optionalString(json, 'notes'),
      date: _optionalString(json, 'date'),
      startTime: _nullableString(json, 'startTime'),
      endTime: _nullableString(json, 'endTime'),
      disciplineName: _nullableString(json, 'disciplineName'),
    );
  }

  final String title;
  final String notes;
  final String date;
  final String? startTime;
  final String? endTime;
  final String? disciplineName;
}

class ExpenseDraftDto {
  const ExpenseDraftDto(this.items);

  factory ExpenseDraftDto.fromJson(Map<String, Object?> json) {
    final rawItems = json['items'];
    if (rawItems is! List<Object?>) {
      return const ExpenseDraftDto([]);
    }
    return ExpenseDraftDto(
      rawItems
          .whereType<Map<Object?, Object?>>()
          .map((item) {
            return ExpenseItemDto.fromJson(_normalize(item));
          })
          .toList(growable: false),
    );
  }

  final List<ExpenseItemDto> items;
}

class ExpenseItemDto {
  const ExpenseItemDto({
    required this.type,
    required this.amount,
    required this.occurredOn,
    required this.description,
    required this.category,
    required this.exceptional,
  });

  factory ExpenseItemDto.fromJson(Map<String, Object?> json) {
    final rawType = _nullableString(json, 'type') ?? 'EXPENSE';
    final rawAmount = json['amount'];
    return ExpenseItemDto(
      type: rawType == 'INCOME'
          ? CaptureTransactionType.income
          : CaptureTransactionType.expense,
      amount: rawAmount is num ? rawAmount.toInt() : 0,
      occurredOn: _optionalString(json, 'occurredOn'),
      description: _optionalString(json, 'description'),
      category: _optionalString(json, 'category'),
      exceptional: json['exceptional'] == true,
    );
  }

  final CaptureTransactionType type;
  final int amount;
  final String occurredOn;
  final String description;
  final String category;
  final bool exceptional;
}

class StudyDraftDto {
  const StudyDraftDto(this.items);

  factory StudyDraftDto.fromJson(Map<String, Object?> json) {
    return StudyDraftDto(_objectList(json, 'items', StudyItemDto.fromJson));
  }

  final List<StudyItemDto> items;
}

class StudyItemDto {
  const StudyItemDto({
    required this.skill,
    required this.kind,
    required this.durationMinutes,
    required this.scoreRaw,
    required this.scoreMax,
    required this.notes,
    required this.occurredOn,
    required this.disciplineName,
  });

  factory StudyItemDto.fromJson(Map<String, Object?> json) {
    return StudyItemDto(
      skill: _optionalString(json, 'skill'),
      kind: _optionalString(json, 'kind'),
      durationMinutes: _optionalString(json, 'durationMinutes'),
      scoreRaw: _optionalString(json, 'scoreRaw'),
      scoreMax: _optionalString(json, 'scoreMax'),
      notes: _optionalString(json, 'notes'),
      occurredOn: _optionalString(json, 'occurredOn'),
      disciplineName: _optionalString(json, 'disciplineName'),
    );
  }

  final String skill;
  final String kind;
  final String durationMinutes;
  final String scoreRaw;
  final String scoreMax;
  final String notes;
  final String occurredOn;
  final String disciplineName;
}

class VocabDraftDto {
  const VocabDraftDto(this.items);

  factory VocabDraftDto.fromJson(Map<String, Object?> json) {
    return VocabDraftDto(_objectList(json, 'items', VocabItemDto.fromJson));
  }

  final List<VocabItemDto> items;
}

class VocabItemDto {
  const VocabItemDto({
    required this.front,
    required this.back,
    required this.reading,
    required this.partOfSpeech,
    required this.example,
    required this.language,
    required this.deck,
    required this.disciplineName,
  });

  factory VocabItemDto.fromJson(Map<String, Object?> json) {
    return VocabItemDto(
      front: _optionalString(json, 'front'),
      back: _optionalString(json, 'back'),
      reading: _optionalString(json, 'reading'),
      partOfSpeech: _optionalString(json, 'partOfSpeech'),
      example: _optionalString(json, 'example'),
      language: _optionalString(json, 'language'),
      deck: _optionalString(json, 'deck'),
      disciplineName: _optionalString(json, 'disciplineName'),
    );
  }

  final String front;
  final String back;
  final String reading;
  final String partOfSpeech;
  final String example;
  final String language;
  final String deck;
  final String disciplineName;
}

List<T> _objectList<T>(
  Map<String, Object?> json,
  String key,
  T Function(Map<String, Object?>) fromJson,
) {
  final raw = json[key];
  if (raw is! List<Object?>) {
    return const [];
  }
  return raw
      .whereType<Map<Object?, Object?>>()
      .map((item) => fromJson(_normalize(item)))
      .toList(growable: false);
}

T? _map<T>(Object? value, T Function(Map<String, Object?>) fromJson) {
  if (value is! Map<Object?, Object?>) {
    return null;
  }
  return fromJson(_normalize(value));
}

Map<String, Object?> _normalize(Map<Object?, Object?> source) {
  return source.map((key, value) => MapEntry(key.toString(), value));
}

String _requiredString(Map<String, Object?> json, String key) {
  final value = json[key];
  if (value is! String || value.trim().isEmpty) {
    throw FormatException('Expected a non-empty string at $key.');
  }
  return value;
}

String _optionalString(Map<String, Object?> json, String key) {
  return _nullableString(json, key) ?? '';
}

String? _nullableString(Map<String, Object?> json, String key) {
  final value = json[key];
  if (value is! String || value.trim().isEmpty) {
    return null;
  }
  return value.trim();
}
