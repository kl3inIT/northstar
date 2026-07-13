import 'package:northstar/data/services/study_review_api.dart';
import 'package:northstar/domain/models/study_review_models.dart';

abstract interface class StudyReviewRepository {
  Future<List<VocabReviewCard>> reviewQueue({
    required VocabLanguage language,
    String? deck,
    int limit = 20,
  });

  Future<void> recordReview({
    required VocabReviewCard card,
    required VocabRating rating,
    required String timezone,
  });
}

class RemoteStudyReviewRepository implements StudyReviewRepository {
  const RemoteStudyReviewRepository(this._api);

  final StudyReviewDataSource _api;

  @override
  Future<List<VocabReviewCard>> reviewQueue({
    required VocabLanguage language,
    String? deck,
    int limit = 20,
  }) async {
    final items = await _api.reviewQueue(
      language: language,
      deck: deck,
      limit: limit,
    );
    return List.unmodifiable(items.map((item) => item.toDomain()));
  }

  @override
  Future<void> recordReview({
    required VocabReviewCard card,
    required VocabRating rating,
    required String timezone,
  }) {
    return _api.recordReview(
      id: card.id,
      rating: rating,
      direction: card.direction,
      previewedAt: card.previewedAt,
      schedulingVersion: card.schedulingVersion,
      timezone: timezone,
    );
  }
}
