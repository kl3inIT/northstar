import 'dart:convert';

import 'package:northstar/domain/models/study_review_models.dart';

class VocabReviewCardDto {
  const VocabReviewCardDto(this.json);

  final Map<String, Object?> json;

  VocabReviewCard toDomain() {
    final metadata = _metadata(_optionalString(json, 'metadata'));
    final previews = _requiredList(
      json,
      'ratingPreviews',
    ).map(_map).map(_preview).toList(growable: false);
    return VocabReviewCard(
      id: _requiredString(json, 'id'),
      schedulingCardId: _requiredString(json, 'schedulingCardId'),
      schedulingVersion: _requiredInt(json, 'schedulingVersion'),
      direction: _direction(_requiredString(json, 'direction')),
      front: _requiredString(json, 'front'),
      back: _requiredString(json, 'back'),
      metadata: metadata,
      language: _language(_requiredString(json, 'language')),
      deck: _optionalString(json, 'deck'),
      recallProbability: _requiredDouble(json, 'recallProbability'),
      dueAt: DateTime.parse(_requiredString(json, 'dueAt')),
      schedulingState: _state(_requiredString(json, 'schedulingState')),
      lapseCount: _requiredInt(json, 'lapseCount'),
      leech: _requiredBool(json, 'leech'),
      reviewCount: _requiredInt(json, 'reviewCount'),
      previewedAt: DateTime.parse(_requiredString(json, 'previewedAt')),
      ratingPreviews: previews,
    );
  }

  VocabRatingPreview _preview(Map<String, Object?> value) {
    return VocabRatingPreview(
      rating: _rating(_requiredString(value, 'rating')),
      nextState: _state(_requiredString(value, 'nextState')),
      dueAt: DateTime.parse(_requiredString(value, 'dueAt')),
      intervalSeconds: _requiredInt(value, 'intervalSeconds'),
      intervalLabel: _requiredString(value, 'intervalLabel'),
    );
  }

  VocabReviewMetadata _metadata(String? value) {
    if (value == null || value.isEmpty) {
      return const VocabReviewMetadata();
    }
    try {
      final decoded = jsonDecode(value);
      if (decoded is! Map) {
        return const VocabReviewMetadata();
      }
      return VocabReviewMetadata(
        reading: decoded['reading'] is String
            ? decoded['reading'] as String
            : null,
        partOfSpeech: decoded['partOfSpeech'] is String
            ? decoded['partOfSpeech'] as String
            : null,
        example: decoded['example'] is String
            ? decoded['example'] as String
            : null,
      );
    } on FormatException {
      return const VocabReviewMetadata();
    }
  }
}

VocabLanguage _language(String value) => switch (value.toUpperCase()) {
  'ENGLISH' => VocabLanguage.english,
  'CHINESE' => VocabLanguage.chinese,
  _ => throw FormatException('Unsupported vocabulary language: $value'),
};

VocabReviewDirection _direction(String value) => switch (value.toUpperCase()) {
  'RECOGNITION' => VocabReviewDirection.recognition,
  'PRODUCTION' => VocabReviewDirection.production,
  _ => throw FormatException('Unsupported review direction: $value'),
};

VocabSchedulingState _state(String value) => switch (value.toUpperCase()) {
  'LEARNING' => VocabSchedulingState.learning,
  'REVIEW' => VocabSchedulingState.review,
  'RELEARNING' => VocabSchedulingState.relearning,
  _ => throw FormatException('Unsupported scheduling state: $value'),
};

VocabRating _rating(String value) => switch (value.toUpperCase()) {
  'AGAIN' => VocabRating.again,
  'HARD' => VocabRating.hard,
  'GOOD' => VocabRating.good,
  'EASY' => VocabRating.easy,
  _ => throw FormatException('Unsupported vocabulary rating: $value'),
};

Map<String, Object?> _map(Object? value) {
  if (value is Map<String, Object?>) return value;
  if (value is Map) {
    return value.map((key, value) => MapEntry(key.toString(), value));
  }
  throw const FormatException('Expected a JSON object.');
}

List<Object?> _requiredList(Map<String, Object?> json, String key) {
  final value = json[key];
  if (value is List) return value;
  throw FormatException('Expected list field "$key".');
}

String _requiredString(Map<String, Object?> json, String key) {
  final value = json[key];
  if (value is String && value.isNotEmpty) return value;
  throw FormatException('Expected string field "$key".');
}

String? _optionalString(Map<String, Object?> json, String key) {
  final value = json[key];
  if (value == null) return null;
  if (value is String) return value;
  throw FormatException('Expected optional string field "$key".');
}

int _requiredInt(Map<String, Object?> json, String key) {
  final value = json[key];
  if (value is int) return value;
  if (value is num) return value.toInt();
  throw FormatException('Expected integer field "$key".');
}

double _requiredDouble(Map<String, Object?> json, String key) {
  final value = json[key];
  if (value is num) return value.toDouble();
  throw FormatException('Expected number field "$key".');
}

bool _requiredBool(Map<String, Object?> json, String key) {
  final value = json[key];
  if (value is bool) return value;
  throw FormatException('Expected boolean field "$key".');
}
