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

  test('busca preview de sincronizacao do manifesto operacional', () async {
    final service = AdminViagensPortalService(
      session: session,
      client: MockClient((request) async {
        expect(request.method, 'GET');
        expect(
          request.url.path,
          '/company-admins/viagens/31/manifesto-preview',
        );
        expect(request.headers['Authorization'], 'Bearer token.admin');
        return http.Response(
          jsonEncode({
            'viagemId': 31,
            'servicoId': 15,
            'servicoNome': 'Linha Manhã',
            'dataOperacao': '2026-05-25',
            'paradasAtuais': 2,
            'paradasAposSincronizacao': 3,
            'adicionados': 2,
            'removidos': 1,
            'mantidos': 1,
            'mudancas': 3,
            'semMudancas': false,
            'passageirosAdicionados': [
              {
                'passageiroId': 23,
                'nome': 'Novo Passageiro',
                'menorDeIdade': false,
                'ordemAtual': null,
                'ordemNova': 2,
              },
            ],
            'passageirosRemovidos': [
              {
                'passageiroId': 21,
                'nome': 'Sai da rota',
                'menorDeIdade': false,
                'ordemAtual': 1,
                'ordemNova': null,
              },
            ],
            'passageirosMantidos': [
              {
                'passageiroId': 22,
                'nome': 'Permanece',
                'menorDeIdade': false,
                'ordemAtual': 2,
                'ordemNova': 1,
              },
            ],
          }),
          200,
          headers: {'content-type': 'application/json; charset=utf-8'},
        );
      }),
    );

    final preview = await service.fetchManifestPreview(31);

    expect(preview.viagemId, 31);
    expect(preview.servicoId, 15);
    expect(preview.paradasAtuais, 2);
    expect(preview.paradasAposSincronizacao, 3);
    expect(preview.mudancas, 3);
    expect(preview.semMudancas, isFalse);
    expect(preview.passageirosAdicionados.single.passageiroId, 23);
    expect(preview.passageirosAdicionados.single.ordemNova, 2);
    expect(preview.passageirosRemovidos.single.ordemAtual, 1);
  });
}
