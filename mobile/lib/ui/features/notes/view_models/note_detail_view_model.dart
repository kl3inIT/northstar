import 'package:flutter/foundation.dart';
import 'package:northstar/data/repositories/note_detail_repository.dart';
import 'package:northstar/domain/models/note_detail.dart';

enum NoteDetailPhase { loading, ready, error }

class NoteDetailViewModel extends ChangeNotifier {
  factory NoteDetailViewModel({
    required NoteDetailRepository repository,
    required String slug,
  }) {
    return NoteDetailViewModel._(repository, slug);
  }

  NoteDetailViewModel._(this._repository, this.slug);

  final NoteDetailRepository _repository;
  final String slug;

  NoteDetailPhase _phase = NoteDetailPhase.loading;
  NoteDetail? _note;
  String? _errorMessage;

  NoteDetailPhase get phase => _phase;
  NoteDetail? get note => _note;
  String? get errorMessage => _errorMessage;

  Future<void> load() async {
    _phase = NoteDetailPhase.loading;
    _errorMessage = null;
    notifyListeners();
    try {
      _note = await _repository.get(slug);
      _phase = NoteDetailPhase.ready;
    } on Exception {
      _phase = NoteDetailPhase.error;
      _errorMessage = 'This note is unavailable.';
    }
    notifyListeners();
  }
}
