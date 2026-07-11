import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

abstract interface class RefreshTokenStore {
  Future<String?> read();

  Future<void> write(String token);

  Future<void> clear();
}

RefreshTokenStore createRefreshTokenStore() {
  if (kIsWeb) {
    return MemoryRefreshTokenStore();
  }
  return SecureRefreshTokenStore(const FlutterSecureStorage());
}

class SecureRefreshTokenStore implements RefreshTokenStore {
  SecureRefreshTokenStore(this._storage);

  static const _key = 'northstar.mobile.refresh_token';

  final FlutterSecureStorage _storage;

  @override
  Future<void> clear() => _storage.delete(key: _key);

  @override
  Future<String?> read() => _storage.read(key: _key);

  @override
  Future<void> write(String token) => _storage.write(key: _key, value: token);
}

class MemoryRefreshTokenStore implements RefreshTokenStore {
  String? _token;

  @override
  Future<void> clear() async => _token = null;

  @override
  Future<String?> read() async => _token;

  @override
  Future<void> write(String token) async => _token = token;
}
