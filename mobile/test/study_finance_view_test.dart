import 'dart:async';

import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:northstar/data/repositories/finance_repository.dart';
import 'package:northstar/data/repositories/study_review_repository.dart';
import 'package:northstar/data/services/device_timezone.dart';
import 'package:northstar/domain/models/finance_models.dart';
import 'package:northstar/domain/models/study_review_models.dart';
import 'package:northstar/ui/features/finance/view_models/finance_view_model.dart';
import 'package:northstar/ui/features/finance/views/finance_view.dart';
import 'package:northstar/ui/features/study/view_models/study_review_view_model.dart';
import 'package:northstar/ui/features/study/views/study_review_view.dart';

void main() {
  testWidgets('reveals a production card before enabling FSRS ratings', (
    tester,
  ) async {
    _setWindowSize(tester, const Size(390, 844));
    final repository = _StudyViewRepository();
    final viewModel = StudyReviewViewModel(
      repository: repository,
      timezoneProvider: _TimezoneProvider(),
    );

    await tester.pumpWidget(
      CupertinoApp(
        theme: const CupertinoThemeData(brightness: Brightness.dark),
        home: StudyReviewView(viewModel: viewModel),
      ),
    );
    await _pumpAsync(tester);

    expect(find.text('có khả năng phục hồi'), findsOneWidget);
    expect(find.text('resilient'), findsNothing);
    expect(find.text('/rɪˈzɪliənt/'), findsNothing);
    expect(find.byKey(const Key('study-rating-good')), findsNothing);

    await tester.tap(find.byKey(const Key('study-reveal-button')));
    await tester.pump();

    expect(find.text('resilient'), findsOneWidget);
    expect(find.text('/rɪˈzɪliənt/'), findsOneWidget);
    expect(find.text('5d'), findsOneWidget);
    await tester.tap(find.byKey(const Key('study-rating-good')));
    await _pumpAsync(tester);

    expect(repository.rating, VocabRating.good);
    expect(find.byKey(const Key('study-complete')), findsOneWidget);
  });

  testWidgets('shows Study empty, error, and expanded states', (tester) async {
    _setWindowSize(tester, const Size(1024, 768));
    final repository = _StudyViewRepository(loadFailures: 1, cards: const []);
    final viewModel = StudyReviewViewModel(
      repository: repository,
      timezoneProvider: _TimezoneProvider(),
    );

    await tester.pumpWidget(
      CupertinoApp(home: StudyReviewView(viewModel: viewModel)),
    );
    await _pumpAsync(tester);
    expect(find.byKey(const Key('study-load-error')), findsOneWidget);

    await tester.tap(find.text('Try again'));
    await _pumpAsync(tester);
    expect(find.byKey(const Key('study-empty')), findsOneWidget);
  });

  testWidgets('renders a compact dark Finance glance with recent activity', (
    tester,
  ) async {
    _setWindowSize(tester, const Size(390, 844));
    await tester.pumpWidget(
      CupertinoApp(
        theme: const CupertinoThemeData(brightness: Brightness.dark),
        home: FinanceView(
          viewModel: FinanceViewModel(
            repository: _FinanceViewRepository(),
            timezoneProvider: _TimezoneProvider(),
            clock: () => DateTime(2026, 7, 13),
          ),
        ),
      ),
    );
    await _pumpAsync(tester);

    expect(find.byKey(const Key('finance-compact-layout')), findsOneWidget);
    expect(find.text('Income'), findsOneWidget);
    expect(find.text('8.000.000 ₫'), findsOneWidget);
    expect(find.byKey(const Key('finance-one-off-summary')), findsOneWidget);
    await tester.ensureVisible(
      find.byKey(const Key('finance-transaction-transaction-1')),
    );
    expect(find.text('Lunch'), findsOneWidget);
  });

  testWidgets('shows Finance retry and expanded layouts', (tester) async {
    _setWindowSize(tester, const Size(1024, 768));
    final repository = _FinanceViewRepository(failures: 1);
    final viewModel = FinanceViewModel(
      repository: repository,
      timezoneProvider: _TimezoneProvider(),
      clock: () => DateTime(2026, 7, 13),
    );

    await tester.pumpWidget(
      CupertinoApp(home: FinanceView(viewModel: viewModel)),
    );
    await _pumpAsync(tester);
    expect(find.byKey(const Key('finance-load-error')), findsOneWidget);

    await tester.tap(find.byKey(const Key('finance-load-retry')));
    await _pumpAsync(tester);
    expect(find.byKey(const Key('finance-expanded-layout')), findsOneWidget);
  });

  testWidgets('keeps Finance content visible during a failed refresh', (
    tester,
  ) async {
    _setWindowSize(tester, const Size(390, 844));
    final repository = _FinanceViewRepository();
    final viewModel = FinanceViewModel(
      repository: repository,
      timezoneProvider: _TimezoneProvider(),
      clock: () => DateTime(2026, 7, 13),
    );
    await tester.pumpWidget(
      CupertinoApp(home: FinanceView(viewModel: viewModel)),
    );
    await _pumpAsync(tester);

    repository.next = Completer<FinanceGlance>();
    final refresh = viewModel.load();
    await tester.pump();
    expect(find.byKey(const Key('finance-compact-layout')), findsOneWidget);
    expect(find.byKey(const Key('finance-loading-indicator')), findsNothing);

    repository.next!.completeError(Exception('Offline'));
    await refresh;
    await tester.pump();
    expect(find.byKey(const Key('finance-compact-layout')), findsOneWidget);
    expect(find.byKey(const Key('finance-load-error')), findsNothing);
  });
}

class _TimezoneProvider implements DeviceTimezoneProvider {
  @override
  Future<String> currentIdentifier() async => 'Asia/Bangkok';
}

class _StudyViewRepository implements StudyReviewRepository {
  _StudyViewRepository({this.loadFailures = 0, List<VocabReviewCard>? cards})
    : cards = cards ?? [_card()];

  int loadFailures;
  final List<VocabReviewCard> cards;
  VocabRating? rating;

  @override
  Future<List<VocabReviewCard>> reviewQueue({
    required VocabLanguage language,
    String? deck,
    int limit = 20,
  }) async {
    if (loadFailures > 0) {
      loadFailures -= 1;
      throw StateError('Offline');
    }
    return cards;
  }

  @override
  Future<void> recordReview({
    required VocabReviewCard card,
    required VocabRating rating,
    required String timezone,
  }) async {
    this.rating = rating;
  }
}

class _FinanceViewRepository implements FinanceRepository {
  _FinanceViewRepository({this.failures = 0});

  int failures;
  Completer<FinanceGlance>? next;

  @override
  Future<FinanceGlance> glance({
    required String month,
    required String timezone,
  }) async {
    final pending = next;
    if (pending != null) {
      return pending.future;
    }
    if (failures > 0) {
      failures -= 1;
      throw StateError('Offline');
    }
    return _glance();
  }
}

VocabReviewCard _card() {
  return VocabReviewCard(
    id: 'card-1',
    schedulingCardId: 'schedule-1',
    schedulingVersion: 7,
    direction: VocabReviewDirection.production,
    front: 'resilient',
    back: 'có khả năng phục hồi',
    metadata: const VocabReviewMetadata(
      reading: '/rɪˈzɪliənt/',
      example: 'A resilient system recovers quickly.',
    ),
    language: VocabLanguage.english,
    recallProbability: 0.82,
    dueAt: DateTime.utc(2026, 7, 13),
    schedulingState: VocabSchedulingState.review,
    lapseCount: 1,
    leech: false,
    reviewCount: 4,
    previewedAt: DateTime.utc(2026, 7, 13, 1),
    ratingPreviews: [
      for (final rating in VocabRating.values)
        VocabRatingPreview(
          rating: rating,
          nextState: VocabSchedulingState.review,
          dueAt: DateTime.utc(2026, 7, 18),
          intervalSeconds: 432000,
          intervalLabel: rating == VocabRating.good ? '5d' : '1d',
        ),
    ],
  );
}

FinanceGlance _glance() {
  return const FinanceGlance(
    summary: FinanceMonthSummary(
      month: '2026-07',
      expenseTotal: 3000000,
      incomeTotal: 8000000,
      net: 5000000,
      exceptionalTotal: 1000000,
      exceptionalCount: 1,
      previousMonthExpenseTotal: 2500000,
      categories: [
        FinanceCategoryTotal(
          name: 'Food',
          total: 2000000,
          hasExceptional: false,
        ),
      ],
    ),
    transactions: [
      FinanceTransaction(
        id: 'transaction-1',
        type: FinanceTransactionType.expense,
        amount: 65000,
        occurredOn: '2026-07-13',
        description: 'Lunch',
        category: 'Food',
        exceptional: false,
        source: 'ASSISTANT',
      ),
    ],
  );
}

void _setWindowSize(WidgetTester tester, Size size) {
  tester.view.devicePixelRatio = 1;
  tester.view.physicalSize = size;
  addTearDown(tester.view.resetDevicePixelRatio);
  addTearDown(tester.view.resetPhysicalSize);
}

Future<void> _pumpAsync(WidgetTester tester) async {
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 250));
}
