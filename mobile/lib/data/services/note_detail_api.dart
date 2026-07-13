import 'package:northstar/data/models/note_detail_dto.dart';
import 'package:northstar/data/services/authenticated_json_client.dart';

abstract interface class NoteDetailDataSource {
  Future<NoteDetailDto> get(String slug);
}

class NoteDetailApi implements NoteDetailDataSource {
  const NoteDetailApi(this._client);

  final AuthenticatedJsonClient _client;

  @override
  Future<NoteDetailDto> get(String slug) async {
    return NoteDetailDto(
      await _client.getObject('/api/notes/${Uri.encodeComponent(slug)}'),
    );
  }
}
