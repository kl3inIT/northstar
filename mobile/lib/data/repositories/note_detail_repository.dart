import 'package:northstar/data/services/note_detail_api.dart';
import 'package:northstar/domain/models/note_detail.dart';

abstract interface class NoteDetailRepository {
  Future<NoteDetail> get(String slug);
}

class RemoteNoteDetailRepository implements NoteDetailRepository {
  const RemoteNoteDetailRepository(this._api);

  final NoteDetailDataSource _api;

  @override
  Future<NoteDetail> get(String slug) async {
    return (await _api.get(slug)).toDomain();
  }
}
