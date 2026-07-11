import 'package:flutter/foundation.dart';
import 'package:northstar/data/repositories/auth_repository.dart';
import 'package:northstar/data/services/mobile_auth_api.dart';
import 'package:northstar/domain/models/auth_session.dart';

enum AuthStatus { checking, signedOut, authenticating, signedIn }

class AuthViewModel extends ChangeNotifier {
  AuthViewModel(this._repository);

  final AuthRepository _repository;

  AuthStatus _status = AuthStatus.checking;
  AuthSession? _session;
  String? _errorMessage;

  AuthStatus get status => _status;
  AuthSession? get session => _session;
  String? get errorMessage => _errorMessage;
  bool get isSignedIn => _status == AuthStatus.signedIn;

  Future<void> restore() async {
    try {
      _session = await _repository.restore();
      _status = _session == null ? AuthStatus.signedOut : AuthStatus.signedIn;
    } catch (_) {
      _session = null;
      _status = AuthStatus.signedOut;
      _errorMessage = 'Could not reach Northstar. Check your connection.';
    }
    notifyListeners();
  }

  Future<bool> login({
    required String username,
    required String password,
  }) async {
    if (username.trim().isEmpty || password.isEmpty) {
      _errorMessage = 'Enter your username and password.';
      notifyListeners();
      return false;
    }

    _status = AuthStatus.authenticating;
    _errorMessage = null;
    notifyListeners();
    try {
      _session = await _repository.login(
        username: username.trim(),
        password: password,
      );
      _status = AuthStatus.signedIn;
      notifyListeners();
      return true;
    } on MobileAuthApiException catch (error) {
      _errorMessage = error.statusCode == 401
          ? 'The username or password is incorrect.'
          : error.message;
    } catch (_) {
      _errorMessage = 'Could not reach Northstar. Check your connection.';
    }
    _status = AuthStatus.signedOut;
    notifyListeners();
    return false;
  }

  Future<void> logout() async {
    try {
      await _repository.logout();
    } finally {
      _session = null;
      _errorMessage = null;
      _status = AuthStatus.signedOut;
      notifyListeners();
    }
  }

  void expireSession() {
    _session = null;
    _errorMessage = null;
    _status = AuthStatus.signedOut;
    notifyListeners();
  }
}
