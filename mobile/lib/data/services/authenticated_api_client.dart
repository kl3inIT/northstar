import 'dart:async';

import 'package:http/http.dart' as http;

class AuthenticatedApiClient {
  factory AuthenticatedApiClient({
    required http.Client client,
    required String? Function() accessToken,
    required Future<String?> Function() refreshAccessToken,
    required void Function() onUnauthorized,
  }) {
    return AuthenticatedApiClient._(
      client,
      accessToken,
      refreshAccessToken,
      onUnauthorized,
    );
  }

  const AuthenticatedApiClient._(
    this._client,
    this._accessToken,
    this._refreshAccessToken,
    this._onUnauthorized,
  );

  final http.Client _client;
  final String? Function() _accessToken;
  final Future<String?> Function() _refreshAccessToken;
  final void Function() _onUnauthorized;

  Future<http.StreamedResponse> send(
    http.BaseRequest Function(String token) createRequest,
  ) async {
    var token = _accessToken() ?? await _refreshAccessToken();
    if (token == null) {
      _onUnauthorized();
      throw const AuthenticatedApiUnauthorizedException();
    }

    var response = await _client.send(createRequest(token));
    if (response.statusCode != 401) {
      return response;
    }
    await response.stream.drain<void>();
    token = await _refreshAccessToken();
    if (token == null) {
      _onUnauthorized();
      throw const AuthenticatedApiUnauthorizedException();
    }
    response = await _client.send(createRequest(token));
    if (response.statusCode == 401) {
      await response.stream.drain<void>();
      _onUnauthorized();
      throw const AuthenticatedApiUnauthorizedException();
    }
    return response;
  }
}

class AuthenticatedApiUnauthorizedException implements Exception {
  const AuthenticatedApiUnauthorizedException();

  @override
  String toString() => 'Your session expired. Sign in again.';
}
