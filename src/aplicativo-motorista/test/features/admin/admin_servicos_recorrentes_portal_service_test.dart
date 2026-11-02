import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:vanrides_frontend_dev/features/admin/data/admin_servicos_recorrentes_portal_service.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_portal_session.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_servico_recorrente_input.dart';

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

  test('lista servicos recorrentes do portal empresa', () async {
    final service = AdminServicosRecorrentesPortalService(
      session: session,
      client: MockClient((request) async {
        expect(request.method, 'GET');
        expect(request.url.path, '/company-admins/servicos-recorrentes');
        expect(request.headers['Authorization'], 'Bearer token.admin');
        return _jsonResponse([
          {
            'id': 15,
            'rotaBaseLegadaId': 10,
            'nome': 'Linha Manhã',
            'tipoOperacao': 'IDA',
            'motoristaPadrao': null,
            'quantidadePassageiros': 2,
            'passageiroIds': [21, 22],
            'ativo': true,
            'geracaoAutomaticaAtiva': true,
            'politicaPresenca': 'EXIGE_CONFIRMACAO',
            'diasOperacao': ['MONDAY', 'WEDNESDAY'],
            'horarioPartida': '07:30:00',
          },
        ]);
      }),
    );

    final servicos = await service.fetchServicos();

    expect(servicos, hasLength(1));
    expect(servicos.first.nome, 'Linha Manhã');
    expect(servicos.first.motoristaPadrao, isNull);
    expect(servicos.first.diasOperacao, ['SEG', 'QUA']);
    expect(servicos.first.horarioPartida, '07:30');
  });

  test('cria servico recorrente com payload operacional', () async {
    final service = AdminServicosRecorrentesPortalService(
      session: session,
      client: MockClient((request) async {
        expect(request.method, 'POST');
        expect(request.url.path, '/company-admins/servicos-recorrentes');
        expect(request.headers['Authorization'], 'Bearer token.admin');
        final payload = jsonDecode(request.body) as Map<String, dynamic>;
        expect(payload['nome'], 'Linha Manhã');
        expect(payload['motoristaPadraoId'], 7);
        expect(payload['tipoOperacao'], 'IDA');
        expect(payload['politicaPresenca'], 'EXIGE_CONFIRMACAO');
        expect(payload['diasOperacao'], ['MONDAY', 'TUESDAY']);
        expect(payload['passageiroIds'], [21, 22]);
        return _jsonResponse({
          'id': 15,
          'nome': 'Linha Manhã',
          'tipoOperacao': 'IDA',
          'motoristaPadrao': {
            'id': 7,
            'nome': 'Motorista Portal',
            'email': 'm@vanrides.dev',
            'telefone': '51999990000',
          },
          'quantidadePassageiros': 2,
          'passageiroIds': [21, 22],
          'ativo': true,
          'geracaoAutomaticaAtiva': true,
          'politicaPresenca': 'EXIGE_CONFIRMACAO',
          'diasOperacao': ['MONDAY', 'TUESDAY'],
        }, 201);
      }),
    );

    final created = await service.createServico(
      const AdminServicoRecorrenteInput(
        nome: 'Linha Manhã',
        motoristaPadraoId: 7,
        tipoOperacao: 'IDA',
        politicaPresenca: 'EXIGE_CONFIRMACAO',
        geracaoAutomaticaAtiva: true,
        diasOperacao: ['SEG', 'TER'],
        passageiroIds: [21, 22],
      ),
    );

    expect(created.id, 15);
    expect(created.motoristaPadrao?.id, 7);
  });

  test('inativa servico recorrente pelo contrato admin', () async {
    final service = AdminServicosRecorrentesPortalService(
      session: session,
      client: MockClient((request) async {
        expect(request.method, 'DELETE');
        expect(request.url.path, '/company-admins/servicos-recorrentes/15');
        expect(request.headers['Authorization'], 'Bearer token.admin');
        return http.Response('', 204);
      }),
    );

    await service.deleteServico(15);
  });
}

http.Response _jsonResponse(Object body, [int status = 200]) {
  return http.Response(
    jsonEncode(body),
    status,
    headers: {'content-type': 'application/json; charset=utf-8'},
  );
}
