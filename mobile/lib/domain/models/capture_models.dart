enum CaptureKind { note, task, event, expense }

enum ReceiptSource { camera, photoLibrary }

sealed class CaptureDraft {
  const CaptureDraft();

  CaptureKind get kind;
  String get title;
}

final class NoteCaptureDraft extends CaptureDraft {
  const NoteCaptureDraft({
    required this.title,
    required this.folderPath,
    required this.contentMarkdown,
    required this.tags,
  });

  @override
  final String title;
  final String folderPath;
  final String contentMarkdown;
  final List<String> tags;

  @override
  CaptureKind get kind => CaptureKind.note;

  NoteCaptureDraft copyWith({
    String? title,
    String? folderPath,
    String? contentMarkdown,
    List<String>? tags,
  }) {
    return NoteCaptureDraft(
      title: title ?? this.title,
      folderPath: folderPath ?? this.folderPath,
      contentMarkdown: contentMarkdown ?? this.contentMarkdown,
      tags: tags ?? this.tags,
    );
  }
}

final class TaskCaptureDraft extends CaptureDraft {
  const TaskCaptureDraft({
    required this.title,
    required this.notes,
    this.dueDate,
    this.dueTime,
    this.disciplineName,
  });

  @override
  final String title;
  final String notes;
  final String? dueDate;
  final String? dueTime;
  final String? disciplineName;

  @override
  CaptureKind get kind => CaptureKind.task;

  TaskCaptureDraft copyWith({
    String? title,
    String? notes,
    String? dueDate,
    String? dueTime,
    String? disciplineName,
  }) {
    return TaskCaptureDraft(
      title: title ?? this.title,
      notes: notes ?? this.notes,
      dueDate: dueDate ?? this.dueDate,
      dueTime: dueTime ?? this.dueTime,
      disciplineName: disciplineName ?? this.disciplineName,
    );
  }
}

final class EventCaptureDraft extends CaptureDraft {
  const EventCaptureDraft({
    required this.title,
    required this.notes,
    required this.date,
    this.startTime,
    this.endTime,
    this.disciplineName,
  });

  @override
  final String title;
  final String notes;
  final String date;
  final String? startTime;
  final String? endTime;
  final String? disciplineName;

  @override
  CaptureKind get kind => CaptureKind.event;

  EventCaptureDraft copyWith({
    String? title,
    String? notes,
    String? date,
    String? startTime,
    String? endTime,
    String? disciplineName,
  }) {
    return EventCaptureDraft(
      title: title ?? this.title,
      notes: notes ?? this.notes,
      date: date ?? this.date,
      startTime: startTime ?? this.startTime,
      endTime: endTime ?? this.endTime,
      disciplineName: disciplineName ?? this.disciplineName,
    );
  }
}

enum CaptureTransactionType { expense, income }

class ExpenseCaptureItem {
  const ExpenseCaptureItem({
    required this.type,
    required this.amount,
    required this.occurredOn,
    required this.description,
    required this.category,
    required this.exceptional,
  });

  final CaptureTransactionType type;
  final int amount;
  final String occurredOn;
  final String description;
  final String category;
  final bool exceptional;

  ExpenseCaptureItem copyWith({
    CaptureTransactionType? type,
    int? amount,
    String? occurredOn,
    String? description,
    String? category,
    bool? exceptional,
  }) {
    return ExpenseCaptureItem(
      type: type ?? this.type,
      amount: amount ?? this.amount,
      occurredOn: occurredOn ?? this.occurredOn,
      description: description ?? this.description,
      category: category ?? this.category,
      exceptional: exceptional ?? this.exceptional,
    );
  }
}

final class ExpenseCaptureDraft extends CaptureDraft {
  const ExpenseCaptureDraft(this.items);

  final List<ExpenseCaptureItem> items;

  @override
  CaptureKind get kind => CaptureKind.expense;

  @override
  String get title {
    if (items.isEmpty) {
      return 'Expense';
    }
    return items.map((item) => item.description).join(', ');
  }

  ExpenseCaptureDraft copyWithItem(int index, ExpenseCaptureItem item) {
    final next = items.toList();
    next[index] = item;
    return ExpenseCaptureDraft(List.unmodifiable(next));
  }
}

class SavedCapture {
  const SavedCapture({
    required this.kind,
    required this.ids,
    required this.title,
  });

  final CaptureKind kind;
  final List<String> ids;
  final String title;
}

class ReceiptUpload {
  const ReceiptUpload({
    required this.bytes,
    required this.filename,
    required this.mimeType,
  });

  final List<int> bytes;
  final String filename;
  final String mimeType;
}
