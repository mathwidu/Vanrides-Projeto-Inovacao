import 'dart:convert';

String buildAuthorizationHeader({
  String? accessToken,
  required String email,
  required String senha,
}) {
  final token = accessToken?.trim();
  if (token != null && token.isNotEmpty) {
    return 'Bearer $token';
  }

  final basic = base64Encode(utf8.encode('$email:$senha'));
  return 'Basic $basic';
}
