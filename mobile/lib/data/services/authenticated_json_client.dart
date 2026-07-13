import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:northstar/data/services/authenticated_api_client.dart';

class AuthenticatedJsonClient {
  factory AuthenticatedJsonClient({
    required AuthenticatedApiClient authenticatedClient,
    required Uri baseUrl,
    Duration timeout = const Duration(seconds: 30),
  }) {
    return AuthenticatedJsonClient._(authenticatedClient, baseUrl, timeout);
  }

  const AuthenticatedJsonClient._(
    this._authenticated,
    this._baseUrl,
    this._timeout,
  ) : assert(_timeout > Duration.zero);

  final AuthenticatedApiClient _authenticated;
  final Uri _baseUrl;
  final Duration _timeout;

  Future<List<Map<String, Object?>>> getList(
    String path, {
    Map<String, String>? query,
    String? timezone,
  }) async {
    final value = await _send('GET', path, query: query, timezone: timezone);
    if (value is! List) {
      throw const FormatException('Expected a JSON array.');
    }
    return value.map(_asObject).toList(growable: false);
  }

  Future<Map<String, Object?>> getObject(
    String path, {
    Map<String, String>? query,
    String? timezone,
  }) async {
    return _asObject(
      await _send('GET', path, query: query, timezone: timezone),
    );
  }

  Future<Map<String, Object?>> postObject(
    String path, {
    required Map<String, Object?> body,
    Map<String, String>? query,
    String? timezone,
  }) async {
    return _writeObject(
      'POST',
      path,
      body: body,
      query: query,
      timezone: timezone,
    );
  }

  Future<Map<String, Object?>> putObject(
    String path, {
    required Map<String, Object?> body,
    Map<String, String>? query,
    String? timezone,
  }) {
    return _writeObject(
      'PUT',
      path,
      body: body,
      query: query,
      timezone: timezone,
    );
  }

  Future<Map<String, Object?>> patchObject(
    String path, {
    required Map<String, Object?> body,
    Map<String, String>? query,
    String? timezone,
  }) {
    return _writeObject(
      'PATCH',
      path,
      body: body,
      query: query,
      timezone: timezone,
    );
  }

  Future<Map<String, Object?>> deleteObject(
    String path, {
    Map<String, String>? query,
    String? timezone,
  }) {
    return _writeObject('DELETE', path, query: query, timezone: timezone);
  }

  Future<Map<String, Object?>> _writeObject(
    String method,
    String path, {
    Map<String, Object?>? body,
    Map<String, String>? query,
    String? timezone,
  }) async {
    return _asObject(
      await _send(method, path, query: query, timezone: timezone, body: body),
    );
  }

  Future<Object?> _send(
    String method,
    String path, {
    Map<String, String>? query,
    String? timezone,
    Map<String, Object?>? body,
  }) {
    return _sendWithoutTimeout(
      method,
      path,
      query: query,
      timezone: timezone,
      body: body,
    ).timeout(
      _timeout,
      onTimeout: () => throw const AuthenticatedJsonTimeoutException(),
    );
  }

  Future<Object?> _sendWithoutTimeout(
    String method,
    String path, {
    Map<String, String>? query,
    String? timezone,
    Map<String, Object?>? body,
  }) async {
    final response = await _authenticated.send((token) {
      final resolved = _baseUrl.resolve(path);
      final uri = query == null
          ? resolved
          : resolved.replace(queryParameters: query);
      final request = http.Request(method, uri)
        ..headers['authorization'] = 'Bearer $token'
        ..headers['accept'] = 'application/json';
      if (timezone != null) {
        request.headers['X-Timezone'] = timezone;
      }
      if (body != null) {
        request.headers['content-type'] = 'application/json';
        request.body = jsonEncode(body);
      }
      return request;
    });
    final text = await utf8.decoder.bind(response.stream).join();
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw AuthenticatedJsonException(
        response.statusCode,
        _problemMessage(text),
      );
    }
    if (text.trim().isEmpty) {
      throw const FormatException('Expected a JSON response body.');
    }
    return jsonDecode(text);
  }

  Map<String, Object?> _asObject(Object? value) {
    if (value is Map<String, Object?>) {
      return value;
    }
    if (value is Map) {
      return value.map((key, value) => MapEntry(key.toString(), value));
    }
    throw const FormatException('Expected a JSON object.');
  }

  String _problemMessage(String body) {
    try {
      final value = jsonDecode(body);
      if (value is Map) {
        for (final key in ['detail', 'message', 'title']) {
          final message = value[key];
          if (message is String && message.trim().isNotEmpty) {
            return message;
          }
        }
      }
    } on FormatException {
      // Fall through to the stable message below.
    }
    return 'Northstar could not complete the request.';
  }
}

class AuthenticatedJsonException implements Exception {
  const AuthenticatedJsonException(this.statusCode, this.message);

  final int statusCode;
  final String message;

  @override
  String toString() => message;
}

class AuthenticatedJsonTimeoutException implements Exception {
  const AuthenticatedJsonTimeoutException();

  @override
  String toString() => 'Northstar took too long to respond. Try again.';
}
