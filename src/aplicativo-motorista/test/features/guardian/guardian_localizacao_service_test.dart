import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:vanrides_frontend_dev/features/guardian/data/guardian_localizacao_service.dart';
import 'package:vanrides_frontend_dev/features/guardian/domain/guardian_session.dart';

void main() {
  const session = GuardianSession(
    guardianId: 7,
    guardianName: 'Responsavel QA',
    companyId: 88,
    email: 'responsavel@vanrides.dev',
    accessToken: 'token.guardian',
  );

  test('carrega historico de localizacao do motorista', () async {
    final service = GuardianLocalizacaoService(
      session: session,
      client: MockClient((request) async {
        expect(request.url.path, '/api/localizacao/12/historico');
        expect(request.headers['Authorization'], 'Bearer token.guardian');
        return http.Response(
          '[{"latitude":-30.1,"longitude":-51.2,"dataHora":"2026-03-12T10:00:00"}]',
          200,
          headers: {'content-type': 'application/json'},
        );
      }),
    );

    final historico = await service.fetchHistoricoPorMotorista(12);

    expect(historico, hasLength(1));
    expect(historico.first.latitude, -30.1);
    expect(historico.first.longitude, -51.2);
  });

  test('propaga mensagem amigavel em falha de contrato', () async {
    final service = GuardianLocalizacaoService(
      session: session,
      client: MockClient((request) async {
        return http.Response('{"message":"Motorista nao encontrado"}', 404);
      }),
    );

    expect(
      () => service.fetchHistoricoPorMotorista(99),
      throwsA(
        predicate((error) => error.toString().contains('Motorista nao encontrado')),
      ),
    );
  });
}
