enum VocabLanguage { english, chinese }

enum VocabReviewDirection { recognition, production }

enum VocabSchedulingState { learning, review, relearning }

enum VocabRating { again, hard, good, easy }

class VocabRatingPreview {
  const VocabRatingPreview({
    required this.rating,
    required this.nextState,
    required this.dueAt,
    required this.intervalSeconds,
    required this.intervalLabel,
  });

  final VocabRating rating;
  final VocabSchedulingState nextState;
  final DateTime dueAt;
  final int intervalSeconds;
  final String intervalLabel;
}

class VocabReviewMetadata {
  const VocabReviewMetadata({this.reading, this.partOfSpeech, this.example});

  final String? reading;
  final String? partOfSpeech;
  final String? example;
}

class VocabReviewCard {
  const VocabReviewCard({
    required this.id,
    required this.schedulingCardId,
    required this.schedulingVersion,
    required this.direction,
    required this.front,
    required this.back,
    required this.metadata,
    required this.language,
    required this.recallProbability,
    required this.dueAt,
    required this.schedulingState,
    required this.lapseCount,
    required this.leech,
    required this.reviewCount,
    required this.previewedAt,
    required this.ratingPreviews,
    this.deck,
  });

  final String id;
  final String schedulingCardId;
  final int schedulingVersion;
  final VocabReviewDirection direction;
  final String front;
  final String back;
  final VocabReviewMetadata metadata;
  final VocabLanguage language;
  final String? deck;
  final double recallProbability;
  final DateTime dueAt;
  final VocabSchedulingState schedulingState;
  final int lapseCount;
  final bool leech;
  final int reviewCount;
  final DateTime previewedAt;
  final List<VocabRatingPreview> ratingPreviews;

  bool get isProduction => direction == VocabReviewDirection.production;

  String get prompt => isProduction ? back : front;

  String get answer => isProduction ? front : back;

  VocabRatingPreview? previewFor(VocabRating rating) {
    for (final preview in ratingPreviews) {
      if (preview.rating == rating) {
        return preview;
      }
    }
    return null;
  }
}
