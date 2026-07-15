import 'package:flutter/foundation.dart';
import 'package:northstar/data/repositories/study_review_repository.dart';
import 'package:northstar/data/services/device_timezone.dart';
import 'package:northstar/data/services/interaction_telemetry.dart';
import 'package:northstar/domain/models/study_review_models.dart';

enum StudyReviewPhase { idle, loading, ready, error }

class StudyReviewViewModel extends ChangeNotifier {
  factory StudyReviewViewModel({
    required StudyReviewRepository repository,
    required DeviceTimezoneProvider timezoneProvider,
    InteractionTelemetry telemetry = const NoopInteractionTelemetry(),
  }) {
    return StudyReviewViewModel._(repository, timezoneProvider, telemetry);
  }

  StudyReviewViewModel._(
    this._repository,
    this._timezoneProvider,
    this._telemetry,
  );

  final StudyReviewRepository _repository;
  final DeviceTimezoneProvider _timezoneProvider;
  final InteractionTelemetry _telemetry;

  StudyReviewPhase _phase = StudyReviewPhase.idle;
  VocabLanguage _language = VocabLanguage.english;
  List<VocabReviewCard> _cards = const [];
  int _index = 0;
  bool _revealed = false;
  bool _submitting = false;
  String? _errorMessage;
  String? _timezone;
  final Map<VocabRating, int> _tally = {
    for (final rating in VocabRating.values) rating: 0,
  };

  StudyReviewPhase get phase => _phase;
  VocabLanguage get language => _language;
  List<VocabReviewCard> get cards => _cards;
  int get index => _index;
  bool get revealed => _revealed;
  bool get submitting => _submitting;
  String? get errorMessage => _errorMessage;
  bool get isComplete =>
      _phase == StudyReviewPhase.ready && _index >= _cards.length;
  VocabReviewCard? get currentCard =>
      isComplete || _cards.isEmpty ? null : _cards[_index];
  int tallyFor(VocabRating rating) => _tally[rating] ?? 0;

  Future<void> initialize() async {
    if (_phase != StudyReviewPhase.idle) return;
    await load();
  }

  int _loadToken = 0;

  Future<void> load() async {
    if (_submitting) return;
    // Guard against overlapping loads (e.g. rapid language switches): only the
    // most recent load applies its result, so an earlier one can't resolve last
    // and overwrite the queue with a stale language.
    final token = ++_loadToken;
    _phase = StudyReviewPhase.loading;
    _errorMessage = null;
    notifyListeners();
    try {
      final cards = await _repository.reviewQueue(language: _language);
      if (token != _loadToken) return;
      _cards = cards;
      _index = 0;
      _revealed = false;
      for (final rating in VocabRating.values) {
        _tally[rating] = 0;
      }
      _phase = StudyReviewPhase.ready;
    } on Object catch (error) {
      if (token != _loadToken) return;
      _phase = StudyReviewPhase.error;
      _errorMessage = _messageFor(error);
    }
    notifyListeners();
  }

  Future<void> setLanguage(VocabLanguage value) async {
    if (value == _language || _submitting) return;
    _language = value;
    await load();
  }

  void reveal() {
    if (currentCard == null || _submitting) return;
    _revealed = true;
    _errorMessage = null;
    notifyListeners();
  }

  Future<void> rate(VocabRating rating) async {
    final card = currentCard;
    if (card == null || !_revealed || _submitting) return;
    _submitting = true;
    _errorMessage = null;
    notifyListeners();
    try {
      final timezone = _timezone ??= await _timezoneProvider
          .currentIdentifier();
      await _repository.recordReview(
        card: card,
        rating: rating,
        timezone: timezone,
      );
      _tally[rating] = tallyFor(rating) + 1;
      _index += 1;
      _revealed = false;
      _telemetry.record('study.rating.${rating.name}');
    } on Object catch (error) {
      _errorMessage = _messageFor(error);
    } finally {
      _submitting = false;
      notifyListeners();
    }
  }

  String _messageFor(Object error) {
    final text = error.toString().replaceFirst(
      RegExp(r'^\w+Exception:\s*'),
      '',
    );
    return text.isEmpty ? 'Study review is unavailable.' : text;
  }
}
