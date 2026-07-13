import 'package:flutter_test/flutter_test.dart';
import 'package:northstar/data/repositories/finance_repository.dart';
import 'package:northstar/data/repositories/study_review_repository.dart';
import 'package:northstar/data/services/device_timezone.dart';
import 'package:northstar/domain/models/finance_models.dart';
import 'package:northstar/domain/models/study_review_models.dart';
import 'package:northstar/ui/features/finance/view_models/finance_view_model.dart';
import 'package:northstar/ui/features/study/view_models/study_review_view_model.dart';

void main() {
  test(
    'keeps review progress in the ViewModel and advances after server save',
    () async {
      final repository = _StudyRepository();
      final viewModel = StudyReviewViewModel(
        repository: repository,
        timezoneProvider: _TimezoneProvider(),
      );
      await viewModel.initialize();

      expect(viewModel.currentCard?.prompt, 'có khả năng phục hồi');
      expect(viewModel.currentCard?.answer, 'resilient');
      viewModel.reveal();
      await viewModel.rate(VocabRating.good);

      expect(repository.lastRating, VocabRating.good);
      expect(repository.lastTimezone, 'Asia/Bangkok');
      expect(viewModel.index, 1);
      expect(viewModel.isComplete, isTrue);
      expect(viewModel.tallyFor(VocabRating.good), 1);
    },
  );

  test('keeps the answer revealed when rating persistence fails', () async {
    final repository = _StudyRepository(reviewFailures: 1);
    final viewModel = StudyReviewViewModel(
      repository: repository,
      timezoneProvider: _TimezoneProvider(),
    );
    await viewModel.initialize();
    viewModel.reveal();

    await viewModel.rate(VocabRating.hard);

    expect(viewModel.index, 0);
    expect(viewModel.revealed, isTrue);
    expect(viewModel.errorMessage, contains('Review save failed'));
  });

  test('reloads an independent queue when language changes', () async {
    final repository = _StudyRepository();
    final viewModel = StudyReviewViewModel(
      repository: repository,
      timezoneProvider: _TimezoneProvider(),
    );
    await viewModel.initialize();

    await viewModel.setLanguage(VocabLanguage.chinese);

    expect(repository.languages, [
      VocabLanguage.english,
      VocabLanguage.chinese,
    ]);
    expect(viewModel.language, VocabLanguage.chinese);
    expect(viewModel.index, 0);
  });

  test('loads the current finance month with the device timezone', () async {
    final repository = _FinanceRepository();
    final viewModel = FinanceViewModel(
      repository: repository,
      timezoneProvider: _TimezoneProvider(),
      clock: () => DateTime(2026, 7, 13),
    );

    await viewModel.initialize();

    expect(viewModel.phase, FinancePhase.ready);
    expect(repository.month, '2026-07');
    expect(repository.timezone, 'Asia/Bangkok');
    expect(viewModel.glance?.summary.net, 5000000);
  });

  test('refreshes Finance with the current device timezone', () async {
    final repository = _FinanceRepository();
    final viewModel = FinanceViewModel(
      repository: repository,
      timezoneProvider: _SequenceTimezoneProvider([
        'Asia/Bangkok',
        'Asia/Tokyo',
      ]),
      clock: () => DateTime(2026, 7, 13),
    );

    await viewModel.initialize();
    await viewModel.load();

    expect(repository.timezones, ['Asia/Bangkok', 'Asia/Tokyo']);
  });
}

class _TimezoneProvider implements DeviceTimezoneProvider {
  @override
  Future<String> currentIdentifier() async => 'Asia/Bangkok';
}

class _SequenceTimezoneProvider implements DeviceTimezoneProvider {
  _SequenceTimezoneProvider(this._values);

  final List<String> _values;
  int _index = 0;

  @override
  Future<String> currentIdentifier() async => _values[_index++];
}

class _StudyRepository implements StudyReviewRepository {
  _StudyRepository({this.reviewFailures = 0});

  int reviewFailures;
  VocabRating? lastRating;
  String? lastTimezone;
  final List<VocabLanguage> languages = [];

  @override
  Future<List<VocabReviewCard>> reviewQueue({
    required VocabLanguage language,
    String? deck,
    int limit = 20,
  }) async {
    languages.add(language);
    return [_card(language: language)];
  }

  @override
  Future<void> recordReview({
    required VocabReviewCard card,
    required VocabRating rating,
    required String timezone,
  }) async {
    await Future<void>.delayed(Duration.zero);
    if (reviewFailures > 0) {
      reviewFailures -= 1;
      throw StateError('Review save failed');
    }
    lastRating = rating;
    lastTimezone = timezone;
  }
}

class _FinanceRepository implements FinanceRepository {
  String? month;
  String? timezone;
  final List<String> timezones = [];

  @override
  Future<FinanceGlance> glance({
    required String month,
    required String timezone,
  }) async {
    this.month = month;
    this.timezone = timezone;
    timezones.add(timezone);
    return _glance();
  }
}

VocabReviewCard _card({VocabLanguage language = VocabLanguage.english}) {
  return VocabReviewCard(
    id: 'card-1',
    schedulingCardId: 'schedule-1',
    schedulingVersion: 7,
    direction: VocabReviewDirection.production,
    front: language == VocabLanguage.english ? 'resilient' : '恢复力强',
    back: language == VocabLanguage.english
        ? 'có khả năng phục hồi'
        : 'resilient',
    metadata: const VocabReviewMetadata(
      reading: '/rɪˈzɪliənt/',
      example: 'A resilient system recovers quickly.',
    ),
    language: language,
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
    transactions: [],
  );
}
