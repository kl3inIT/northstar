import 'package:flutter_test/flutter_test.dart';
import 'package:northstar/data/repositories/capture_repository.dart';
import 'package:northstar/data/services/receipt_picker.dart';
import 'package:northstar/domain/models/capture_models.dart';
import 'package:northstar/ui/features/capture/view_models/capture_view_model.dart';

void main() {
  test(
    'requires explicit save after drafting and uses edited fields',
    () async {
      final repository = _FakeCaptureRepository();
      final viewModel = CaptureViewModel(
        repository: repository,
        receiptPicker: _FakeReceiptPicker(),
      );
      viewModel.setText('Remember the architecture decision');
      viewModel.setForcedKind(CaptureKind.note);

      await viewModel.draftText();

      expect(viewModel.phase, CapturePhase.review);
      expect(repository.saveCalls, 0);
      viewModel.updateNote(title: 'Mobile architecture decision');
      await viewModel.save();

      expect(viewModel.phase, CapturePhase.saved);
      expect(repository.savedDraft, isA<NoteCaptureDraft>());
      expect(
        (repository.savedDraft! as NoteCaptureDraft).title,
        'Mobile architecture decision',
      );
    },
  );

  test('receipt cancellation returns to input without an error', () async {
    final viewModel = CaptureViewModel(
      repository: _FakeCaptureRepository(),
      receiptPicker: _FakeReceiptPicker(result: null),
    );

    await viewModel.draftReceipt(ReceiptSource.photoLibrary);

    expect(viewModel.phase, CapturePhase.input);
    expect(viewModel.errorMessage, isNull);
  });

  test(
    'clearing event times turns the draft back into an all-day event',
    () async {
      final repository = _FakeCaptureRepository(
        draft: const EventCaptureDraft(
          title: 'Study day',
          notes: '',
          date: '2026-07-12',
          startTime: '09:00',
          endTime: '10:00',
        ),
      );
      final viewModel = CaptureViewModel(
        repository: repository,
        receiptPicker: _FakeReceiptPicker(),
      )..setText('Study all day');

      await viewModel.draftText();
      viewModel.updateEvent(startTime: '', endTime: '');

      final event = viewModel.draft! as EventCaptureDraft;
      expect(event.startTime, isNull);
      expect(event.endTime, isNull);
    },
  );

  test('undo failure remains recoverable and retry completes it', () async {
    final repository = _FakeCaptureRepository(undoFailures: 1);
    final viewModel = CaptureViewModel(
      repository: repository,
      receiptPicker: _FakeReceiptPicker(),
    )..setText('Create a note');
    await viewModel.draftText();
    await viewModel.save();

    await viewModel.undo();
    expect(viewModel.phase, CapturePhase.saved);
    expect(viewModel.errorMessage, contains('Undo unavailable'));

    await viewModel.retry();
    expect(viewModel.phase, CapturePhase.undone);
    expect(repository.undoCalls, 2);
  });

  test('edits and saves a reviewed study activity, then undoes it', () async {
    final repository = _FakeCaptureRepository(
      draft: const StudyCaptureDraft([
        StudyCaptureItem(
          skill: 'Reading',
          kind: StudyCaptureKind.practice,
          occurredOn: '2026-07-13',
          notes: '',
          durationMinutes: 30,
        ),
      ]),
    );
    final viewModel = CaptureViewModel(
      repository: repository,
      receiptPicker: _FakeReceiptPicker(),
    )..setText('Practiced IELTS reading');

    await viewModel.draftText();
    final original = (viewModel.draft! as StudyCaptureDraft).items.single;
    viewModel.updateStudyItem(
      0,
      StudyCaptureItem(
        skill: 'IELTS Reading',
        kind: StudyCaptureKind.mock,
        occurredOn: original.occurredOn,
        notes: 'Cambridge 19',
        durationMinutes: 40,
        scoreRaw: 34,
        scoreMax: 40,
      ),
    );
    await viewModel.save();

    final saved = repository.savedDraft! as StudyCaptureDraft;
    expect(saved.items.single.skill, 'IELTS Reading');
    expect(saved.items.single.kind, StudyCaptureKind.mock);
    expect(saved.items.single.scoreRaw, 34);

    await viewModel.undo();
    expect(viewModel.phase, CapturePhase.undone);
    expect(repository.undoCalls, 1);
  });

  test('edits and saves a reviewed vocabulary card', () async {
    final repository = _FakeCaptureRepository(
      draft: const VocabCaptureDraft([
        VocabCaptureItem(
          front: 'resilient',
          back: 'able to recover',
          reading: '',
          partOfSpeech: 'adjective',
          example: '',
          language: VocabCaptureLanguage.english,
          deck: 'IELTS',
        ),
      ]),
    );
    final viewModel = CaptureViewModel(
      repository: repository,
      receiptPicker: _FakeReceiptPicker(),
    )..setText('Save resilient to vocabulary');

    await viewModel.draftText();
    final original = (viewModel.draft! as VocabCaptureDraft).items.single;
    viewModel.updateVocabItem(
      0,
      original.copyWith(
        back: 'có khả năng phục hồi',
        example: 'A resilient system recovers quickly.',
      ),
    );
    await viewModel.save();

    final saved = repository.savedDraft! as VocabCaptureDraft;
    expect(saved.items.single.back, 'có khả năng phục hồi');
    expect(saved.items.single.example, contains('recovers quickly'));
  });
}

class _FakeCaptureRepository implements CaptureRepository {
  _FakeCaptureRepository({
    this.undoFailures = 0,
    this.draft = const NoteCaptureDraft(
      title: 'Draft note',
      folderPath: '',
      contentMarkdown: 'Draft content',
      tags: [],
    ),
  });

  int undoFailures;
  final CaptureDraft draft;
  int saveCalls = 0;
  int undoCalls = 0;
  CaptureDraft? savedDraft;

  @override
  Future<CaptureDraft> draftText({
    required String text,
    CaptureKind? forcedKind,
  }) async => draft;

  @override
  Future<CaptureDraft> draftReceipt(ReceiptUpload upload) async {
    return const ExpenseCaptureDraft([
      ExpenseCaptureItem(
        type: CaptureTransactionType.expense,
        amount: 10000,
        occurredOn: '2026-07-11',
        description: 'Receipt',
        category: 'Khác',
        exceptional: false,
      ),
    ]);
  }

  @override
  Future<SavedCapture> save(CaptureDraft draft) async {
    saveCalls += 1;
    savedDraft = draft;
    return SavedCapture(
      kind: draft.kind,
      ids: const ['saved-1'],
      title: draft.title,
    );
  }

  @override
  Future<void> undo(SavedCapture saved) async {
    undoCalls += 1;
    if (undoFailures > 0) {
      undoFailures -= 1;
      throw StateError('Undo unavailable');
    }
  }
}

class _FakeReceiptPicker implements ReceiptSourcePicker {
  _FakeReceiptPicker({
    this.result = const ReceiptUpload(
      bytes: [1],
      filename: 'receipt.jpg',
      mimeType: 'image/jpeg',
    ),
  });

  final ReceiptUpload? result;

  @override
  Future<ReceiptUpload?> pick(ReceiptSource source) async => result;
}
