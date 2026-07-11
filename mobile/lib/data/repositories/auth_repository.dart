import 'package:northstar/data/services/mobile_auth_api.dart';
import 'package:northstar/data/services/refresh_token_store.dart';
import 'package:northstar/data/models/auth_tokens.dart';
import 'package:northstar/domain/models/auth_session.dart';

abstract interface class AuthRepository {
  String? get accessToken;

  Future<String?> refreshAccessToken();

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
  Future<String?>? _refreshingAccessToken;

  @override
  String? get accessToken => _accessToken;

  @override
  Future<AuthSession?> restore() async {
    final accessToken = await refreshAccessToken();
    if (accessToken == null) {
      return null;
    }
    return _session;
  }

  AuthSession? _session;

  @override
  Future<String?> refreshAccessToken() {
    final existing = _refreshingAccessToken;
    if (existing != null) {
      return existing;
    }
    final refresh = _rotateRefreshToken();
    _refreshingAccessToken = refresh;
    return refresh.whenComplete(() {
      if (identical(_refreshingAccessToken, refresh)) {
        _refreshingAccessToken = null;
      }
    });
  }

  Future<String?> _rotateRefreshToken() async {
    final refreshToken = await _refreshTokenStore.read();
    if (refreshToken == null) {
      return null;
    }
    try {
      final tokens = await _api.refresh(refreshToken);
      await _accept(tokens);
      return _accessToken;
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
    final session = AuthSession(username: tokens.username);
    _session = session;
    return session;
  }

  Future<void> _clearLocalSession() async {
    _accessToken = null;
    _session = null;
    await _refreshTokenStore.clear();
  }
}
