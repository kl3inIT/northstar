import 'package:northstar/data/models/study_review_dtos.dart';
import 'package:northstar/data/services/authenticated_json_client.dart';
import 'package:northstar/domain/models/study_review_models.dart';

abstract interface class StudyReviewDataSource {
  Future<List<VocabReviewCardDto>> reviewQueue({
    required VocabLanguage language,
    String? deck,
    int limit = 20,
  });

  Future<void> recordReview({
    required String id,
    required VocabRating rating,
    required VocabReviewDirection direction,
    required DateTime previewedAt,
    required int schedulingVersion,
    required String timezone,
  });
}

class StudyReviewApi implements StudyReviewDataSource {
  const StudyReviewApi(this._client);

  final AuthenticatedJsonClient _client;

  @override
  Future<List<VocabReviewCardDto>> reviewQueue({
    required VocabLanguage language,
    String? deck,
    int limit = 20,
  }) async {
    final json = await _client.getList(
      '/api/study/vocab/review',
      query: {
        'language': language.name.toUpperCase(),
        if (deck != null && deck.trim().isNotEmpty) 'deck': deck.trim(),
        'limit': '$limit',
      },
    );
    return json.map(VocabReviewCardDto.new).toList(growable: false);
  }

  @override
  Future<void> recordReview({
    required String id,
    required VocabRating rating,
    required VocabReviewDirection direction,
    required DateTime previewedAt,
    required int schedulingVersion,
    required String timezone,
  }) async {
    await _client.postObject(
      '/api/study/vocab/${Uri.encodeComponent(id)}/reviews',
      timezone: timezone,
      body: {
        'rating': rating.name.toUpperCase(),
        'direction': direction.name.toUpperCase(),
        'previewedAt': previewedAt.toUtc().toIso8601String(),
        'schedulingVersion': schedulingVersion,
      },
    );
  }
}
