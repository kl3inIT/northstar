import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:northstar/data/models/capture_dtos.dart';
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

  test('parses current study and vocabulary draft kinds', () async {
    final responses = <String>[
      jsonEncode({
        'kind': 'STUDY',
        'study': {
          'items': [
            {
              'skill': 'Listening',
              'kind': 'PRACTICE',
              'durationMinutes': '25',
              'scoreRaw': '18',
              'scoreMax': '25',
              'notes': 'HSK4',
              'occurredOn': '2026-07-13',
              'disciplineName': 'IELTS',
            },
          ],
        },
      }),
      jsonEncode({
        'kind': 'VOCAB',
        'vocab': {
          'items': [
            {
              'front': 'meticulous',
              'back': 'tỉ mỉ',
              'reading': '/məˈtɪkjələs/',
              'partOfSpeech': 'adjective',
              'example': '',
              'language': 'ENGLISH',
              'deck': 'IELTS',
              'disciplineName': 'IELTS',
            },
          ],
        },
      }),
    ];
    final client = _HandlerClient(
      (_) async => _response(responses.removeAt(0)),
    );
    final api = _api(client);

    final study = await api.draftText(text: 'studied');
    final vocab = await api.draftText(text: 'meticulous = tỉ mỉ');

    expect(study.kind, CaptureKind.study);
    expect(study.study?.items.single.scoreRaw, '18');
    expect(vocab.kind, CaptureKind.vocab);
    expect(vocab.vocab?.items.single.language, 'ENGLISH');
  });

  test('reports an explicit unsupported capture kind', () async {
    final client = _HandlerClient((_) async {
      return _response(jsonEncode({'kind': 'BOOKMARK'}));
    });

    await expectLater(
      _api(client).draftText(text: 'save this'),
      throwsA(
        isA<UnsupportedCaptureKindException>().having(
          (error) => error.kind,
          'kind',
          'BOOKMARK',
        ),
      ),
    );
  });

  test(
    'posts confirmed study and vocabulary batches to typed endpoints',
    () async {
      final captured = <http.Request>[];
      final client = _HandlerClient((request) async {
        captured.add(request as http.Request);
        return _response('[]');
      });
      final api = _api(client);

      await api.createStudySessions({
        'items': [
          {'skill': 'Listening', 'occurredOn': '2026-07-13'},
        ],
      });
      await api.createVocabCards({
        'items': [
          {'front': 'meticulous', 'back': 'tỉ mỉ', 'language': 'ENGLISH'},
        ],
      });

      expect(captured.map((request) => request.url.path), [
        '/api/study/sessions',
        '/api/study/vocab',
      ]);
      expect((jsonDecode(captured.first.body) as Map)['items'], hasLength(1));
      expect(captured.every((request) => request.method == 'POST'), isTrue);
    },
  );
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
