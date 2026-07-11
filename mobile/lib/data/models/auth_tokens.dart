class AuthTokens {
  const AuthTokens({
    required this.accessToken,
    required this.accessTokenExpiresAt,
    required this.refreshToken,
    required this.refreshTokenExpiresAt,
    required this.username,
  });

  factory AuthTokens.fromJson(Map<String, Object?> json) {
    return AuthTokens(
      accessToken: _requiredString(json, 'accessToken'),
      accessTokenExpiresAt: DateTime.parse(
        _requiredString(json, 'accessTokenExpiresAt'),
      ),
      refreshToken: _requiredString(json, 'refreshToken'),
      refreshTokenExpiresAt: DateTime.parse(
        _requiredString(json, 'refreshTokenExpiresAt'),
      ),
      username: _requiredString(json, 'username'),
    );
  }

  final String accessToken;
  final DateTime accessTokenExpiresAt;
  final String refreshToken;
  final DateTime refreshTokenExpiresAt;
  final String username;
}

String _requiredString(Map<String, Object?> json, String key) {
  final value = json[key];
  if (value is! String || value.isEmpty) {
    throw const FormatException('The authentication response is incomplete.');
  }
  return value;
}
