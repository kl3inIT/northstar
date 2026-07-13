import 'dart:convert';

import 'package:northstar/data/models/capture_dtos.dart';
import 'package:northstar/data/services/capture_api.dart';
import 'package:northstar/domain/models/capture_models.dart';

abstract interface class CaptureRepository {
  Future<CaptureDraft> draftText({
    required String text,
    CaptureKind? forcedKind,
  });

  Future<CaptureDraft> draftReceipt(ReceiptUpload upload);

  Future<SavedCapture> save(CaptureDraft draft);

  Future<void> undo(SavedCapture saved);
}

class RemoteCaptureRepository implements CaptureRepository {
  const RemoteCaptureRepository(this._api);

  final CaptureDataSource _api;

  @override
  Future<CaptureDraft> draftText({
    required String text,
    CaptureKind? forcedKind,
  }) async {
    final dto = await _api.draftText(text: text, forcedKind: forcedKind);
    return _mapDraft(dto, fallbackText: text);
  }

  @override
  Future<CaptureDraft> draftReceipt(ReceiptUpload upload) async {
    final dto = await _api.draftReceipt(upload);
    if (dto.kind != CaptureKind.expense) {
      throw const FormatException('A receipt must produce an expense draft.');
    }
    return _mapDraft(dto, fallbackText: 'Receipt');
  }

  @override
  Future<SavedCapture> save(CaptureDraft draft) async {
    return switch (draft) {
      NoteCaptureDraft() => _saveNote(draft),
      TaskCaptureDraft() => _saveTask(draft),
      EventCaptureDraft() => _saveEvent(draft),
      ExpenseCaptureDraft() => _saveExpense(draft),
      StudyCaptureDraft() => _saveStudy(draft),
      VocabCaptureDraft() => _saveVocab(draft),
    };
  }

  @override
  Future<void> undo(SavedCapture saved) async {
    final prefix = switch (saved.kind) {
      CaptureKind.note => '/api/notes',
      CaptureKind.task => '/api/tasks',
      CaptureKind.event => '/api/calendar/events',
      CaptureKind.expense => '/api/finance',
      CaptureKind.study => '/api/study/sessions',
      CaptureKind.vocab => '/api/study/vocab',
    };
    for (final id in saved.ids) {
      await _api.delete('$prefix/${Uri.encodeComponent(id)}');
    }
  }

  CaptureDraft _mapDraft(CaptureDraftDto dto, {required String fallbackText}) {
    return switch (dto.kind) {
      CaptureKind.note => _noteDraft(dto.note, fallbackText),
      CaptureKind.task => _taskDraft(dto.task, fallbackText),
      CaptureKind.event => _eventDraft(dto.event, fallbackText),
      CaptureKind.expense => _expenseDraft(dto.expense),
      CaptureKind.study => _studyDraft(dto.study),
      CaptureKind.vocab => _vocabDraft(dto.vocab),
    };
  }

  NoteCaptureDraft _noteDraft(NoteDraftDto? dto, String fallbackText) {
    if (dto == null) {
      throw const FormatException('The note draft is missing.');
    }
    return NoteCaptureDraft(
      title: _fallback(dto.title, _truncate(fallbackText, 80)),
      folderPath: dto.folderPath,
      contentMarkdown: _fallback(dto.contentMarkdown, fallbackText),
      tags: List.unmodifiable(dto.tags.take(4)),
    );
  }

  TaskCaptureDraft _taskDraft(TaskDraftDto? dto, String fallbackText) {
    if (dto == null) {
      throw const FormatException('The task draft is missing.');
    }
    return TaskCaptureDraft(
      title: _fallback(dto.title, _truncate(fallbackText, 160)),
      notes: dto.notes,
      dueDate: _dateOrNull(dto.dueDate),
      dueTime: _timeOrNull(dto.dueTime),
      disciplineName: dto.disciplineName,
    );
  }

  EventCaptureDraft _eventDraft(EventDraftDto? dto, String fallbackText) {
    if (dto == null) {
      throw const FormatException('The event draft is missing.');
    }
    return EventCaptureDraft(
      title: _fallback(dto.title, _truncate(fallbackText, 160)),
      notes: dto.notes,
      date: _dateOrNull(dto.date) ?? _today(),
      startTime: _timeOrNull(dto.startTime),
      endTime: _timeOrNull(dto.endTime),
      disciplineName: dto.disciplineName,
    );
  }

  ExpenseCaptureDraft _expenseDraft(ExpenseDraftDto? dto) {
    if (dto == null) {
      throw const FormatException('The expense draft is missing.');
    }
    final items = dto.items
        .map((item) {
          return ExpenseCaptureItem(
            type: item.type,
            amount: item.amount,
            occurredOn: _dateOrNull(item.occurredOn) ?? _today(),
            description: _fallback(item.description, 'Expense'),
            category: item.category,
            exceptional: item.exceptional,
          );
        })
        .where((item) => item.amount > 0)
        .toList(growable: false);
    if (items.isEmpty) {
      throw const FormatException(
        'No valid positive amount was found in this expense draft.',
      );
    }
    return ExpenseCaptureDraft(List.unmodifiable(items));
  }

  StudyCaptureDraft _studyDraft(StudyDraftDto? dto) {
    if (dto == null) {
      throw const FormatException('The study draft is missing.');
    }
    final items = dto.items
        .map((item) {
          final raw = _nonNegativeIntOrNull(item.scoreRaw);
          final max = _positiveIntOrNull(item.scoreMax);
          return StudyCaptureItem(
            skill: _fallback(item.skill, 'Other'),
            kind: item.kind.toUpperCase() == 'MOCK'
                ? StudyCaptureKind.mock
                : StudyCaptureKind.practice,
            occurredOn: _dateOrNull(item.occurredOn) ?? _today(),
            notes: item.notes.trim(),
            durationMinutes: _positiveIntOrNull(item.durationMinutes),
            scoreRaw: raw,
            scoreMax: max,
            disciplineName: _textOrNull(item.disciplineName),
          );
        })
        .toList(growable: false);
    if (items.isEmpty) {
      throw const FormatException('The study draft has no activities.');
    }
    return StudyCaptureDraft(List.unmodifiable(items));
  }

  VocabCaptureDraft _vocabDraft(VocabDraftDto? dto) {
    if (dto == null) {
      throw const FormatException('The vocabulary draft is missing.');
    }
    final items = dto.items
        .map((item) {
          final language = switch (item.language.toUpperCase()) {
            'CHINESE' => VocabCaptureLanguage.chinese,
            'ENGLISH' => VocabCaptureLanguage.english,
            _ =>
              _containsHan(item.front)
                  ? VocabCaptureLanguage.chinese
                  : VocabCaptureLanguage.english,
          };
          return VocabCaptureItem(
            front: _fallback(item.front, '(word)'),
            back: _fallback(item.back, '(meaning)'),
            reading: item.reading.trim(),
            partOfSpeech: item.partOfSpeech.trim(),
            example: item.example.trim(),
            language: language,
            deck: item.deck.trim(),
            disciplineName: _textOrNull(item.disciplineName),
          );
        })
        .toList(growable: false);
    if (items.isEmpty) {
      throw const FormatException('The vocabulary draft has no cards.');
    }
    return VocabCaptureDraft(List.unmodifiable(items));
  }

  Future<SavedCapture> _saveNote(NoteCaptureDraft draft) async {
    _requireText(draft.title, 'Note title');
    _requireText(draft.contentMarkdown, 'Note content');
    final created = await _api.createNote({
      'title': draft.title.trim(),
      'folderPath': draft.folderPath.trim(),
      'contentMarkdown': draft.contentMarkdown,
      'tags': draft.tags,
      'status': 'STAGING',
    });
    return SavedCapture(
      kind: CaptureKind.note,
      ids: [_requiredId(created)],
      title: _responseTitle(created, draft.title),
    );
  }

  Future<SavedCapture> _saveTask(TaskCaptureDraft draft) async {
    _requireText(draft.title, 'Task title');
    final disciplineId = await _disciplineId(draft.disciplineName);
    final created = await _api.createTask({
      'title': draft.title.trim(),
      if (draft.notes.trim().isNotEmpty) 'notes': draft.notes.trim(),
      'dueDate': ?draft.dueDate,
      'dueTime': ?draft.dueTime,
      'disciplineId': ?disciplineId,
    });
    return SavedCapture(
      kind: CaptureKind.task,
      ids: [_requiredId(created)],
      title: _responseTitle(created, draft.title),
    );
  }

  Future<SavedCapture> _saveEvent(EventCaptureDraft draft) async {
    _requireText(draft.title, 'Event title');
    final date = DateTime.parse(draft.date);
    final allDay = draft.startTime == null;
    final start = allDay
        ? DateTime(date.year, date.month, date.day)
        : _localDateTime(date, draft.startTime!);
    var end = allDay
        ? DateTime(date.year, date.month, date.day, 23, 59)
        : draft.endTime == null
        ? start.add(const Duration(hours: 1))
        : _localDateTime(date, draft.endTime!);
    if (!end.isAfter(start)) {
      end = end.add(const Duration(days: 1));
    }
    final disciplineId = await _disciplineId(draft.disciplineName);
    final created = await _api.createEvent({
      'title': draft.title.trim(),
      if (draft.notes.trim().isNotEmpty) 'notes': draft.notes.trim(),
      'startAt': start.toUtc().toIso8601String(),
      'endAt': end.toUtc().toIso8601String(),
      'allDay': allDay,
      'color': 'BLUE',
      'disciplineId': ?disciplineId,
    });
    return SavedCapture(
      kind: CaptureKind.event,
      ids: [_requiredId(created)],
      title: _responseTitle(created, draft.title),
    );
  }

  Future<SavedCapture> _saveExpense(ExpenseCaptureDraft draft) async {
    if (draft.items.isEmpty) {
      throw const FormatException('Add at least one expense item.');
    }
    for (final item in draft.items) {
      if (item.amount <= 0) {
        throw const FormatException('Expense amounts must be positive.');
      }
      _requireText(item.description, 'Expense description');
    }
    final created = await _api.createTransactions({
      'items': [
        for (final item in draft.items)
          {
            'type': item.type.name.toUpperCase(),
            'amount': item.amount,
            'occurredOn': item.occurredOn,
            'description': item.description.trim(),
            if (item.category.trim().isNotEmpty)
              'category': item.category.trim(),
            'exceptional': item.exceptional,
          },
      ],
    });
    final ids = created.map(_requiredId).toList(growable: false);
    if (ids.isEmpty) {
      throw const FormatException('No expense was saved.');
    }
    return SavedCapture(
      kind: CaptureKind.expense,
      ids: ids,
      title: draft.title,
    );
  }

  Future<SavedCapture> _saveStudy(StudyCaptureDraft draft) async {
    if (draft.items.isEmpty) {
      throw const FormatException('Add at least one study activity.');
    }
    final items = <Map<String, Object?>>[];
    for (final item in draft.items) {
      _requireText(item.skill, 'Study skill');
      if (_dateOrNull(item.occurredOn) == null) {
        throw const FormatException('Study date must use yyyy-MM-dd.');
      }
      if (item.durationMinutes case final minutes?) {
        if (minutes <= 0 || minutes > 1440) {
          throw const FormatException(
            'Study duration must be between 1 and 1,440 minutes.',
          );
        }
      }
      final raw = item.scoreRaw;
      final max = item.scoreMax;
      if ((raw == null) != (max == null) ||
          (raw != null && max != null && (raw < 0 || max <= 0 || raw > max))) {
        throw const FormatException(
          'Study score needs a valid raw and maximum value.',
        );
      }
      final disciplineId = await _disciplineId(item.disciplineName);
      items.add({
        'occurredOn': item.occurredOn,
        'skill': item.skill.trim(),
        'kind': item.kind.name.toUpperCase(),
        'durationMinutes': ?item.durationMinutes,
        'scoreRaw': ?raw,
        'scoreMax': ?max,
        if (item.notes.trim().isNotEmpty) 'notes': item.notes.trim(),
        'disciplineId': ?disciplineId,
      });
    }
    final created = await _api.createStudySessions({'items': items});
    final ids = created.map(_requiredId).toList(growable: false);
    if (ids.isEmpty) {
      throw const FormatException('No study activity was saved.');
    }
    return SavedCapture(kind: CaptureKind.study, ids: ids, title: draft.title);
  }

  Future<SavedCapture> _saveVocab(VocabCaptureDraft draft) async {
    if (draft.items.isEmpty) {
      throw const FormatException('Add at least one vocabulary card.');
    }
    final items = <Map<String, Object?>>[];
    for (final item in draft.items) {
      _requireText(item.front, 'Vocabulary word');
      _requireText(item.back, 'Vocabulary meaning');
      final metadata = <String, String>{
        if (item.reading.trim().isNotEmpty) 'reading': item.reading.trim(),
        if (item.partOfSpeech.trim().isNotEmpty)
          'partOfSpeech': item.partOfSpeech.trim(),
        if (item.example.trim().isNotEmpty) 'example': item.example.trim(),
      };
      final disciplineId = await _disciplineId(item.disciplineName);
      items.add({
        'front': item.front.trim(),
        'back': item.back.trim(),
        if (metadata.isNotEmpty) 'metadata': jsonEncode(metadata),
        'language': item.language.name.toUpperCase(),
        if (item.deck.trim().isNotEmpty) 'deck': item.deck.trim(),
        'disciplineId': ?disciplineId,
      });
    }
    final created = await _api.createVocabCards({'items': items});
    final ids = created.map(_requiredId).toList(growable: false);
    if (ids.isEmpty) {
      throw const FormatException('No vocabulary card was saved.');
    }
    return SavedCapture(kind: CaptureKind.vocab, ids: ids, title: draft.title);
  }

  Future<String?> _disciplineId(String? name) async {
    if (name == null || name.trim().isEmpty) {
      return null;
    }
    final disciplines = await _api.listDisciplines();
    for (final discipline in disciplines) {
      if (discipline['name'] == name && discipline['id'] is String) {
        return discipline['id']! as String;
      }
    }
    return null;
  }

  String _requiredId(Map<String, Object?> json) {
    final id = json['id'];
    if (id is! String || id.isEmpty) {
      throw const FormatException('Saved capture is missing its id.');
    }
    return id;
  }

  String _responseTitle(Map<String, Object?> json, String fallback) {
    final title = json['title'];
    return title is String && title.trim().isNotEmpty ? title : fallback;
  }

  DateTime _localDateTime(DateTime date, String time) {
    final parts = time.split(':').map(int.parse).toList(growable: false);
    return DateTime(date.year, date.month, date.day, parts[0], parts[1]);
  }

  String _today() {
    final now = DateTime.now();
    return '${now.year.toString().padLeft(4, '0')}-'
        '${now.month.toString().padLeft(2, '0')}-'
        '${now.day.toString().padLeft(2, '0')}';
  }

  String? _dateOrNull(String? value) {
    if (value == null || value.isEmpty) {
      return null;
    }
    final parsed = DateTime.tryParse(value);
    if (parsed == null || value.length < 10) {
      return null;
    }
    return value.substring(0, 10);
  }

  String? _timeOrNull(String? value) {
    if (value == null || !RegExp(r'^\d{2}:\d{2}').hasMatch(value)) {
      return null;
    }
    final parts = value.substring(0, 5).split(':').map(int.tryParse).toList();
    if (parts.any((part) => part == null) || parts[0]! > 23 || parts[1]! > 59) {
      return null;
    }
    return value.substring(0, 5);
  }

  int? _positiveIntOrNull(String value) {
    final parsed = int.tryParse(value.trim());
    return parsed != null && parsed > 0 ? parsed : null;
  }

  int? _nonNegativeIntOrNull(String value) {
    final parsed = int.tryParse(value.trim());
    return parsed != null && parsed >= 0 ? parsed : null;
  }

  String? _textOrNull(String value) {
    final trimmed = value.trim();
    return trimmed.isEmpty ? null : trimmed;
  }

  bool _containsHan(String value) {
    return RegExp(r'[\u3400-\u4DBF\u4E00-\u9FFF]').hasMatch(value);
  }

  String _fallback(String value, String fallback) {
    return value.trim().isEmpty ? fallback.trim() : value.trim();
  }

  String _truncate(String value, int length) {
    final trimmed = value.trim();
    return trimmed.length <= length ? trimmed : trimmed.substring(0, length);
  }

  void _requireText(String value, String field) {
    if (value.trim().isEmpty) {
      throw FormatException('$field is required.');
    }
  }
}
