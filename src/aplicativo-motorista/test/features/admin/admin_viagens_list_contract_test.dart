import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:vanrides_frontend_dev/features/admin/data/admin_viagens_portal_service.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_portal_session.dart';

void main() {
  const session = AdminPortalSession(
    adminId: 1,
    adminName: 'Admin Portal',
    companyId: 1,
    companyName: 'Portal Empresa',
    companySlug: 'vanrides-default',
    email: 'admin@vanrides.dev',
    accessToken: 'token.admin',
  );

  test('busca viagens com status de manifesto pendente', () async {
    final service = AdminViagensPortalService(
      session: session,
      client: MockClient((request) async {
        expect(request.method, 'GET');
        expect(request.url.path, '/company-admins/viagens');
        expect(request.url.queryParameters['data'], '2026-05-25');
        return http.Response(
          jsonEncode([
            {
              'id': 31,
              'rotaBaseId': null,
              'servicoId': 15,
              'rotaNome': 'Linha Manhã',
              'servicoNome': 'Linha Manhã',
              'tipoOperacao': 'IDA',
              'dataOperacao': '2026-05-25',
              'horarioPartida': '07:30:00',
              'status': 'PLANNED',
              'motorista': {
                'id': 7,
                'nome': 'Motorista Portal',
                'email': 'm@vanrides.dev',
                'telefone': '51999990000',
              },
              'quantidadeParadas': 3,
              'manifesto': {
                'status': 'PENDING',
                'mudancas': 2,
                'adicionados': 1,
                'removidos': 1,
                'mensagem': 'Sincronize o manifesto antes da saída.',
              },
            },
          ]),
          200,
          headers: {'content-type': 'application/json; charset=utf-8'},
        );
      }),
    );

    final viagens = await service.fetchViagens(data: DateTime(2026, 5, 25));

    expect(viagens.single.manifesto.status, 'PENDING');
    expect(viagens.single.manifesto.mudancas, 2);
    expect(viagens.single.manifesto.adicionados, 1);
    expect(viagens.single.manifesto.removidos, 1);
    expect(viagens.single.manifesto.isPending, isTrue);
  });
}
