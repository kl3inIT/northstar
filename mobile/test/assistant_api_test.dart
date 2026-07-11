import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:northstar/data/models/assistant_dtos.dart';
import 'package:northstar/data/services/assistant_api.dart';

void main() {
  test('parses text, tool, and completion frames from SSE', () async {
    late http.BaseRequest capturedRequest;
    final client = _HandlerClient((request) async {
      capturedRequest = request;
      return _response(
        '''data: {"type":"start","messageId":"message-1"}\n\n'''
        ''': ping\n\n'''
        '''data: {"type":"start-step"}\n\n'''
        '''data: {"type":"text-delta","id":"text-1","delta":"Hello"}\n\n'''
        '''data: {"type":"tool-input-start","toolCallId":"tool-1","toolName":"today_tasks"}\n\n'''
        '''data: {"type":"tool-output-error","toolCallId":"tool-1","errorText":"Tool execution failed."}\n\n'''
        '''data: {"type":"source-url","sourceId":"web-1","url":"https://example.com","title":"Example"}\n\n'''
        '''data: {"type":"source-document","sourceId":"/notes/northstar","mediaType":"text/markdown","title":"Northstar note","filename":"northstar.md"}\n\n'''
        '''data: [DONE]\n\n''',
      );
    });
    final api = AssistantApi(
      client: client,
      baseUrl: Uri.parse('https://northstar.example'),
      accessToken: () => 'access-token',
      refreshAccessToken: () async => null,
      onUnauthorized: () {},
    );

    final frames = await api
        .streamTurn(conversationId: 'conversation-1', message: 'Hello')
        .toList();

    expect(capturedRequest.headers['authorization'], 'Bearer access-token');
    expect(capturedRequest.url.path, '/api/assistant/chat');
    expect(frames, hasLength(8));
    expect(frames[1], isA<AssistantUnknownFrame>());
    expect(frames[2], isA<AssistantTextDeltaFrame>());
    expect((frames[2] as AssistantTextDeltaFrame).delta, 'Hello');
    expect(frames[3], isA<AssistantToolInputStartFrame>());
    expect(frames[4], isA<AssistantToolOutputErrorFrame>());
    expect(frames[5], isA<AssistantSourceUrlFrame>());
    expect((frames[5] as AssistantSourceUrlFrame).url, 'https://example.com');
    expect(frames[6], isA<AssistantSourceDocumentFrame>());
    expect(
      (frames[6] as AssistantSourceDocumentFrame).sourceId,
      '/notes/northstar',
    );
    expect(frames.last, isA<AssistantDoneFrame>());
  });

  test('parses an abort frame', () async {
    final api = AssistantApi(
      client: _HandlerClient(
        (_) async => _response(
          '''data: {"type":"abort","reason":"Assistant turn timed out."}\n\n'''
          '''data: [DONE]\n\n''',
        ),
      ),
      baseUrl: Uri.parse('https://northstar.example'),
      accessToken: () => 'access-token',
      refreshAccessToken: () async => null,
      onUnauthorized: () {},
    );

    final frames = await api
        .streamTurn(conversationId: 'conversation-1', message: 'Hello')
        .toList();

    expect(frames.first, isA<AssistantAbortFrame>());
    expect(
      (frames.first as AssistantAbortFrame).reason,
      'Assistant turn timed out.',
    );
    expect(frames.last, isA<AssistantDoneFrame>());
  });

  test('refreshes once after 401 and retries with the rotated token', () async {
    final tokens = <String?>[];
    var calls = 0;
    final client = _HandlerClient((request) async {
      calls += 1;
      tokens.add(request.headers['authorization']);
      if (calls == 1) {
        return _response('unauthorized', statusCode: 401);
      }
      return _response('data: [DONE]\n\n');
    });
    final api = AssistantApi(
      client: client,
      baseUrl: Uri.parse('https://northstar.example'),
      accessToken: () => 'expired-token',
      refreshAccessToken: () async => 'rotated-token',
      onUnauthorized: () {},
    );

    await api
        .streamTurn(conversationId: 'conversation-1', message: 'Hello')
        .drain<void>();

    expect(calls, 2);
    expect(tokens, ['Bearer expired-token', 'Bearer rotated-token']);
  });
}

http.StreamedResponse _response(String body, {int statusCode = 200}) {
  return http.StreamedResponse(Stream.value(utf8.encode(body)), statusCode);
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
