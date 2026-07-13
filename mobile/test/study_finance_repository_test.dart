import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:northstar/data/models/finance_dtos.dart';
import 'package:northstar/data/models/study_review_dtos.dart';
import 'package:northstar/data/repositories/finance_repository.dart';
import 'package:northstar/data/repositories/study_review_repository.dart';
import 'package:northstar/data/services/finance_api.dart';
import 'package:northstar/data/services/study_review_api.dart';
import 'package:northstar/domain/models/study_review_models.dart';

void main() {
  test(
    'maps review queue and preserves the server scheduling token on rating',
    () async {
      final api = _StudyDataSource();
      final repository = RemoteStudyReviewRepository(api);
      final cards = await repository.reviewQueue(
        language: VocabLanguage.english,
      );

      await repository.recordReview(
        card: cards.single,
        rating: VocabRating.easy,
        timezone: 'Asia/Bangkok',
      );

      expect(cards.single.prompt, 'meaning');
      expect(api.reviewedId, 'card-1');
      expect(api.schedulingVersion, 9);
      expect(api.previewedAt, DateTime.utc(2026, 7, 13, 1));
      expect(api.direction, VocabReviewDirection.production);
    },
  );

  test(
    'combines finance summary and recent transactions into one glance',
    () async {
      final repository = RemoteFinanceRepository(_FinanceDataSource());

      final glance = await repository.glance(
        month: '2026-07',
        timezone: 'Asia/Bangkok',
      );

      expect(glance.summary.net, 5000000);
      expect(glance.transactions.single.description, 'Lunch');
    },
  );
}

class _StudyDataSource implements StudyReviewDataSource {
  String? reviewedId;
  int? schedulingVersion;
  DateTime? previewedAt;
  VocabReviewDirection? direction;

  @override
  Future<List<VocabReviewCardDto>> reviewQueue({
    required VocabLanguage language,
    String? deck,
    int limit = 20,
  }) async => [VocabReviewCardDto(_cardJson())];

  @override
  Future<void> recordReview({
    required String id,
    required VocabRating rating,
    required VocabReviewDirection direction,
    required DateTime previewedAt,
    required int schedulingVersion,
    required String timezone,
  }) async {
    reviewedId = id;
    this.schedulingVersion = schedulingVersion;
    this.previewedAt = previewedAt;
    this.direction = direction;
  }
}

class _FinanceDataSource implements FinanceDataSource {
  @override
  Future<FinanceMonthSummaryDto> summary({
    required String month,
    required String timezone,
  }) async => FinanceMonthSummaryDto({
    'month': month,
    'expenseTotal': 3000000,
    'incomeTotal': 8000000,
    'net': 5000000,
    'exceptionalTotal': 0,
    'exceptionalCount': 0,
    'previousMonthExpenseTotal': 2500000,
    'categories': <Object?>[],
  });

  @override
  Future<List<FinanceTransactionDto>> transactions({
    required String month,
    required String timezone,
  }) async => [
    FinanceTransactionDto({
      'id': 'transaction-1',
      'type': 'EXPENSE',
      'amount': 65000,
      'occurredOn': '2026-07-13',
      'description': 'Lunch',
      'category': 'Food',
      'exceptional': false,
      'source': 'ASSISTANT',
    }),
  ];
}

Map<String, Object?> _cardJson() => {
  'id': 'card-1',
  'schedulingCardId': 'schedule-1',
  'schedulingVersion': 9,
  'direction': 'PRODUCTION',
  'front': 'word',
  'back': 'meaning',
  'metadata': jsonEncode({'example': 'Example'}),
  'language': 'ENGLISH',
  'recallProbability': 0.5,
  'dueAt': '2026-07-13T00:00:00Z',
  'schedulingState': 'LEARNING',
  'lapseCount': 0,
  'leech': false,
  'reviewCount': 0,
  'previewedAt': '2026-07-13T01:00:00Z',
  'ratingPreviews': [
    {
      'rating': 'EASY',
      'nextState': 'REVIEW',
      'dueAt': '2026-07-20T01:00:00Z',
      'intervalSeconds': 604800,
      'intervalLabel': '7d',
    },
  ],
};
