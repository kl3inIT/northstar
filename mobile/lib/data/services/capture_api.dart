import 'dart:convert';
import 'dart:io';

import 'package:http/http.dart' as http;
import 'package:http_parser/http_parser.dart';
import 'package:northstar/data/models/capture_dtos.dart';
import 'package:northstar/data/services/authenticated_api_client.dart';
import 'package:northstar/domain/models/capture_models.dart';

abstract interface class CaptureDataSource {
  Future<CaptureDraftDto> draftText({
    required String text,
    CaptureKind? forcedKind,
  });

  Future<CaptureDraftDto> draftReceipt(ReceiptUpload upload);

  Future<List<Map<String, Object?>>> listDisciplines();

  Future<Map<String, Object?>> createTask(Map<String, Object?> body);

  Future<Map<String, Object?>> createNote(Map<String, Object?> body);

  Future<Map<String, Object?>> createEvent(Map<String, Object?> body);

  Future<List<Map<String, Object?>>> createTransactions(
    Map<String, Object?> body,
  );

  Future<List<Map<String, Object?>>> createStudySessions(
    Map<String, Object?> body,
  );

  Future<List<Map<String, Object?>>> createVocabCards(
    Map<String, Object?> body,
  );

  Future<void> delete(String path);
}

class CaptureApi implements CaptureDataSource {
  factory CaptureApi({
    required AuthenticatedApiClient authenticatedClient,
    required Uri baseUrl,
  }) {
    return CaptureApi._(authenticatedClient, baseUrl);
  }

  const CaptureApi._(this._authenticated, this._baseUrl);

  final AuthenticatedApiClient _authenticated;
  final Uri _baseUrl;

  @override
  Future<CaptureDraftDto> draftText({
    required String text,
    CaptureKind? forcedKind,
  }) async {
    final json = await _postObject('/api/capture/draft', {
      'text': text,
      if (forcedKind != null) 'kind': forcedKind.name.toUpperCase(),
    });
    return CaptureDraftDto.fromJson(json);
  }

  @override
  Future<CaptureDraftDto> draftReceipt(ReceiptUpload upload) async {
    final response = await _authenticated.send((token) {
      final request = http.MultipartRequest(
        'POST',
        _baseUrl.resolve('/api/capture/receipt'),
      );
      request.headers[HttpHeaders.authorizationHeader] = 'Bearer $token';
      request.headers[HttpHeaders.acceptHeader] = 'application/json';
      request.files.add(
        http.MultipartFile.fromBytes(
          'image',
          upload.bytes,
          filename: upload.filename,
          contentType: MediaType.parse(upload.mimeType),
        ),
      );
      return request;
    });
    return CaptureDraftDto.fromJson(await _readObject(response));
  }

  @override
  Future<List<Map<String, Object?>>> listDisciplines() async {
    final response = await _authenticated.send(
      (token) => _request('GET', '/api/disciplines', token),
    );
    return _readObjectList(response);
  }

  @override
  Future<Map<String, Object?>> createTask(Map<String, Object?> body) {
    return _postObject('/api/tasks', body);
  }

  @override
  Future<Map<String, Object?>> createNote(Map<String, Object?> body) {
    return _postObject('/api/notes', body);
  }

  @override
  Future<Map<String, Object?>> createEvent(Map<String, Object?> body) {
    return _postObject('/api/calendar/events', body);
  }

  @override
  Future<List<Map<String, Object?>>> createTransactions(
    Map<String, Object?> body,
  ) {
    return _postObjectList('/api/finance', body);
  }

  @override
  Future<List<Map<String, Object?>>> createStudySessions(
    Map<String, Object?> body,
  ) {
    return _postObjectList('/api/study/sessions', body);
  }

  @override
  Future<List<Map<String, Object?>>> createVocabCards(
    Map<String, Object?> body,
  ) {
    return _postObjectList('/api/study/vocab', body);
  }

  Future<List<Map<String, Object?>>> _postObjectList(
    String path,
    Map<String, Object?> body,
  ) async {
    final response = await _authenticated.send((token) {
      return _jsonRequest('POST', path, token, body);
    });
    return _readObjectList(response);
  }

  @override
  Future<void> delete(String path) async {
    final response = await _authenticated.send(
      (token) => _request('DELETE', path, token),
    );
    if (response.statusCode == 404) {
      await response.stream.drain<void>();
      return;
    }
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw await CaptureApiException.fromResponse(response);
    }
    await response.stream.drain<void>();
  }

  Future<Map<String, Object?>> _postObject(
    String path,
    Map<String, Object?> body,
  ) async {
    final response = await _authenticated.send((token) {
      return _jsonRequest('POST', path, token, body);
    });
    return _readObject(response);
  }

  http.Request _request(String method, String path, String token) {
    return http.Request(method, _baseUrl.resolve(path))
      ..headers[HttpHeaders.authorizationHeader] = 'Bearer $token'
      ..headers[HttpHeaders.acceptHeader] = 'application/json';
  }

  http.Request _jsonRequest(
    String method,
    String path,
    String token,
    Map<String, Object?> body,
  ) {
    return _request(method, path, token)
      ..headers[HttpHeaders.contentTypeHeader] = 'application/json'
      ..body = jsonEncode(body);
  }

  Future<Map<String, Object?>> _readObject(
    http.StreamedResponse response,
  ) async {
    final body = await _readBody(response);
    final decoded = jsonDecode(body);
    if (decoded is! Map<Object?, Object?>) {
      throw const FormatException('Capture response is not an object.');
    }
    return _normalize(decoded);
  }

  Future<List<Map<String, Object?>>> _readObjectList(
    http.StreamedResponse response,
  ) async {
    final body = await _readBody(response);
    final decoded = jsonDecode(body);
    if (decoded is! List<Object?>) {
      throw const FormatException('Capture response is not a list.');
    }
    return decoded
        .map((item) {
          if (item is! Map<Object?, Object?>) {
            throw const FormatException('Capture list entry is not an object.');
          }
          return _normalize(item);
        })
        .toList(growable: false);
  }

  Future<String> _readBody(http.StreamedResponse response) async {
    final chunks = await response.stream
        .timeout(const Duration(seconds: 120))
        .toList();
    final body = utf8.decode(chunks.expand((chunk) => chunk).toList());
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw CaptureApiException.fromBody(body, statusCode: response.statusCode);
    }
    return body;
  }
}

class CaptureApiException implements Exception {
  const CaptureApiException(this.message, {required this.statusCode});

  factory CaptureApiException.fromBody(String body, {required int statusCode}) {
    var message = 'Northstar could not complete the capture.';
    try {
      final decoded = jsonDecode(body);
      if (decoded is Map<String, Object?> && decoded['detail'] is String) {
        message = decoded['detail']! as String;
      }
    } on FormatException {
      // Keep the stable fallback for proxy and non-JSON responses.
    }
    return CaptureApiException(message, statusCode: statusCode);
  }

  static Future<CaptureApiException> fromResponse(
    http.StreamedResponse response,
  ) async {
    return CaptureApiException.fromBody(
      await response.stream.bytesToString(),
      statusCode: response.statusCode,
    );
  }

  final String message;
  final int statusCode;

  @override
  String toString() => message;
}

Map<String, Object?> _normalize(Map<Object?, Object?> source) {
  return source.map((key, value) => MapEntry(key.toString(), value));
}
