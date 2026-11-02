import 'dart:convert';
import 'dart:io';

import 'package:http/http.dart' as http;
import 'package:http_parser/http_parser.dart';

Future<void> main() async {
  final baseUrl = _requiredEnv('VANRIDES_API_BASE_URL');
  final email = _requiredEnv('VANRIDES_PROBE_EMAIL');
  final password = _requiredEnv('VANRIDES_PROBE_PASSWORD');

  final loginResponse = await http.post(
    Uri.parse('$baseUrl/login'),
    headers: {'Content-Type': 'application/json'},
    body: jsonEncode({'email': email, 'senha': password}),
  );
  if (loginResponse.statusCode != 200) {
    throw StateError(
      'Login HTTP ${loginResponse.statusCode}: ${loginResponse.body}',
    );
  }

  final loginBody = jsonDecode(loginResponse.body) as Map<String, dynamic>;
  final token = loginBody['accessToken'] as String?;
  if (token == null || token.trim().isEmpty) {
    throw StateError('Login response sem accessToken.');
  }

  final pngBytes = base64Decode(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII=',
  );
  final request =
      http.MultipartRequest('POST', Uri.parse('$baseUrl/account/me/avatar'))
        ..headers['Authorization'] = 'Bearer $token'
        ..files.add(
          http.MultipartFile.fromBytes(
            'file',
            pngBytes,
            filename: 'avatar.png',
            contentType: MediaType.parse('image/png'),
          ),
        );

  final streamedResponse = await request.send();
  final response = await http.Response.fromStream(streamedResponse);
  stdout.writeln('AVATAR_HTTP ${response.statusCode}');
  stdout.writeln(response.body);
  if (response.statusCode != 200) {
    throw StateError('Avatar upload falhou.');
  }
}

String _requiredEnv(String name) {
  final value = Platform.environment[name]?.trim();
  if (value == null || value.isEmpty) {
    throw StateError('Defina $name no ambiente.');
  }
  return value;
}
