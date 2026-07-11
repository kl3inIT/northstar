import 'package:northstar/data/services/mobile_auth_api.dart';
import 'package:northstar/data/services/refresh_token_store.dart';
import 'package:northstar/data/models/auth_tokens.dart';
import 'package:northstar/domain/models/auth_session.dart';

abstract interface class AuthRepository {
  String? get accessToken;

  Future<AuthSession?> restore();

  Future<AuthSession> login({
    required String username,
    required String password,
  });

  Future<void> logout();
}

class MobileAuthRepository implements AuthRepository {
  MobileAuthRepository({
    required MobileAuthApi api,
    required RefreshTokenStore refreshTokenStore,
  }) : this._(api, refreshTokenStore);

  MobileAuthRepository._(this._api, this._refreshTokenStore);

  final MobileAuthApi _api;
  final RefreshTokenStore _refreshTokenStore;

  String? _accessToken;

  @override
  String? get accessToken => _accessToken;

  @override
  Future<AuthSession?> restore() async {
    final refreshToken = await _refreshTokenStore.read();
    if (refreshToken == null) {
      return null;
    }
    try {
      final tokens = await _api.refresh(refreshToken);
      return _accept(tokens);
    } on MobileAuthApiException catch (error) {
      if (error.statusCode == 401) {
        await _clearLocalSession();
        return null;
      }
      rethrow;
    }
  }

  @override
  Future<AuthSession> login({
    required String username,
    required String password,
  }) async {
    final tokens = await _api.login(username: username, password: password);
    return _accept(tokens);
  }

  @override
  Future<void> logout() async {
    final refreshToken = await _refreshTokenStore.read();
    try {
      if (refreshToken != null) {
        await _api.logout(refreshToken);
      }
    } finally {
      await _clearLocalSession();
    }
  }

  Future<AuthSession> _accept(AuthTokens tokens) async {
    _accessToken = tokens.accessToken;
    await _refreshTokenStore.write(tokens.refreshToken);
    return AuthSession(username: tokens.username);
  }

  Future<void> _clearLocalSession() async {
    _accessToken = null;
    await _refreshTokenStore.clear();
  }
}
