import 'package:northstar/domain/models/capture_models.dart';

class CaptureDraftDto {
  const CaptureDraftDto({
    required this.kind,
    this.note,
    this.task,
    this.event,
    this.expense,
  });

  factory CaptureDraftDto.fromJson(Map<String, Object?> json) {
    final kind = CaptureKind.values.byName(
      _requiredString(json, 'kind').toLowerCase(),
    );
    return CaptureDraftDto(
      kind: kind,
      note: _map(json['note'], NoteDraftDto.fromJson),
      task: _map(json['task'], TaskDraftDto.fromJson),
      event: _map(json['event'], EventDraftDto.fromJson),
      expense: _map(json['expense'], ExpenseDraftDto.fromJson),
    );
  }

  final CaptureKind kind;
  final NoteDraftDto? note;
  final TaskDraftDto? task;
  final EventDraftDto? event;
  final ExpenseDraftDto? expense;
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
