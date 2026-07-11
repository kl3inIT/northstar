import 'dart:async';

import 'package:flutter/cupertino.dart';
import 'package:go_router/go_router.dart';
import 'package:http/http.dart' as http;
import 'package:northstar/data/repositories/assistant_repository.dart';
import 'package:northstar/data/repositories/auth_repository.dart';
import 'package:northstar/data/services/assistant_api.dart';
import 'package:northstar/data/services/mobile_auth_api.dart';
import 'package:northstar/data/services/refresh_token_store.dart';
import 'package:northstar/ui/core/design_system/northstar_theme.dart';
import 'package:northstar/ui/core/navigation/northstar_router.dart';
import 'package:northstar/ui/features/auth/view_models/auth_view_model.dart';
import 'package:northstar/ui/features/assistant/view_models/assistant_view_model.dart';

class NorthstarApp extends StatefulWidget {
  const NorthstarApp({
    super.key,
    this.authRepository,
    this.assistantRepository,
  });

  final AuthRepository? authRepository;
  final AssistantRepository? assistantRepository;

  @override
  State<NorthstarApp> createState() => _NorthstarAppState();
}

class _NorthstarAppState extends State<NorthstarApp> {
  http.Client? _client;
  late final AuthRepository _authRepository;
  late final AuthViewModel _auth;
  late final AssistantViewModel _assistant;
  late final GoRouter _router;

  @override
  void initState() {
    super.initState();
    _client = http.Client();
    _authRepository = widget.authRepository ?? _createAuthRepository();
    _auth = AuthViewModel(_authRepository);
    final assistantRepository =
        widget.assistantRepository ?? _createAssistantRepository();
    _assistant = AssistantViewModel(assistantRepository);
    _router = createNorthstarRouter(_auth, _assistant);
    unawaited(_auth.restore());
  }

  static const _configuredBaseUrl = String.fromEnvironment(
    'NORTHSTAR_API_BASE_URL',
    defaultValue: 'http://localhost:8888',
  );

  AuthRepository _createAuthRepository() {
    return MobileAuthRepository(
      api: MobileAuthApi(
        client: _client!,
        baseUrl: Uri.parse(_configuredBaseUrl),
      ),
      refreshTokenStore: createRefreshTokenStore(),
    );
  }

  AssistantRepository _createAssistantRepository() {
    return RemoteAssistantRepository(
      AssistantApi(
        client: _client!,
        baseUrl: Uri.parse(_configuredBaseUrl),
        accessToken: () => _authRepository.accessToken,
        refreshAccessToken: _authRepository.refreshAccessToken,
        onUnauthorized: _auth.expireSession,
      ),
    );
  }

  @override
  void dispose() {
    _router.dispose();
    _assistant.dispose();
    _auth.dispose();
    _client?.close();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return CupertinoApp.router(
      debugShowCheckedModeBanner: false,
      title: 'Northstar',
      theme: NorthstarTheme.data,
      routerConfig: _router,
    );
  }
}
