import 'package:northstar/domain/models/note_detail.dart';

class NoteDetailDto {
  const NoteDetailDto(this.json);

  final Map<String, Object?> json;

  NoteDetail toDomain() {
    return NoteDetail(
      id: _requiredString('id'),
      title: _requiredString('title'),
      slug: _requiredString('slug'),
      folderPath: _requiredString('folderPath', allowEmpty: true),
      contentMarkdown: _requiredString('contentMarkdown', allowEmpty: true),
      tags: _requiredList('tags')
          .map((item) {
            if (item is String) return item;
            throw const FormatException('Expected string tag.');
          })
          .toList(growable: false),
      status: _requiredString('status'),
      updatedAt: DateTime.parse(_requiredString('updatedAt')),
    );
  }

  String _requiredString(String key, {bool allowEmpty = false}) {
    final value = json[key];
    if (value is String && (allowEmpty || value.isNotEmpty)) return value;
    throw FormatException('Expected string field "$key".');
  }

  List<Object?> _requiredList(String key) {
    final value = json[key];
    if (value is List) return value;
    throw FormatException('Expected list field "$key".');
  }
}
