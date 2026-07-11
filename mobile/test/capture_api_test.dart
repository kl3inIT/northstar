import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:northstar/data/services/authenticated_api_client.dart';
import 'package:northstar/data/services/capture_api.dart';
import 'package:northstar/domain/models/capture_models.dart';

void main() {
  test('sends a typed forced-kind draft request with Bearer auth', () async {
    late http.BaseRequest captured;
    final client = _HandlerClient((request) async {
      captured = request;
      return _response(
        jsonEncode({
          'kind': 'TASK',
          'task': {'title': 'Submit essay', 'notes': ''},
        }),
      );
    });
    final api = _api(client);

    final draft = await api.draftText(
      text: 'Submit my essay',
      forcedKind: CaptureKind.task,
    );

    expect(captured.url.path, '/api/capture/draft');
    expect(captured.headers['authorization'], 'Bearer access-token');
    final body = jsonDecode((captured as http.Request).body) as Map;
    expect(body['kind'], 'TASK');
    expect(draft.kind, CaptureKind.task);
    expect(draft.task?.title, 'Submit essay');
  });

  test('uploads receipt bytes with an explicit image content type', () async {
    late http.MultipartRequest captured;
    final client = _HandlerClient((request) async {
      captured = request as http.MultipartRequest;
      return _response(
        jsonEncode({
          'kind': 'EXPENSE',
          'expense': {
            'items': [
              {'type': 'EXPENSE', 'amount': 12000, 'description': 'Coffee'},
            ],
          },
        }),
      );
    });
    final api = _api(client);

    await api.draftReceipt(
      const ReceiptUpload(
        bytes: [1, 2, 3],
        filename: 'receipt.png',
        mimeType: 'image/png',
      ),
    );

    expect(captured.url.path, '/api/capture/receipt');
    expect(captured.files.single.field, 'image');
    expect(captured.files.single.filename, 'receipt.png');
    expect(captured.files.single.contentType.toString(), 'image/png');
  });

  test('treats an already deleted capture as a successful undo', () async {
    late http.BaseRequest captured;
    final client = _HandlerClient((request) async {
      captured = request;
      return http.StreamedResponse(const Stream.empty(), 404);
    });

    await _api(client).delete('/api/finance/transaction-1');

    expect(captured.method, 'DELETE');
    expect(captured.headers['authorization'], 'Bearer access-token');
  });
}

CaptureApi _api(http.Client client) {
  return CaptureApi(
    authenticatedClient: AuthenticatedApiClient(
      client: client,
      accessToken: () => 'access-token',
      refreshAccessToken: () async => null,
      onUnauthorized: () {},
    ),
    baseUrl: Uri.parse('https://northstar.example'),
  );
}

http.StreamedResponse _response(String body) {
  return http.StreamedResponse(Stream.value(utf8.encode(body)), 200);
}

class _HandlerClient extends http.BaseClient {
  _HandlerClient(this._handler);

  final Future<http.StreamedResponse> Function(http.BaseRequest request)
  _handler;

  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) {
    return _handler(request);
  }
}
