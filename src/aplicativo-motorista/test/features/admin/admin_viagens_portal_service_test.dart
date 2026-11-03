import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:vanrides_frontend_dev/features/admin/data/admin_viagens_portal_service.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_portal_exception.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_portal_session.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_viagem_create_input.dart';

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

  test('busca servicos recorrentes para criar viagem operacional', () async {
    final service = AdminViagensPortalService(
      session: session,
      client: MockClient((request) async {
        expect(request.method, 'GET');
        expect(request.url.path, '/company-admins/servicos-recorrentes');
        expect(request.headers['Authorization'], 'Bearer token.admin');
        return http.Response(
          jsonEncode([
            {
              'id': 15,
              'rotaBaseLegadaId': 10,
              'nome': 'Linha Manhã',
              'tipoOperacao': 'IDA',
              'motoristaPadrao': {
                'id': 7,
                'nome': 'Motorista Portal',
                'email': 'm@vanrides.dev',
                'telefone': '51999990000',
              },
              'quantidadePassageiros': 3,
              'passageiroIds': [21, 22, 23],
              'ativo': true,
              'geracaoAutomaticaAtiva': true,
              'politicaPresenca': 'INCLUIR_POR_PADRAO',
              'diasOperacao': ['MONDAY', 'TUESDAY'],
              'horarioPartida': '07:30:00',
            },
          ]),
          200,
          headers: {'content-type': 'application/json; charset=utf-8'},
        );
      }),
    );

    final servicos = await service.fetchServicos();

    expect(servicos, hasLength(1));
    expect(servicos.first.id, 15);
    expect(servicos.first.nome, 'Linha Manhã');
    expect(servicos.first.motoristaPadrao?.id, 7);
    expect(servicos.first.horarioPartida, '07:30');
  });

  test('cria viagem por servico com motorista operacional opcional', () async {
    final service = AdminViagensPortalService(
      session: session,
      client: MockClient((request) async {
        expect(request.method, 'POST');
        expect(request.url.path, '/company-admins/viagens');
        expect(request.headers['Authorization'], 'Bearer token.admin');
        final payload = jsonDecode(request.body) as Map<String, dynamic>;
        expect(payload['servicoId'], 15);
        expect(payload['motoristaId'], 7);
        expect(payload.containsKey('rotaBaseId'), isFalse);
        return http.Response(
          jsonEncode({
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
          }),
          201,
          headers: {'content-type': 'application/json; charset=utf-8'},
        );
      }),
    );

    final created = await service.createViagem(
      AdminViagemCreateInput(
        servicoId: 15,
        motoristaId: 7,
        dataOperacao: DateTime(2026, 5, 25),
      ),
    );

    expect(created.id, 31);
    expect(created.servicoId, 15);
    expect(created.nomeOperacional, 'Linha Manhã');
    expect(created.tipoOperacao, 'IDA');
  });

  test(
    'usa fallback contextual quando backend antigo retorna Conflict cru',
    () async {
      final service = AdminViagensPortalService(
        session: session,
        client: MockClient((request) async {
          expect(request.method, 'POST');
          expect(request.url.path, '/company-admins/viagens');
          return http.Response(
            jsonEncode({'error': 'Conflict'}),
            409,
            headers: {'content-type': 'application/json; charset=utf-8'},
          );
        }),
      );

      await expectLater(
        service.createViagem(
          AdminViagemCreateInput(
            servicoId: 15,
            motoristaId: 7,
            dataOperacao: DateTime(2026, 5, 25),
          ),
        ),
        throwsA(
          isA<AdminPortalException>().having(
            (error) => error.message,
            'message',
            allOf(
              contains('Abra a viagem existente'),
              contains('Sincronizar lista'),
            ),
          ),
        ),
      );
    },
  );

  test('preserva mensagem humana do backend novo ao criar viagem', () async {
    final service = AdminViagensPortalService(
      session: session,
      client: MockClient((request) async {
        expect(request.method, 'POST');
        expect(request.url.path, '/company-admins/viagens');
        return http.Response(
          jsonEncode({
            'code': 'CONFLICT',
            'message': 'Já existe viagem criada para este serviço e data.',
          }),
          409,
          headers: {'content-type': 'application/json; charset=utf-8'},
        );
      }),
    );

    await expectLater(
      service.createViagem(
        AdminViagemCreateInput(
          servicoId: 15,
          motoristaId: 7,
          dataOperacao: DateTime(2026, 5, 25),
        ),
      ),
      throwsA(
        isA<AdminPortalException>().having(
          (error) => error.message,
          'message',
          'Já existe viagem criada para este serviço e data.',
        ),
      ),
    );
  });

  test('gera viagens automaticas do dia por empresa', () async {
    final service = AdminViagensPortalService(
      session: session,
      client: MockClient((request) async {
        expect(request.method, 'POST');
        expect(request.url.path, '/company-admins/viagens/gerar-automaticas');
        expect(request.headers['Authorization'], 'Bearer token.admin');
        final payload = jsonDecode(request.body) as Map<String, dynamic>;
        expect(payload['dataOperacao'], '2026-05-25');
        return http.Response(
          jsonEncode({
            'dataOperacao': '2026-05-25',
            'criadas': 1,
            'existentes': 2,
            'atualizadas': 3,
            'bloqueadas': 1,
            'ignoradas': 1,
            'servicosIgnorados': [
              {
                'servicoId': 18,
                'servicoNome': 'Linha Lotada',
                'motivo': 'CAPACIDADE_EXCEDIDA',
                'mensagem':
                    'Manifesto operacional acima da capacidade planejada.',
              },
            ],
            'servicosBloqueados': [
              {
                'servicoId': 19,
                'servicoNome': 'Linha Rodando',
                'motivo': 'VIAGEM_EM_ANDAMENTO',
                'mensagem':
                    'A viagem já começou; revise alterações manualmente.',
              },
            ],
            'viagens': [
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
              },
            ],
          }),
          200,
          headers: {'content-type': 'application/json; charset=utf-8'},
        );
      }),
    );

    final result = await service.generateAutomaticViagens(
      DateTime(2026, 5, 25),
    );

    expect(result.criadas, 1);
    expect(result.existentes, 2);
    expect(result.atualizadas, 3);
    expect(result.bloqueadas, 1);
    expect(result.ignoradas, 1);
    expect(result.servicosIgnorados, hasLength(1));
    expect(result.servicosIgnorados.single.servicoId, 18);
    expect(result.servicosIgnorados.single.servicoNome, 'Linha Lotada');
    expect(result.servicosIgnorados.single.motivo, 'CAPACIDADE_EXCEDIDA');
    expect(
      result.servicosIgnorados.single.mensagem,
      'Manifesto operacional acima da capacidade planejada.',
    );
    expect(result.servicosBloqueados, hasLength(1));
    expect(result.servicosBloqueados.single.servicoId, 19);
    expect(result.servicosBloqueados.single.servicoNome, 'Linha Rodando');
    expect(result.servicosBloqueados.single.motivo, 'VIAGEM_EM_ANDAMENTO');
    expect(
      result.servicosBloqueados.single.mensagem,
      'A viagem já começou; revise alterações manualmente.',
    );
    expect(result.viagens.single.nomeOperacional, 'Linha Manhã');
  });

  test('busca detalhe administrativo com manifesto operacional', () async {
    final service = AdminViagensPortalService(
      session: session,
      client: MockClient((request) async {
        expect(request.method, 'GET');
        expect(request.url.path, '/company-admins/viagens/31');
        expect(request.headers['Authorization'], 'Bearer token.admin');
        return http.Response(
          jsonEncode(_detailPayload(motoristaId: 7)),
          200,
          headers: {'content-type': 'application/json; charset=utf-8'},
        );
      }),
    );

    final detail = await service.fetchViagemDetail(31);

    expect(detail.id, 31);
    expect(detail.nomeOperacional, 'Linha Manhã');
    expect(detail.paradas, hasLength(1));
    expect(detail.paradas.first.presencaId, 91);
    expect(detail.paradas.first.presencaStatus, 'CONFIRMED');
    expect(detail.fontePlanejamento, 'SERVICO_RECORRENTE');
    expect(detail.rotaGeografica?.provider, 'GOOGLE_DIRECTIONS_V1');
    expect(detail.rotaGeografica?.snapshotOperacional, isTrue);
    expect(detail.rotaGeografica?.calculadaEm, DateTime(2026, 5, 25, 7, 35));
    expect(detail.rotaGeografica?.waypoints, hasLength(2));
    expect(detail.rotaGeografica?.nextStop?.nome, 'Passageiro Teste');
  });

  test('troca motorista operacional da viagem planejada', () async {
    final service = AdminViagensPortalService(
      session: session,
      client: MockClient((request) async {
        expect(request.method, 'PUT');
        expect(request.url.path, '/company-admins/viagens/31/motorista');
        expect(request.headers['Authorization'], 'Bearer token.admin');
        final payload = jsonDecode(request.body) as Map<String, dynamic>;
        expect(payload['motoristaId'], 8);
        return http.Response(
          jsonEncode(_detailPayload(motoristaId: 8, rotaVersao: 2)),
          200,
          headers: {'content-type': 'application/json; charset=utf-8'},
        );
      }),
    );

    final detail = await service.updateViagemMotorista(31, 8);

    expect(detail.motorista.id, 8);
    expect(detail.rotaVersao, 2);
  });

  test('sincroniza manifesto operacional da viagem planejada', () async {
    final service = AdminViagensPortalService(
      session: session,
      client: MockClient((request) async {
        expect(request.method, 'POST');
        expect(
          request.url.path,
          '/company-admins/viagens/31/sincronizar-manifesto',
        );
        expect(request.headers['Authorization'], 'Bearer token.admin');
        return http.Response(
          jsonEncode(_detailPayload(motoristaId: 7, rotaVersao: 3)),
          200,
          headers: {'content-type': 'application/json; charset=utf-8'},
        );
      }),
    );

    final detail = await service.syncManifesto(31);

    expect(detail.id, 31);
    expect(detail.rotaVersao, 3);
    expect(detail.fontePlanejamento, 'SERVICO_RECORRENTE');
  });

  test('traduz bloqueio tecnico ao sincronizar lista da viagem', () async {
    final service = AdminViagensPortalService(
      session: session,
      client: MockClient((request) async {
        expect(request.method, 'POST');
        expect(
          request.url.path,
          '/company-admins/viagens/31/sincronizar-manifesto',
        );
        return http.Response(
          jsonEncode({'error': 'Bad Request'}),
          400,
          headers: {'content-type': 'application/json; charset=utf-8'},
        );
      }),
    );

    await expectLater(
      service.syncManifesto(31),
      throwsA(
        isA<AdminPortalException>().having(
          (error) => error.message,
          'message',
          allOf(
            contains('Sincronizar lista'),
            contains('viagem planejada'),
            isNot(contains('Bad Request')),
          ),
        ),
      ),
    );
  });

  test(
    'cancela viagem planejada pelo contrato administrativo explicito',
    () async {
      final service = AdminViagensPortalService(
        session: session,
        client: MockClient((request) async {
          expect(request.method, 'POST');
          expect(request.url.path, '/company-admins/viagens/31/cancelar');
          expect(request.headers['Authorization'], 'Bearer token.admin');
          return http.Response(
            jsonEncode(_detailPayload(motoristaId: 7, status: 'CANCELED')),
            200,
            headers: {'content-type': 'application/json; charset=utf-8'},
          );
        }),
      );

      final detail = await service.cancelViagem(31);

      expect(detail.status, 'CANCELED');
    },
  );

  test(
    'atualiza presenca existente no manifesto e recarrega detalhe',
    () async {
      var call = 0;
      final service = AdminViagensPortalService(
        session: session,
        client: MockClient((request) async {
          call += 1;
          expect(request.headers['Authorization'], 'Bearer token.admin');
          if (call == 1) {
            expect(request.method, 'PUT');
            expect(request.url.path, '/presencas-servico/91');
            final payload = jsonDecode(request.body) as Map<String, dynamic>;
            expect(payload, {'status': 'ABSENT'});
            return http.Response('{}', 200);
          }

          expect(request.method, 'GET');
          expect(request.url.path, '/company-admins/viagens/31');
          return http.Response(
            jsonEncode(
              _detailPayload(motoristaId: 7, presenceStatus: 'ABSENT'),
            ),
            200,
            headers: {'content-type': 'application/json; charset=utf-8'},
          );
        }),
      );

      final detail = await service.updateManifestPresence(
        viagemId: 31,
        servicoId: 15,
        passageiroId: 21,
        dataOperacao: DateTime(2026, 5, 25),
        presencaId: 91,
        status: 'ABSENT',
      );

      expect(call, 2);
      expect(detail.paradas.first.presencaId, 91);
      expect(detail.paradas.first.presencaStatus, 'ABSENT');
    },
  );

  test(
    'cria presenca ausente quando manifesto ainda nao tem decisao',
    () async {
      var call = 0;
      final service = AdminViagensPortalService(
        session: session,
        client: MockClient((request) async {
          call += 1;
          expect(request.headers['Authorization'], 'Bearer token.admin');
          if (call == 1) {
            expect(request.method, 'POST');
            expect(request.url.path, '/presencas-servico');
            final payload = jsonDecode(request.body) as Map<String, dynamic>;
            expect(payload['servicoId'], 15);
            expect(payload['passageiroId'], 21);
            expect(payload['dataOperacao'], '2026-05-25');
            expect(payload['status'], 'ABSENT');
            return http.Response('{}', 201);
          }

          expect(request.method, 'GET');
          expect(request.url.path, '/company-admins/viagens/31');
          return http.Response(
            jsonEncode(
              _detailPayload(
                motoristaId: 7,
                presenceId: 92,
                presenceStatus: 'ABSENT',
              ),
            ),
            200,
            headers: {'content-type': 'application/json; charset=utf-8'},
          );
        }),
      );

      final detail = await service.updateManifestPresence(
        viagemId: 31,
        servicoId: 15,
        passageiroId: 21,
        dataOperacao: DateTime(2026, 5, 25),
        presencaId: null,
        status: 'ABSENT',
      );

      expect(call, 2);
      expect(detail.paradas.first.presencaId, 92);
      expect(detail.paradas.first.presencaStatus, 'ABSENT');
    },
  );
}

Map<String, dynamic> _detailPayload({
  required int motoristaId,
  int rotaVersao = 1,
  String status = 'PLANNED',
  int? presenceId = 91,
  String presenceStatus = 'CONFIRMED',
}) {
  final confirmedPresence = presenceStatus == 'CONFIRMED';
  return {
    'id': 31,
    'rotaBaseId': null,
    'servicoId': 15,
    'rotaNome': null,
    'servicoNome': 'Linha Manhã',
    'tipoOperacao': 'IDA',
    'dataOperacao': '2026-05-25',
    'horarioPartida': '07:30:00',
    'status': status,
    'rotaVersao': rotaVersao,
    'etaMinutosAteProximaParada': 12,
    'etaAtualizadoEm': '2026-05-25T07:00:00',
    'motorista': {
      'id': motoristaId,
      'nome': motoristaId == 8 ? 'Motorista Reserva' : 'Motorista Portal',
      'email': 'm$motoristaId@vanrides.dev',
      'telefone': '51999990000',
    },
    'quantidadeParadas': 1,
    'destinoFinal': {
      'nome': 'Escola Central',
      'endereco': 'Rua A, 100',
      'latitude': -30.01,
      'longitude': -51.01,
    },
    'proximaParada': {
      'id': 51,
      'ordem': 1,
      'status': 'PENDING',
      'etaMinutos': 12,
      'confirmadoPresenca': confirmedPresence,
      'presencaId': presenceId,
      'presencaStatus': presenceStatus,
      'proximaParada': true,
      'passageiro': {
        'id': 21,
        'nome': 'Passageiro Teste',
        'menorDeIdade': false,
      },
    },
    'paradas': [
      {
        'id': 51,
        'ordem': 1,
        'status': 'PENDING',
        'etaMinutos': 12,
        'confirmadoPresenca': confirmedPresence,
        'presencaId': presenceId,
        'presencaStatus': presenceStatus,
        'proximaParada': true,
        'passageiro': {
          'id': 21,
          'nome': 'Passageiro Teste',
          'menorDeIdade': false,
        },
      },
    ],
    'fontePlanejamento': 'SERVICO_RECORRENTE',
    'snapshotOperacional': true,
    'rotaGeografica': {
      'provider': 'GOOGLE_DIRECTIONS_V1',
      'polyline': '',
      'calculadaEm': '2026-05-25T07:35:00',
      'snapshotOperacional': true,
      'waypoints': [
        {
          'tipo': 'STOP',
          'paradaId': 51,
          'passageiroId': 21,
          'nome': 'Passageiro Teste',
          'latitude': -30.02,
          'longitude': -51.02,
          'ordem': 1,
          'proximaParada': true,
          'paradaDoPassageiro': false,
        },
        {'tipo': 'DESTINATION', 'nome': 'Escola Central', 'ordem': 2},
      ],
    },
  };
}
