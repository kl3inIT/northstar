import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:northstar/data/repositories/capture_repository.dart';
import 'package:northstar/data/services/receipt_picker.dart';
import 'package:northstar/domain/models/capture_models.dart';
import 'package:northstar/ui/features/capture/view_models/capture_view_model.dart';
import 'package:northstar/ui/features/capture/views/capture_view.dart';

void main() {
  testWidgets('reviews and edits an AI draft before saving it', (tester) async {
    _setWindowSize(tester, const Size(390, 844));
    final repository = _CaptureViewRepository(
      textDraft: const TaskCaptureDraft(
        title: 'Original task',
        notes: 'Check the result',
        dueDate: '2026-07-12',
        dueTime: '09:00',
      ),
    );
    final viewModel = CaptureViewModel(
      repository: repository,
      receiptPicker: _CaptureViewReceiptPicker(),
    );

    await tester.pumpWidget(_app(viewModel));
    await tester.enterText(
      find.byKey(const Key('capture-text-field')),
      'Submit my essay tomorrow',
    );
    await tester.tap(find.byKey(const Key('capture-draft-button')));
    await tester.pumpAndSettle();

    expect(find.text('Review task'), findsOneWidget);
    expect(repository.saveCalls, 0);
    await tester.enterText(
      find.byKey(const Key('capture-task-title')),
      'Submit final essay',
    );
    await tester.tap(find.byKey(const Key('capture-save-button')));
    await tester.pumpAndSettle();

    expect(find.text('Saved to Northstar'), findsOneWidget);
    expect(repository.saveCalls, 1);
    expect(
      (repository.savedDraft! as TaskCaptureDraft).title,
      'Submit final essay',
    );
    expect(find.byKey(const Key('capture-undo-button')), findsOneWidget);
  });

  testWidgets(
    'receipt review exposes consequential expense fields in dark mode',
    (tester) async {
      _setWindowSize(tester, const Size(390, 844));
      final repository = _CaptureViewRepository(
        textDraft: const NoteCaptureDraft(
          title: 'Unused',
          folderPath: '',
          contentMarkdown: '',
          tags: [],
        ),
        receiptDraft: const ExpenseCaptureDraft([
          ExpenseCaptureItem(
            type: CaptureTransactionType.expense,
            amount: 65000,
            occurredOn: '2026-07-11',
            description: 'Lunch receipt',
            category: 'Ăn uống',
            exceptional: false,
          ),
        ]),
      );
      final viewModel = CaptureViewModel(
        repository: repository,
        receiptPicker: _CaptureViewReceiptPicker(),
      );

      await tester.pumpWidget(
        CupertinoApp(
          theme: const CupertinoThemeData(brightness: Brightness.dark),
          home: CaptureView(viewModel: viewModel),
        ),
      );
      await tester.tap(find.byKey(const Key('capture-photo-button')));
      await tester.pumpAndSettle();

      expect(find.text('Review expense'), findsOneWidget);
      expect(
        find.byKey(const Key('capture-expense-description-0')),
        findsOneWidget,
      );
      expect(find.byKey(const Key('capture-expense-amount-0')), findsOneWidget);
      expect(find.text('One-off'), findsOneWidget);
      expect(
        find.text(
          'AI drafted these fields. Correct anything that is wrong before saving.',
        ),
        findsOneWidget,
      );
    },
  );

  testWidgets('edits a reviewed study draft before saving', (tester) async {
    _setWindowSize(tester, const Size(390, 844));
    final repository = _CaptureViewRepository(
      textDraft: const StudyCaptureDraft([
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
      receiptPicker: _CaptureViewReceiptPicker(),
    );

    await tester.pumpWidget(_app(viewModel));
    await tester.enterText(
      find.byKey(const Key('capture-text-field')),
      'IELTS reading practice',
    );
    await tester.tap(find.byKey(const Key('capture-draft-button')));
    await tester.pumpAndSettle();

    await tester.enterText(
      find.byKey(const Key('capture-study-skill-0')),
      'IELTS Reading',
    );
    await tester.enterText(
      find.byKey(const Key('capture-study-score-raw-0')),
      '34',
    );
    await tester.enterText(
      find.byKey(const Key('capture-study-score-max-0')),
      '40',
    );
    await tester.ensureVisible(find.byKey(const Key('capture-save-button')));
    await tester.tap(find.byKey(const Key('capture-save-button')));
    await tester.pumpAndSettle();

    final saved = repository.savedDraft! as StudyCaptureDraft;
    expect(saved.items.single.skill, 'IELTS Reading');
    expect(saved.items.single.scoreRaw, 34);
    expect(saved.items.single.scoreMax, 40);
  });

  testWidgets('edits a reviewed vocabulary draft before saving', (
    tester,
  ) async {
    _setWindowSize(tester, const Size(390, 844));
    final repository = _CaptureViewRepository(
      textDraft: const VocabCaptureDraft([
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
      receiptPicker: _CaptureViewReceiptPicker(),
    );

    await tester.pumpWidget(_app(viewModel));
    await tester.enterText(
      find.byKey(const Key('capture-text-field')),
      'Add resilient to vocabulary',
    );
    await tester.tap(find.byKey(const Key('capture-draft-button')));
    await tester.pumpAndSettle();

    await tester.enterText(
      find.byKey(const Key('capture-vocab-back-0')),
      'có khả năng phục hồi',
    );
    await tester.enterText(
      find.byKey(const Key('capture-vocab-example-0')),
      'A resilient system recovers quickly.',
    );
    await tester.ensureVisible(find.byKey(const Key('capture-save-button')));
    await tester.tap(find.byKey(const Key('capture-save-button')));
    await tester.pumpAndSettle();

    final saved = repository.savedDraft! as VocabCaptureDraft;
    expect(saved.items.single.back, 'có khả năng phục hồi');
    expect(saved.items.single.example, contains('recovers quickly'));
  });
}

Widget _app(CaptureViewModel viewModel) {
  return CupertinoApp(home: CaptureView(viewModel: viewModel));
}

class _CaptureViewRepository implements CaptureRepository {
  _CaptureViewRepository({required this.textDraft, this.receiptDraft});

  final CaptureDraft textDraft;
  final CaptureDraft? receiptDraft;
  int saveCalls = 0;
  CaptureDraft? savedDraft;

  @override
  Future<CaptureDraft> draftText({
    required String text,
    CaptureKind? forcedKind,
  }) async => textDraft;

  @override
  Future<CaptureDraft> draftReceipt(ReceiptUpload upload) async =>
      receiptDraft ?? textDraft;

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
  Future<void> undo(SavedCapture saved) async {}
}

class _CaptureViewReceiptPicker implements ReceiptSourcePicker {
  @override
  Future<ReceiptUpload?> pick(ReceiptSource source) async {
    return const ReceiptUpload(
      bytes: [1, 2, 3],
      filename: 'receipt.jpg',
      mimeType: 'image/jpeg',
    );
  }
}

void _setWindowSize(WidgetTester tester, Size size) {
  tester.view.devicePixelRatio = 1;
  tester.view.physicalSize = size;
  addTearDown(tester.view.resetDevicePixelRatio);
  addTearDown(tester.view.resetPhysicalSize);
}
