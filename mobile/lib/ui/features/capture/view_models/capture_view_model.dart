import 'package:flutter/foundation.dart';
import 'package:northstar/data/repositories/capture_repository.dart';
import 'package:northstar/data/services/receipt_picker.dart';
import 'package:northstar/domain/models/capture_models.dart';

enum CapturePhase { input, drafting, review, saving, saved, undoing, undone }

enum _CaptureIntent { draftText, draftReceipt, save, undo }

class CaptureViewModel extends ChangeNotifier {
  factory CaptureViewModel({
    required CaptureRepository repository,
    required ReceiptSourcePicker receiptPicker,
  }) {
    return CaptureViewModel._(repository, receiptPicker);
  }

  CaptureViewModel._(this._repository, this._receiptPicker);

  final CaptureRepository _repository;
  final ReceiptSourcePicker _receiptPicker;

  CapturePhase _phase = CapturePhase.input;
  CaptureKind? _forcedKind;
  CaptureDraft? _draft;
  SavedCapture? _saved;
  String _text = '';
  String? _errorMessage;
  ReceiptUpload? _lastReceipt;
  _CaptureIntent? _lastIntent;
  int _draftVersion = 0;

  CapturePhase get phase => _phase;
  CaptureKind? get forcedKind => _forcedKind;
  CaptureDraft? get draft => _draft;
  SavedCapture? get saved => _saved;
  String get text => _text;
  String? get errorMessage => _errorMessage;
  int get draftVersion => _draftVersion;
  bool get isBusy => switch (_phase) {
    CapturePhase.drafting ||
    CapturePhase.saving ||
    CapturePhase.undoing => true,
    _ => false,
  };

  void setText(String value) {
    _text = value;
    if (_errorMessage != null) {
      _errorMessage = null;
      notifyListeners();
    }
  }

  void setForcedKind(CaptureKind? kind) {
    if (isBusy) {
      return;
    }
    _forcedKind = _forcedKind == kind ? null : kind;
    notifyListeners();
  }

  Future<void> draftText() async {
    final trimmed = _text.trim();
    if (trimmed.isEmpty) {
      _errorMessage = 'Enter something to capture.';
      notifyListeners();
      return;
    }
    if (trimmed.length > 20000) {
      _errorMessage = 'Capture text must be 20,000 characters or fewer.';
      notifyListeners();
      return;
    }
    _lastIntent = _CaptureIntent.draftText;
    await _runDraft(
      () => _repository.draftText(text: trimmed, forcedKind: _forcedKind),
    );
  }

  Future<void> draftReceipt(ReceiptSource source) async {
    _errorMessage = null;
    notifyListeners();
    try {
      final upload = await _receiptPicker.pick(source);
      if (upload == null) {
        return;
      }
      _lastReceipt = upload;
      _lastIntent = _CaptureIntent.draftReceipt;
      await _runDraft(() => _repository.draftReceipt(upload));
    } on Object catch (error) {
      _phase = CapturePhase.input;
      _errorMessage = _messageFor(error);
      notifyListeners();
    }
  }

  Future<void> save() async {
    final current = _draft;
    if (current == null || isBusy) {
      return;
    }
    _phase = CapturePhase.saving;
    _errorMessage = null;
    _lastIntent = _CaptureIntent.save;
    notifyListeners();
    try {
      _saved = await _repository.save(current);
      _phase = CapturePhase.saved;
    } on Object catch (error) {
      _phase = CapturePhase.review;
      _errorMessage = _messageFor(error);
    }
    notifyListeners();
  }

  Future<void> undo() async {
    final current = _saved;
    if (current == null || isBusy || _phase == CapturePhase.undone) {
      return;
    }
    _phase = CapturePhase.undoing;
    _errorMessage = null;
    _lastIntent = _CaptureIntent.undo;
    notifyListeners();
    try {
      await _repository.undo(current);
      _phase = CapturePhase.undone;
    } on Object catch (error) {
      _phase = CapturePhase.saved;
      _errorMessage = _messageFor(error);
    }
    notifyListeners();
  }

  Future<void> retry() async {
    if (isBusy) {
      return;
    }
    switch (_lastIntent) {
      case _CaptureIntent.draftText:
        await draftText();
      case _CaptureIntent.draftReceipt:
        final receipt = _lastReceipt;
        if (receipt != null) {
          await _runDraft(() => _repository.draftReceipt(receipt));
        }
      case _CaptureIntent.save:
        await save();
      case _CaptureIntent.undo:
        await undo();
      case null:
        return;
    }
  }

  void editInput() {
    if (isBusy) {
      return;
    }
    _draft = null;
    _saved = null;
    _phase = CapturePhase.input;
    _errorMessage = null;
    notifyListeners();
  }

  void captureAnother() {
    if (isBusy) {
      return;
    }
    _text = '';
    _forcedKind = null;
    _draft = null;
    _saved = null;
    _lastReceipt = null;
    _lastIntent = null;
    _phase = CapturePhase.input;
    _errorMessage = null;
    notifyListeners();
  }

  void updateNote({
    String? title,
    String? folderPath,
    String? contentMarkdown,
  }) {
    final current = _draft;
    if (current is! NoteCaptureDraft) {
      return;
    }
    _draft = current.copyWith(
      title: title,
      folderPath: folderPath,
      contentMarkdown: contentMarkdown,
    );
    notifyListeners();
  }

  void updateTask({String? title, String? notes}) {
    final current = _draft;
    if (current is! TaskCaptureDraft) {
      return;
    }
    _draft = current.copyWith(title: title, notes: notes);
    notifyListeners();
  }

  void updateEvent({
    String? title,
    String? notes,
    String? date,
    String? startTime,
    String? endTime,
  }) {
    final current = _draft;
    if (current is! EventCaptureDraft) {
      return;
    }
    _draft = EventCaptureDraft(
      title: title ?? current.title,
      notes: notes ?? current.notes,
      date: date ?? current.date,
      startTime: _nullableText(startTime, current.startTime),
      endTime: _nullableText(endTime, current.endTime),
      disciplineName: current.disciplineName,
    );
    notifyListeners();
  }

  void updateExpenseItem(int index, ExpenseCaptureItem item) {
    final current = _draft;
    if (current is! ExpenseCaptureDraft ||
        index < 0 ||
        index >= current.items.length) {
      return;
    }
    _draft = current.copyWithItem(index, item);
    notifyListeners();
  }

  void updateStudyItem(int index, StudyCaptureItem item) {
    final current = _draft;
    if (current is! StudyCaptureDraft ||
        index < 0 ||
        index >= current.items.length) {
      return;
    }
    _draft = current.copyWithItem(index, item);
    notifyListeners();
  }

  void updateVocabItem(int index, VocabCaptureItem item) {
    final current = _draft;
    if (current is! VocabCaptureDraft ||
        index < 0 ||
        index >= current.items.length) {
      return;
    }
    _draft = current.copyWithItem(index, item);
    notifyListeners();
  }

  Future<void> _runDraft(Future<CaptureDraft> Function() operation) async {
    _phase = CapturePhase.drafting;
    _errorMessage = null;
    _draft = null;
    _saved = null;
    notifyListeners();
    try {
      _draft = await operation();
      _draftVersion += 1;
      _phase = CapturePhase.review;
    } on Object catch (error) {
      _phase = CapturePhase.input;
      _errorMessage = _messageFor(error);
    }
    notifyListeners();
  }

  String _messageFor(Object error) {
    final message = error.toString().trim();
    return message.isEmpty
        ? 'Northstar could not complete the capture.'
        : message;
  }

  String? _nullableText(String? next, String? current) {
    if (next == null) {
      return current;
    }
    final trimmed = next.trim();
    return trimmed.isEmpty ? null : trimmed;
  }
}
