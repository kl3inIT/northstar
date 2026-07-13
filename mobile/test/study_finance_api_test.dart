import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:northstar/data/models/study_review_dtos.dart';
import 'package:northstar/data/services/authenticated_api_client.dart';
import 'package:northstar/data/services/authenticated_json_client.dart';
import 'package:northstar/data/services/finance_api.dart';
import 'package:northstar/data/services/study_review_api.dart';
import 'package:northstar/domain/models/study_review_models.dart';

void main() {
  test(
    'loads the direction-specific FSRS queue and posts learner rating',
    () async {
      final captured = <http.Request>[];
      final client = _HandlerClient((request) async {
        captured.add(request as http.Request);
        if (request.method == 'GET') {
          return _response(jsonEncode([_reviewCardJson()]));
        }
        return _response('{}');
      });
      final api = StudyReviewApi(_jsonClient(client));

      final cards = await api.reviewQueue(
        language: VocabLanguage.english,
        deck: 'IELTS',
        limit: 12,
      );
      await api.recordReview(
        id: 'card-1',
        rating: VocabRating.good,
        direction: VocabReviewDirection.production,
        previewedAt: DateTime.utc(2026, 7, 13, 1),
        schedulingVersion: 7,
        timezone: 'Asia/Bangkok',
      );

      expect(
        cards.single.toDomain().direction,
        VocabReviewDirection.production,
      );
      expect(captured.first.url.path, '/api/study/vocab/review');
      expect(captured.first.url.queryParameters, {
        'language': 'ENGLISH',
        'deck': 'IELTS',
        'limit': '12',
      });
      expect(captured.last.url.path, '/api/study/vocab/card-1/reviews');
      expect(captured.last.headers['X-Timezone'], 'Asia/Bangkok');
      expect(jsonDecode(captured.last.body), {
        'rating': 'GOOD',
        'direction': 'PRODUCTION',
        'previewedAt': '2026-07-13T01:00:00.000Z',
        'schedulingVersion': 7,
      });
    },
  );

  test('loads month summary and ledger rows with month and timezone', () async {
    final captured = <http.Request>[];
    final client = _HandlerClient((request) async {
      captured.add(request as http.Request);
      if (request.url.path.endsWith('/summary')) {
        return _response(jsonEncode(_summaryJson()));
      }
      return _response(jsonEncode([_transactionJson()]));
    });
    final api = FinanceApi(_jsonClient(client));

    final summary = await api.summary(
      month: '2026-07',
      timezone: 'Asia/Bangkok',
    );
    final transactions = await api.transactions(
      month: '2026-07',
      timezone: 'Asia/Bangkok',
    );

    expect(summary.toDomain().net, 5000000);
    expect(transactions.single.toDomain().description, 'Lunch');
    expect(captured.map((request) => request.url.path), [
      '/api/finance/summary',
      '/api/finance',
    ]);
    expect(
      captured.every(
        (request) => request.url.queryParameters['month'] == '2026-07',
      ),
      isTrue,
    );
    expect(
      captured.every(
        (request) => request.headers['X-Timezone'] == 'Asia/Bangkok',
      ),
      isTrue,
    );
  });

  test('parses metadata leniently and rejects unknown scheduling enums', () {
    final card = _reviewCardJson();
    card['metadata'] = '{bad json';
    final apiDto = _dto(card);
    expect(apiDto.metadata.reading, isNull);

    card['schedulingState'] = 'UNKNOWN';
    expect(() => _dto(card), throwsFormatException);
  });
}

VocabReviewCard _dto(Map<String, Object?> json) {
  return VocabReviewCardDto(json).toDomain();
}

AuthenticatedJsonClient _jsonClient(http.Client client) {
  return AuthenticatedJsonClient(
    authenticatedClient: AuthenticatedApiClient(
      client: client,
      accessToken: () => 'access-token',
      refreshAccessToken: () async => null,
      onUnauthorized: () {},
    ),
    baseUrl: Uri.parse('https://northstar.example'),
  );
}

Map<String, Object?> _reviewCardJson() => {
  'id': 'card-1',
  'schedulingCardId': 'schedule-1',
  'schedulingVersion': 7,
  'direction': 'PRODUCTION',
  'front': 'resilient',
  'back': 'có khả năng phục hồi',
  'metadata': jsonEncode({
    'reading': '/rɪˈzɪliənt/',
    'partOfSpeech': 'adjective',
    'example': 'A resilient system recovers quickly.',
  }),
  'language': 'ENGLISH',
  'deck': 'IELTS',
  'recallProbability': 0.82,
  'dueAt': '2026-07-13T00:00:00Z',
  'schedulingState': 'REVIEW',
  'lapseCount': 1,
  'leech': false,
  'reviewCount': 4,
  'previewedAt': '2026-07-13T01:00:00Z',
  'ratingPreviews': [
    {
      'rating': 'GOOD',
      'nextState': 'REVIEW',
      'dueAt': '2026-07-18T01:00:00Z',
      'intervalSeconds': 432000,
      'intervalLabel': '5d',
    },
  ],
};

Map<String, Object?> _summaryJson() => {
  'month': '2026-07',
  'expenseTotal': 3000000,
  'incomeTotal': 8000000,
  'net': 5000000,
  'exceptionalTotal': 1000000,
  'exceptionalCount': 1,
  'previousMonthExpenseTotal': 2500000,
  'categories': [
    {'name': 'Food', 'total': 2000000, 'hasExceptional': false},
  ],
};

Map<String, Object?> _transactionJson() => {
  'id': 'transaction-1',
  'type': 'EXPENSE',
  'amount': 65000,
  'occurredOn': '2026-07-13',
  'description': 'Lunch',
  'category': 'Food',
  'exceptional': false,
  'source': 'ASSISTANT',
};

http.StreamedResponse _response(String body) {
  return http.StreamedResponse(Stream.value(utf8.encode(body)), 200);
}

class _HandlerClient extends http.BaseClient {
  _HandlerClient(this._handler);

  final Future<http.StreamedResponse> Function(http.BaseRequest request)
  _handler;

  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) =>
      _handler(request);
}
