import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:http/http.dart' as http;
import 'package:northstar/data/models/assistant_dtos.dart';
import 'package:northstar/data/services/authenticated_api_client.dart';

class AssistantApi {
  factory AssistantApi({
    required http.Client client,
    required Uri baseUrl,
    required String? Function() accessToken,
    required Future<String?> Function() refreshAccessToken,
    required void Function() onUnauthorized,
  }) {
    return AssistantApi._(
      AuthenticatedApiClient(
        client: client,
        accessToken: accessToken,
        refreshAccessToken: refreshAccessToken,
        onUnauthorized: onUnauthorized,
      ),
      baseUrl,
    );
  }

  AssistantApi._(this._authenticated, this._baseUrl);

  final AuthenticatedApiClient _authenticated;
  final Uri _baseUrl;

  Future<List<AssistantConversationDto>> listConversations() async {
    final response = await _authenticated.send(
      (token) => _request('GET', '/api/assistant/conversations', token),
    );
    final body = await _readBody(response);
    final decoded = jsonDecode(body);
    if (decoded is! List<Object?>) {
      throw const FormatException('Conversation response is not a list.');
    }
    return decoded
        .map((item) {
          if (item is! Map<Object?, Object?>) {
            throw const FormatException('Conversation entry is not an object.');
          }
          return AssistantConversationDto.fromJson(_normalizeMap(item));
        })
        .toList(growable: false);
  }

  Future<List<AssistantHistoryMessageDto>> history(
    String conversationId,
  ) async {
    final uri = _resolve(
      '/api/assistant/history',
    ).replace(queryParameters: {'conversationId': conversationId});
    final response = await _authenticated.send(
      (token) => _requestForUri('GET', uri, token),
    );
    final body = await _readBody(response);
    final decoded = jsonDecode(body);
    if (decoded is! List<Object?>) {
      throw const FormatException('History response is not a list.');
    }
    return decoded
        .map((item) {
          if (item is! Map<Object?, Object?>) {
            throw const FormatException('History entry is not an object.');
          }
          return AssistantHistoryMessageDto.fromJson(_normalizeMap(item));
        })
        .toList(growable: false);
  }

  Future<AssistantModelSelectionDto> conversationModel(
    String conversationId,
  ) async {
    final response = await _authenticated.send(
      (token) => _request(
        'GET',
        '/api/assistant/conversations/$conversationId/model',
        token,
      ),
    );
    final decoded = jsonDecode(await _readBody(response));
    if (decoded is! Map<Object?, Object?>) {
      throw const FormatException('Assistant model response is not an object.');
    }
    return AssistantModelSelectionDto.fromJson(_normalizeMap(decoded));
  }

  Future<List<AssistantModelOptionDto>> models(String gatewayId) async {
    final response = await _authenticated.send(
      (token) =>
          _request('GET', '/api/settings/ai/gateways/$gatewayId/models', token),
    );
    final decoded = jsonDecode(await _readBody(response));
    if (decoded is! List<Object?>) {
      throw const FormatException('Assistant model catalog is not a list.');
    }
    return decoded
        .map((item) {
          if (item is! Map<Object?, Object?>) {
            throw const FormatException(
              'Assistant model entry is not an object.',
            );
          }
          return AssistantModelOptionDto.fromJson(_normalizeMap(item));
        })
        .toList(growable: false);
  }

  Future<AssistantModelSelectionDto> updateConversationModel(
    String conversationId,
    AssistantModelSelectionDto selection,
  ) async {
    final response = await _authenticated.send((token) {
      final request = _request(
        'PUT',
        '/api/assistant/conversations/$conversationId/model',
        token,
      );
      request.headers[HttpHeaders.contentTypeHeader] = 'application/json';
      request.body = jsonEncode({
        'gatewayId': selection.gatewayId,
        'modelId': selection.modelId,
      });
      return request;
    });
    final decoded = jsonDecode(await _readBody(response));
    if (decoded is! Map<Object?, Object?>) {
      throw const FormatException('Assistant model response is not an object.');
    }
    return AssistantModelSelectionDto.fromJson(_normalizeMap(decoded));
  }

  Stream<AssistantStreamFrame> streamTurn({
    required String conversationId,
    required String message,
  }) async* {
    final response = await _authenticated.send((token) {
      final request = _request('POST', '/api/assistant/chat', token);
      request.headers[HttpHeaders.acceptHeader] = 'text/event-stream';
      request.headers[HttpHeaders.contentTypeHeader] = 'application/json';
      request.body = jsonEncode({
        'message': message,
        'conversationId': conversationId,
      });
      return request;
    });
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw await AssistantApiException.fromStreamedResponse(response);
    }

    final dataLines = <String>[];
    final lines = response.stream
        .transform(utf8.decoder)
        .transform(const LineSplitter())
        .timeout(const Duration(seconds: 280));
    await for (final line in lines) {
      if (line.isEmpty) {
        final frame = _decodeSseData(dataLines);
        dataLines.clear();
        if (frame == null) {
          continue;
        }
        yield frame;
        if (frame is AssistantDoneFrame) {
          return;
        }
        continue;
      }
      if (line.startsWith('data:')) {
        dataLines.add(line.substring(5).trimLeft());
      }
    }

    final finalFrame = _decodeSseData(dataLines);
    if (finalFrame != null) {
      yield finalFrame;
      if (finalFrame is AssistantDoneFrame) {
        return;
      }
    }
    throw const AssistantApiException(
      'The assistant stream ended before completion.',
      statusCode: 502,
    );
  }

  AssistantStreamFrame? _decodeSseData(List<String> dataLines) {
    if (dataLines.isEmpty) {
      return null;
    }
    final data = dataLines.join('\n');
    if (data == '[DONE]') {
      return const AssistantDoneFrame();
    }
    final decoded = jsonDecode(data);
    if (decoded is! Map<Object?, Object?>) {
      throw const FormatException('Assistant stream frame is not an object.');
    }
    return AssistantStreamFrame.fromJson(_normalizeMap(decoded));
  }

  http.Request _request(String method, String path, String token) {
    return _requestForUri(method, _resolve(path), token);
  }

  http.Request _requestForUri(String method, Uri uri, String token) {
    return http.Request(method, uri)
      ..headers[HttpHeaders.authorizationHeader] = 'Bearer $token'
      ..headers[HttpHeaders.acceptHeader] = 'application/json';
  }

  Future<String> _readBody(http.StreamedResponse response) async {
    final body = await response.stream.bytesToString();
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw AssistantApiException.fromBody(
        body,
        statusCode: response.statusCode,
      );
    }
    return body;
  }

  Uri _resolve(String path) => _baseUrl.resolve(path);
}

class AssistantApiException implements Exception {
  const AssistantApiException(this.message, {required this.statusCode});

  factory AssistantApiException.fromBody(
    String body, {
    required int statusCode,
  }) {
    var message = 'Northstar could not complete the request.';
    try {
      final decoded = jsonDecode(body);
      if (decoded is Map<String, Object?> && decoded['detail'] is String) {
        message = decoded['detail']! as String;
      }
    } on FormatException {
      // Keep a stable message for proxy and non-JSON responses.
    }
    return AssistantApiException(message, statusCode: statusCode);
  }

  static Future<AssistantApiException> fromStreamedResponse(
    http.StreamedResponse response,
  ) async {
    return AssistantApiException.fromBody(
      await response.stream.bytesToString(),
      statusCode: response.statusCode,
    );
  }

  final String message;
  final int statusCode;

  @override
  String toString() => message;
}

class AssistantUnauthorizedException extends AssistantApiException {
  const AssistantUnauthorizedException()
    : super('Your session expired. Sign in again.', statusCode: 401);
}

Map<String, Object?> _normalizeMap(Map<Object?, Object?> source) {
  return source.map((key, value) => MapEntry(key.toString(), value));
}
