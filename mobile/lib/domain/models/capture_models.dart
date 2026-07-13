enum CaptureKind { note, task, event, expense, study, vocab }

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

enum StudyCaptureKind { practice, mock }

class StudyCaptureItem {
  const StudyCaptureItem({
    required this.skill,
    required this.kind,
    required this.occurredOn,
    required this.notes,
    this.durationMinutes,
    this.scoreRaw,
    this.scoreMax,
    this.disciplineName,
  });

  final String skill;
  final StudyCaptureKind kind;
  final String occurredOn;
  final String notes;
  final int? durationMinutes;
  final int? scoreRaw;
  final int? scoreMax;
  final String? disciplineName;

  StudyCaptureItem copyWith({
    String? skill,
    StudyCaptureKind? kind,
    String? occurredOn,
    String? notes,
    int? durationMinutes,
    int? scoreRaw,
    int? scoreMax,
    String? disciplineName,
  }) {
    return StudyCaptureItem(
      skill: skill ?? this.skill,
      kind: kind ?? this.kind,
      occurredOn: occurredOn ?? this.occurredOn,
      notes: notes ?? this.notes,
      durationMinutes: durationMinutes ?? this.durationMinutes,
      scoreRaw: scoreRaw ?? this.scoreRaw,
      scoreMax: scoreMax ?? this.scoreMax,
      disciplineName: disciplineName ?? this.disciplineName,
    );
  }
}

final class StudyCaptureDraft extends CaptureDraft {
  const StudyCaptureDraft(this.items);

  final List<StudyCaptureItem> items;

  @override
  CaptureKind get kind => CaptureKind.study;

  @override
  String get title {
    if (items.isEmpty) {
      return 'Study';
    }
    return items
        .map((item) {
          final details = <String>[
            item.skill,
            if (item.durationMinutes case final minutes?) '${minutes}m',
            if (item.scoreRaw case final raw?)
              if (item.scoreMax case final max?) '$raw/$max',
          ];
          return details.join(' · ');
        })
        .join('  |  ');
  }

  StudyCaptureDraft copyWithItem(int index, StudyCaptureItem item) {
    final next = items.toList();
    next[index] = item;
    return StudyCaptureDraft(List.unmodifiable(next));
  }
}

enum VocabCaptureLanguage { english, chinese }

class VocabCaptureItem {
  const VocabCaptureItem({
    required this.front,
    required this.back,
    required this.reading,
    required this.partOfSpeech,
    required this.example,
    required this.language,
    required this.deck,
    this.disciplineName,
  });

  final String front;
  final String back;
  final String reading;
  final String partOfSpeech;
  final String example;
  final VocabCaptureLanguage language;
  final String deck;
  final String? disciplineName;

  VocabCaptureItem copyWith({
    String? front,
    String? back,
    String? reading,
    String? partOfSpeech,
    String? example,
    VocabCaptureLanguage? language,
    String? deck,
    String? disciplineName,
  }) {
    return VocabCaptureItem(
      front: front ?? this.front,
      back: back ?? this.back,
      reading: reading ?? this.reading,
      partOfSpeech: partOfSpeech ?? this.partOfSpeech,
      example: example ?? this.example,
      language: language ?? this.language,
      deck: deck ?? this.deck,
      disciplineName: disciplineName ?? this.disciplineName,
    );
  }
}

final class VocabCaptureDraft extends CaptureDraft {
  const VocabCaptureDraft(this.items);

  final List<VocabCaptureItem> items;

  @override
  CaptureKind get kind => CaptureKind.vocab;

  @override
  String get title {
    if (items.isEmpty) {
      return 'Vocabulary';
    }
    return items.map((item) => '${item.front} · ${item.back}').join('  |  ');
  }

  VocabCaptureDraft copyWithItem(int index, VocabCaptureItem item) {
    final next = items.toList();
    next[index] = item;
    return VocabCaptureDraft(List.unmodifiable(next));
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
