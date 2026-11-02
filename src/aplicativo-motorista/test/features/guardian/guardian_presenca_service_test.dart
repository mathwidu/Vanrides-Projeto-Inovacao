import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:vanrides_frontend_dev/features/guardian/data/guardian_presenca_service.dart';
import 'package:vanrides_frontend_dev/features/guardian/domain/guardian_session.dart';

void main() {
  const session = GuardianSession(
    guardianId: 7,
    guardianName: 'Mae da Ana',
    companyId: 1,
    email: 'mae@vanrides.dev',
    accessToken: 'token.guardian',
  );

  test('lista presencas do passageiro vinculado com bearer auth', () async {
    final service = GuardianPresencaService(
      session: session,
      client: MockClient((request) async {
        expect(request.method, 'GET');
        expect(request.url.path, '/presencas/passageiro/10');
        expect(request.headers['Authorization'], 'Bearer token.guardian');
        return http.Response(
          jsonEncode([
            {
              'id': 90,
              'confirmado': true,
              'dataPresenca': '2026-03-12',
              'passageiro': {'id': 10},
            },
          ]),
          200,
        );
      }),
    );

    final result = await service.fetchPresencasByPassageiro(10);

    expect(result, hasLength(1));
    expect(result.first.id, 90);
    expect(result.first.passageiroId, 10);
    expect(result.first.confirmado, isTrue);
  });

  test('confirma presenca do passageiro vinculado', () async {
    final service = GuardianPresencaService(
      session: session,
      client: MockClient((request) async {
        expect(request.method, 'POST');
        expect(request.url.path, '/presencas');
        expect(request.headers['Authorization'], 'Bearer token.guardian');
        final body = jsonDecode(request.body) as Map<String, dynamic>;
        expect(body['passageiro']['id'], 10);
        expect(body['dataPresenca'], '2026-03-13');
        expect(body['confirmado'], isTrue);
        return http.Response('{}', 200);
      }),
    );

    await service.confirmarPresenca(
      passageiroId: 10,
      dataPresenca: DateTime(2026, 3, 13),
    );
  });

  test('remove presenca do passageiro vinculado', () async {
    final service = GuardianPresencaService(
      session: session,
      client: MockClient((request) async {
        expect(request.method, 'DELETE');
        expect(request.url.path, '/presencas/91');
        expect(request.headers['Authorization'], 'Bearer token.guardian');
        return http.Response('', 204);
      }),
    );

    await service.removerPresenca(91);
  });
}
