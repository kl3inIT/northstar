import 'dart:async';

import 'package:flutter/cupertino.dart';
import 'package:go_router/go_router.dart';
import 'package:http/http.dart' as http;
import 'package:northstar/data/repositories/auth_repository.dart';
import 'package:northstar/data/services/mobile_auth_api.dart';
import 'package:northstar/data/services/refresh_token_store.dart';
import 'package:northstar/ui/core/design_system/northstar_theme.dart';
import 'package:northstar/ui/core/navigation/northstar_router.dart';
import 'package:northstar/ui/features/auth/view_models/auth_view_model.dart';

class NorthstarApp extends StatefulWidget {
  const NorthstarApp({super.key, this.authRepository});

  final AuthRepository? authRepository;

  @override
  State<NorthstarApp> createState() => _NorthstarAppState();
}

class _NorthstarAppState extends State<NorthstarApp> {
  http.Client? _client;
  late final AuthViewModel _auth;
  late final GoRouter _router;

  @override
  void initState() {
    super.initState();
    final repository = widget.authRepository ?? _createRepository();
    _auth = AuthViewModel(repository);
    _router = createNorthstarRouter(_auth);
    unawaited(_auth.restore());
  }

  AuthRepository _createRepository() {
    _client = http.Client();
    const configuredBaseUrl = String.fromEnvironment(
      'NORTHSTAR_API_BASE_URL',
      defaultValue: 'http://localhost:8888',
    );
    return MobileAuthRepository(
      api: MobileAuthApi(
        client: _client!,
        baseUrl: Uri.parse(configuredBaseUrl),
      ),
      refreshTokenStore: createRefreshTokenStore(),
    );
  }

  @override
  void dispose() {
    _router.dispose();
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
