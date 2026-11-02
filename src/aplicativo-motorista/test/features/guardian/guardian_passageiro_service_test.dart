import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:vanrides_frontend_dev/features/guardian/data/guardian_passageiro_service.dart';
import 'package:vanrides_frontend_dev/features/guardian/domain/guardian_session.dart';

void main() {
  const session = GuardianSession(
    guardianId: 9,
    guardianName: 'Mae da Ana',
    companyId: 1,
    email: 'mae@vanrides.dev',
    accessToken: 'token.guardian',
  );

  test('carrega passageiros vinculados com bearer do guardian', () async {
    final service = GuardianPassageiroService(
      session: session,
      client: MockClient((request) async {
        expect(request.url.path, '/passageiros');
        expect(request.headers['Authorization'], 'Bearer token.guardian');
        return http.Response(
          jsonEncode([
            {
              'id': 10,
              'nome': 'Ana',
              'email': 'ana@vanrides.dev',
              'telefone': '51999990000',
              'endereco': 'Rua A',
              'bairro': 'Centro',
              'cidade': 'Porto Alegre',
              'estado': 'RS',
              'cep': '90000-000',
              'menorDeIdade': true,
              'motorista': {'id': 3, 'nome': 'Motorista Portal'},
            },
          ]),
          200,
        );
      }),
    );

    final result = await service.fetchLinkedPassageiros();

    expect(result, hasLength(1));
    expect(result.first.nome, 'Ana');
    expect(result.first.motoristaNome, 'Motorista Portal');
    expect(result.first.menorDeIdade, isTrue);
  });
}
