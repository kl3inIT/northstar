import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:northstar/data/models/capture_dtos.dart';
import 'package:northstar/data/repositories/capture_repository.dart';
import 'package:northstar/data/services/capture_api.dart';
import 'package:northstar/domain/models/capture_models.dart';

void main() {
  test(
    'maps a task draft and resolves its discipline only when saving',
    () async {
      final api = _FakeCaptureDataSource(
        draft: const CaptureDraftDto(
          kind: CaptureKind.task,
          task: TaskDraftDto(
            title: 'Submit essay',
            notes: 'Final pass',
            dueDate: '2026-07-12',
            dueTime: '09:00',
            disciplineName: 'IELTS',
          ),
        ),
      );
      final repository = RemoteCaptureRepository(api);

      final draft = await repository.draftText(
        text: 'Submit my essay tomorrow at 9',
      );

      expect(draft, isA<TaskCaptureDraft>());
      expect(api.createCalls, 0, reason: 'drafting must not persist data');

      final saved = await repository.save(draft);

      expect(saved.kind, CaptureKind.task);
      expect(saved.ids, ['task-1']);
      expect(api.createdTask?['disciplineId'], 'discipline-1');
      expect(api.createdTask?['dueDate'], '2026-07-12');
    },
  );

  test(
    'receipt expenses are reviewed, saved as a batch, and fully undone',
    () async {
      final api = _FakeCaptureDataSource(
        draft: const CaptureDraftDto(
          kind: CaptureKind.expense,
          expense: ExpenseDraftDto([
            ExpenseItemDto(
              type: CaptureTransactionType.expense,
              amount: 45000,
              occurredOn: '2026-07-11',
              description: 'Lunch',
              category: 'Ăn uống',
              exceptional: false,
            ),
            ExpenseItemDto(
              type: CaptureTransactionType.expense,
              amount: 20000,
              occurredOn: '2026-07-11',
              description: 'Coffee',
              category: 'Ăn uống',
              exceptional: false,
            ),
          ]),
        ),
      );
      final repository = RemoteCaptureRepository(api);

      final draft = await repository.draftReceipt(
        const ReceiptUpload(
          bytes: [1, 2, 3],
          filename: 'receipt.jpg',
          mimeType: 'image/jpeg',
        ),
      );
      final saved = await repository.save(draft);
      await repository.undo(saved);

      expect(api.createdTransactions?['items'], hasLength(2));
      expect(saved.ids, ['transaction-1', 'transaction-2']);
      expect(api.deletedPaths, [
        '/api/finance/transaction-1',
        '/api/finance/transaction-2',
      ]);
    },
  );

  test('maps, saves, and fully undoes a study batch', () async {
    final api = _FakeCaptureDataSource(
      draft: const CaptureDraftDto(
        kind: CaptureKind.study,
        study: StudyDraftDto([
          StudyItemDto(
            skill: 'Listening',
            kind: 'PRACTICE',
            durationMinutes: '25',
            scoreRaw: '18',
            scoreMax: '25',
            notes: 'HSK4',
            occurredOn: '2026-07-13',
            disciplineName: 'IELTS',
          ),
          StudyItemDto(
            skill: 'Writing',
            kind: 'MOCK',
            durationMinutes: '40',
            scoreRaw: '',
            scoreMax: '',
            notes: 'Task 2',
            occurredOn: '2026-07-13',
            disciplineName: '',
          ),
        ]),
      ),
    );
    final repository = RemoteCaptureRepository(api);

    final draft = await repository.draftText(text: 'Study log');
    final saved = await repository.save(draft);
    await repository.undo(saved);

    expect(draft, isA<StudyCaptureDraft>());
    final items = api.createdStudySessions?['items'] as List;
    expect(items, hasLength(2));
    expect((items.first as Map)['durationMinutes'], 25);
    expect((items.first as Map)['disciplineId'], 'discipline-1');
    expect((items.last as Map)['kind'], 'MOCK');
    expect(saved.ids, ['study-1', 'study-2']);
    expect(api.deletedPaths, [
      '/api/study/sessions/study-1',
      '/api/study/sessions/study-2',
    ]);
  });

  test('maps, saves, and fully undoes vocabulary cards', () async {
    final api = _FakeCaptureDataSource(
      draft: const CaptureDraftDto(
        kind: CaptureKind.vocab,
        vocab: VocabDraftDto([
          VocabItemDto(
            front: '磨蹭',
            back: 'lề mề',
            reading: 'mócèng',
            partOfSpeech: 'verb',
            example: '',
            language: '',
            deck: 'HSK4',
            disciplineName: 'IELTS',
          ),
        ]),
      ),
    );
    final repository = RemoteCaptureRepository(api);

    final draft = await repository.draftText(text: '磨蹭 = lề mề');
    final saved = await repository.save(draft);
    await repository.undo(saved);

    expect(draft, isA<VocabCaptureDraft>());
    final requestItems = api.createdVocabCards?['items'] as List;
    final request = requestItems.single as Map;
    expect(request['language'], 'CHINESE');
    expect(request['disciplineId'], 'discipline-1');
    expect(jsonDecode(request['metadata']! as String), {
      'reading': 'mócèng',
      'partOfSpeech': 'verb',
    });
    expect(saved.ids, ['vocab-1']);
    expect(api.deletedPaths, ['/api/study/vocab/vocab-1']);
  });

  test('rejects an incomplete study score before persistence', () async {
    final api = _FakeCaptureDataSource(
      draft: const CaptureDraftDto(
        kind: CaptureKind.study,
        study: StudyDraftDto([
          StudyItemDto(
            skill: 'Reading',
            kind: 'PRACTICE',
            durationMinutes: '',
            scoreRaw: '18',
            scoreMax: '',
            notes: '',
            occurredOn: '2026-07-13',
            disciplineName: '',
          ),
        ]),
      ),
    );
    final repository = RemoteCaptureRepository(api);
    final draft = await repository.draftText(text: 'Reading 18');

    await expectLater(
      repository.save(draft),
      throwsA(
        isA<FormatException>().having(
          (error) => error.message,
          'message',
          contains('raw and maximum'),
        ),
      ),
    );
    expect(api.createCalls, 0);
  });
}

class _FakeCaptureDataSource implements CaptureDataSource {
  _FakeCaptureDataSource({required this.draft});

  final CaptureDraftDto draft;
  int createCalls = 0;
  Map<String, Object?>? createdTask;
  Map<String, Object?>? createdTransactions;
  Map<String, Object?>? createdStudySessions;
  Map<String, Object?>? createdVocabCards;
  final List<String> deletedPaths = [];

  @override
  Future<CaptureDraftDto> draftText({
    required String text,
    CaptureKind? forcedKind,
  }) async => draft;

  @override
  Future<CaptureDraftDto> draftReceipt(ReceiptUpload upload) async => draft;

  @override
  Future<List<Map<String, Object?>>> listDisciplines() async => [
    {'id': 'discipline-1', 'name': 'IELTS'},
  ];

  @override
  Future<Map<String, Object?>> createTask(Map<String, Object?> body) async {
    createCalls += 1;
    createdTask = body;
    return {'id': 'task-1', 'title': body['title']};
  }

  @override
  Future<List<Map<String, Object?>>> createTransactions(
    Map<String, Object?> body,
  ) async {
    createCalls += 1;
    createdTransactions = body;
    return [
      {'id': 'transaction-1'},
      {'id': 'transaction-2'},
    ];
  }

  @override
  Future<List<Map<String, Object?>>> createStudySessions(
    Map<String, Object?> body,
  ) async {
    createCalls += 1;
    createdStudySessions = body;
    return [
      {'id': 'study-1'},
      {'id': 'study-2'},
    ];
  }

  @override
  Future<List<Map<String, Object?>>> createVocabCards(
    Map<String, Object?> body,
  ) async {
    createCalls += 1;
    createdVocabCards = body;
    return [
      {'id': 'vocab-1'},
    ];
  }

  @override
  Future<void> delete(String path) async => deletedPaths.add(path);

  @override
  Future<Map<String, Object?>> createEvent(Map<String, Object?> body) {
    throw UnimplementedError();
  }

  @override
  Future<Map<String, Object?>> createNote(Map<String, Object?> body) {
    throw UnimplementedError();
  }
}
