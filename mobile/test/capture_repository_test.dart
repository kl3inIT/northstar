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
}

class _FakeCaptureDataSource implements CaptureDataSource {
  _FakeCaptureDataSource({required this.draft});

  final CaptureDraftDto draft;
  int createCalls = 0;
  Map<String, Object?>? createdTask;
  Map<String, Object?>? createdTransactions;
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
