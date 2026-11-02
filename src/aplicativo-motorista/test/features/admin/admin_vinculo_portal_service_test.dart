import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:vanrides_frontend_dev/features/admin/data/admin_vinculo_portal_service.dart';
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

  test('lista vinculos passageiro-motorista', () async {
    final service = AdminVinculoPortalService(
      session: session,
      client: MockClient((request) async {
        expect(
          request.url.path,
          '/company-admins/vinculos/passageiros-motoristas',
        );
        return http.Response(
          jsonEncode([
            {
              'passageiroId': 11,
              'passageiroNome': 'Aluno 1',
              'menorDeIdade': true,
              'motorista': {
                'id': 3,
                'nome': 'Motorista A',
                'email': 'motorista@vanrides.dev',
                'telefone': '51999999999',
              },
            },
          ]),
          200,
          headers: {'content-type': 'application/json; charset=utf-8'},
        );
      }),
    );

    final result = await service.fetchPassageiroMotoristaLinks();

    expect(result, hasLength(1));
    expect(result.first.motorista?.nome, 'Motorista A');
  });

  test('lista vinculos responsavel-passageiro', () async {
    final service = AdminVinculoPortalService(
      session: session,
      client: MockClient((request) async {
        expect(
          request.url.path,
          '/company-admins/vinculos/responsaveis-passageiros',
        );
        return http.Response(
          jsonEncode([
            {
              'responsavel': {
                'id': 7,
                'nome': 'Responsavel Portal',
                'email': 'responsavel@vanrides.dev',
                'telefone': '51999999997',
              },
              'passageiro': {
                'id': 11,
                'nome': 'Aluno 1',
                'email': 'aluno@vanrides.dev',
                'menorDeIdade': true,
              },
            },
          ]),
          200,
        );
      }),
    );

    final result = await service.fetchResponsavelPassageiroLinks();

    expect(result, hasLength(1));
    expect(result.first.responsavel.nome, 'Responsavel Portal');
    expect(result.first.passageiro.nome, 'Aluno 1');
  });

  test('lista solicitacoes pendentes de acesso', () async {
    final service = AdminVinculoPortalService(
      session: session,
      client: MockClient((request) async {
        expect(request.url.path, '/company-admins/access-requests');
        return http.Response(
          jsonEncode([
            {
              'id': 1,
              'passageiroId': 11,
              'passageiroNome': 'Aluno 1',
              'passageiroEmail': 'aluno@vanrides.dev',
              'companyName': 'Portal Empresa',
              'source': 'REQUEST',
              'emailVerified': true,
            },
          ]),
          200,
        );
      }),
    );

    final result = await service.fetchAccessRequests();

    expect(result, hasLength(1));
    expect(result.first.passageiroNome, 'Aluno 1');
  });

  test('aprova solicitacao liberando servicos recorrentes', () async {
    final service = AdminVinculoPortalService(
      session: session,
      client: MockClient((request) async {
        expect(request.method, 'POST');
        expect(request.url.path, '/company-admins/access-requests/1/approve');
        final payload = jsonDecode(request.body) as Map<String, dynamic>;
        expect(payload['motoristaId'], 3);
        expect(payload['reason'], 'Liberado para ida e volta');
        expect(payload['servicoIds'], [10, 11]);
        return http.Response(
          jsonEncode({
            'id': 1,
            'passageiroId': 11,
            'passageiroNome': 'Aluno 1',
            'passageiroEmail': 'aluno@vanrides.dev',
            'passageiroTelefone': '51999999998',
            'companyName': 'Portal Empresa',
            'source': 'REQUEST',
            'status': 'APPROVED',
            'emailVerified': true,
          }),
          200,
        );
      }),
    );

    final result = await service.approveAccessRequest(
      requestId: 1,
      motoristaId: 3,
      reason: ' Liberado para ida e volta ',
      servicoIds: const [10, 11],
    );

    expect(result.status, 'APPROVED');
  });

  test('atualiza servicos liberados do passageiro', () async {
    final service = AdminVinculoPortalService(
      session: session,
      client: MockClient((request) async {
        expect(request.method, 'PUT');
        expect(request.url.path, '/company-admins/passageiros/11/servicos');
        final payload = jsonDecode(request.body) as Map<String, dynamic>;
        expect(payload['servicoIds'], [10, 11]);
        return http.Response.bytes(
          utf8.encode(
            jsonEncode([
              {
                'servicoId': 10,
                'nome': 'Ida manhã',
                'tipoOperacao': 'IDA',
                'horarioPartida': '07:30:00',
                'habilitado': true,
                'elegibilidadeId': 101,
                'ordemPadrao': 3,
              },
              {
                'servicoId': 11,
                'nome': 'Volta tarde',
                'tipoOperacao': 'VOLTA',
                'horarioPartida': '17:40:00',
                'habilitado': true,
                'elegibilidadeId': 102,
                'ordemPadrao': 4,
              },
            ]),
          ),
          200,
        );
      }),
    );

    final result = await service.updatePassageiroServicos(
      passageiroId: 11,
      servicoIds: const [10, 11],
    );

    expect(result, hasLength(2));
    expect(result.first.nome, 'Ida manhã');
    expect(result.first.habilitado, isTrue);
  });

  test('gera novo codigo de convite', () async {
    final service = AdminVinculoPortalService(
      session: session,
      client: MockClient((request) async {
        expect(request.url.path, '/company-admins/invite-codes');
        return http.Response(
          jsonEncode({
            'id': 91,
            'code': 'VAN-2026-ABCD',
            'status': 'ACTIVE',
            'maxUses': 1,
            'useCount': 0,
            'usages': const [],
          }),
          201,
        );
      }),
    );

    final result = await service.createInviteCode(maxUses: 1);

    expect(result.code, 'VAN-2026-ABCD');
    expect(result.status, 'ACTIVE');
  });
}
