import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:northstar/data/models/auth_tokens.dart';

class MobileAuthApi {
  MobileAuthApi({required http.Client client, required Uri baseUrl})
    : this._(client, baseUrl);

  MobileAuthApi._(this._client, this._baseUrl);

  final http.Client _client;
  final Uri _baseUrl;

  static const _timeout = Duration(seconds: 20);

  Future<AuthTokens> login({
    required String username,
    required String password,
  }) async {
    final response = await _client
        .post(
          _resolve('/api/auth/mobile/login'),
          headers: _jsonHeaders,
          body: jsonEncode({'username': username, 'password': password}),
        )
        .timeout(_timeout);
    return _tokensFrom(response);
  }

  Future<AuthTokens> refresh(String refreshToken) async {
    final response = await _client
        .post(
          _resolve('/api/auth/mobile/refresh'),
          headers: _jsonHeaders,
          body: jsonEncode({'refreshToken': refreshToken}),
        )
        .timeout(_timeout);
    return _tokensFrom(response);
  }

  Future<void> logout(String refreshToken) async {
    final response = await _client
        .post(
          _resolve('/api/auth/mobile/logout'),
          headers: _jsonHeaders,
          body: jsonEncode({'refreshToken': refreshToken}),
        )
        .timeout(_timeout);
    if (response.statusCode != 204) {
      throw MobileAuthApiException.fromResponse(response);
    }
  }

  AuthTokens _tokensFrom(http.Response response) {
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw MobileAuthApiException.fromResponse(response);
    }
    final decoded = jsonDecode(response.body);
    if (decoded is! Map<String, Object?>) {
      throw const FormatException('The authentication response is invalid.');
    }
    return AuthTokens.fromJson(decoded);
  }

  Uri _resolve(String path) => _baseUrl.resolve(path);

  static const _jsonHeaders = {'Content-Type': 'application/json'};
}

class MobileAuthApiException implements Exception {
  const MobileAuthApiException(this.message, {required this.statusCode});

  factory MobileAuthApiException.fromResponse(http.Response response) {
    var message = 'Authentication request failed.';
    try {
      final decoded = jsonDecode(response.body);
      if (decoded is Map<String, Object?>) {
        final detail = decoded['detail'];
        if (detail is String) {
          message = detail;
        }
      }
    } on FormatException {
      // Keep the stable user-facing fallback for non-JSON proxy responses.
    }
    return MobileAuthApiException(message, statusCode: response.statusCode);
  }

  final String message;
  final int statusCode;

  @override
  String toString() => message;
}
