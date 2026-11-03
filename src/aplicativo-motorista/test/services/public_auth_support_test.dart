import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;

import 'package:vanrides_frontend_dev/services/public_auth_support.dart';

void main() {
  test('decodifica resposta UTF-8 com acentos corretamente', () {
    final response = http.Response.bytes(
      utf8.encode('ação e confirmação'),
      202,
      headers: const {'content-type': 'application/json; charset=utf-8'},
    );

    expect(decodePublicAuthResponseBody(response), 'ação e confirmação');
  });

  test('extrai cooldown inicial do payload de cadastro', () {
    const responseBody =
        '{"emailVerificationLastSentAt":"2026-04-14T12:00:00Z"}';

    final cooldown = extractInitialEmailVerificationCooldownFromBody(
      responseBody,
      now: DateTime.parse('2026-04-14T12:05:00Z'),
    );

    expect(cooldown, const Duration(minutes: 10));
  });
}
