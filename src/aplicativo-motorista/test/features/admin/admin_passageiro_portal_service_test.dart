import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:vanrides_frontend_dev/features/admin/data/admin_passageiro_portal_service.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_passageiro_create_input.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_passageiro_update_input.dart';
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

  test('carrega motoristas para o cadastro do portal', () async {
    final service = AdminPassageiroPortalService(
      session: session,
      client: MockClient((request) async {
        expect(request.url.path, '/company-admins/motoristas');
        expect(request.headers['Authorization'], 'Bearer token.admin');
        return http.Response(
          jsonEncode([
            {
              'id': 1,
              'nome': 'Motorista Portal',
              'email': 'portal@vanrides.dev',
              'telefone': '51999999999',
            },
          ]),
          200,
        );
      }),
    );

    final result = await service.fetchMotoristas();

    expect(result, hasLength(1));
    expect(result.first.nome, 'Motorista Portal');
  });

  test('lista passageiros administrativos da empresa', () async {
    final service = AdminPassageiroPortalService(
      session: session,
      client: MockClient((request) async {
        expect(request.url.path, '/company-admins/passageiros');
        expect(request.headers['Authorization'], 'Bearer token.admin');
        return http.Response(
          jsonEncode([
            {
              'id': 10,
              'nome': 'Passageiro Portal',
              'email': 'passageiro@vanrides.dev',
              'telefone': '51999999998',
              'dataNascimento': '2010-05-20',
              'fotoPerfilUrl': '/uploads/profiles/passageiro-10.jpg',
              'menorDeIdade': true,
              'responsavelCount': 1,
              'motorista': {'id': 3, 'nome': 'Motorista Vinculado'},
            },
          ]),
          200,
        );
      }),
    );

    final result = await service.fetchPassageiros();

    expect(result, hasLength(1));
    expect(result.first.motoristaNome, 'Motorista Vinculado');
    expect(result.first.responsavelCount, 1);
    expect(result.first.dataNascimento, '2010-05-20');
    expect(result.first.fotoPerfilUrl, '/uploads/profiles/passageiro-10.jpg');
  });

  test('cria passageiro com payload esperado', () async {
    final service = AdminPassageiroPortalService(
      session: session,
      client: MockClient((request) async {
        expect(request.method, 'POST');
        expect(request.url.path, '/company-admins/passageiros');
        expect(request.headers['Authorization'], 'Bearer token.admin');

        final body = jsonDecode(request.body) as Map<String, dynamic>;
        expect(body['nome'], 'Passageiro Portal');
        expect(body['dataNascimento'], '2010-05-20');
        expect(body['menorDeIdade'], true);
        expect(body['motoristaId'], 3);
        expect(body['servicoIds'], [10, 11]);

        return http.Response(
          jsonEncode({
            'id': 10,
            'nome': 'Passageiro Portal',
            'email': 'passageiro@vanrides.dev',
            'telefone': '51999999998',
            'endereco': 'Rua A',
            'bairro': 'Centro',
            'cidade': 'Porto Alegre',
            'estado': 'RS',
            'cep': '90000-000',
            'dataNascimento': '2010-05-20',
            'fotoPerfilUrl': '/uploads/profiles/passageiro-10.jpg',
            'menorDeIdade': true,
            'motorista': {'id': 3, 'nome': 'Motorista Vinculado'},
          }),
          201,
        );
      }),
    );

    final created = await service.createPassageiro(
      const AdminPassageiroCreateInput(
        nome: 'Passageiro Portal',
        email: 'passageiro@vanrides.dev',
        telefone: '51999999998',
        dataNascimento: '2010-05-20',
        senha: 'Portal!123',
        endereco: 'Rua A',
        bairro: 'Centro',
        cidade: 'Porto Alegre',
        estado: 'RS',
        cep: '90000-000',
        motoristaId: 3,
        menorDeIdade: true,
        servicoIds: [10, 11],
      ),
    );

    expect(created.nome, 'Passageiro Portal');
    expect(created.motoristaNome, 'Motorista Vinculado');
    expect(created.menorDeIdade, isTrue);
    expect(created.dataNascimento, '2010-05-20');
    expect(created.fotoPerfilUrl, '/uploads/profiles/passageiro-10.jpg');
  });

  test('carrega servicos recorrentes para liberar no cadastro', () async {
    final service = AdminPassageiroPortalService(
      session: session,
      client: MockClient((request) async {
        expect(request.method, 'GET');
        expect(request.url.path, '/company-admins/servicos-recorrentes');
        expect(request.headers['Authorization'], 'Bearer token.admin');

        return http.Response.bytes(
          utf8.encode(
            jsonEncode([
              {
                'id': 10,
                'nome': 'Ida manhã',
                'tipoOperacao': 'IDA',
                'quantidadePassageiros': 1,
                'passageiroIds': [11],
                'ativo': true,
                'geracaoAutomaticaAtiva': true,
                'politicaPresenca': 'INCLUIR_POR_PADRAO',
                'diasOperacao': ['SEG'],
                'horarioPartida': '07:30:00',
                'motoristaPadrao': null,
              },
            ]),
          ),
          200,
          headers: {'content-type': 'application/json; charset=utf-8'},
        );
      }),
    );

    final servicos = await service.fetchServicos();

    expect(servicos, hasLength(1));
    expect(servicos.single.nome, 'Ida manhã');
    expect(servicos.single.horarioPartida, '07:30');
    expect(servicos.single.motoristaPadrao, isNull);
  });

  test(
    'atualiza servicos liberados do passageiro criado manualmente',
    () async {
      final service = AdminPassageiroPortalService(
        session: session,
        client: MockClient((request) async {
          expect(request.method, 'PUT');
          expect(request.url.path, '/company-admins/passageiros/11/servicos');
          expect(request.headers['Authorization'], 'Bearer token.admin');

          final body = jsonDecode(request.body) as Map<String, dynamic>;
          expect(body['servicoIds'], [10, 11]);

          return http.Response.bytes(
            utf8.encode(
              jsonEncode([
                {
                  'servicoId': 10,
                  'nome': 'Ida manhã',
                  'tipoOperacao': 'IDA',
                  'horarioPartida': '07:30:00',
                  'habilitado': true,
                },
              ]),
            ),
            200,
            headers: {'content-type': 'application/json; charset=utf-8'},
          );
        }),
      );

      final servicos = await service.updatePassageiroServicos(
        passageiroId: 11,
        servicoIds: const [10, 11],
      );

      expect(servicos, hasLength(1));
      expect(servicos.single.habilitado, isTrue);
    },
  );

  test(
    'busca presenca de hoje do passageiro para ficha administrativa',
    () async {
      final service = AdminPassageiroPortalService(
        session: session,
        client: MockClient((request) async {
          expect(request.method, 'GET');
          expect(request.url.path, '/passageiros/11/servicos-disponiveis');
          expect(request.url.queryParameters['data'], '2026-06-18');
          expect(request.headers['Authorization'], 'Bearer token.admin');

          return http.Response.bytes(
            utf8.encode(
              jsonEncode([
                {
                  'servicoId': 10,
                  'nome': 'Ida manhã',
                  'tipoOperacao': 'IDA',
                  'dataOperacao': '2026-06-18',
                  'horarioPartida': '07:30:00',
                  'politicaPresenca': 'INCLUIR_POR_PADRAO',
                  'presencaId': 91,
                  'presencaStatus': 'CONFIRMED',
                },
              ]),
            ),
            200,
            headers: {'content-type': 'application/json; charset=utf-8'},
          );
        }),
      );

      final presencas = await service.fetchAvailableServices(
        passageiroId: 11,
        data: DateTime(2026, 6, 18),
      );

      expect(presencas, hasLength(1));
      expect(presencas.single.nome, 'Ida manhã');
      expect(presencas.single.statusLabel, 'Vou');
    },
  );

  test(
    'cria passageiro sem motorista inicial quando cadastro ainda nao tem rota',
    () async {
      final service = AdminPassageiroPortalService(
        session: session,
        client: MockClient((request) async {
          expect(request.method, 'POST');
          expect(request.url.path, '/company-admins/passageiros');

          final body = jsonDecode(request.body) as Map<String, dynamic>;
          expect(body['nome'], 'Passageiro Sem Motorista');
          expect(body['dataNascimento'], '1998-08-12');
          expect(body.containsKey('motoristaId'), isFalse);

          return http.Response(
            jsonEncode({
              'id': 11,
              'nome': 'Passageiro Sem Motorista',
              'email': 'sem.motorista@vanrides.dev',
              'telefone': '51999999997',
              'endereco': 'Rua B',
              'bairro': 'Centro',
              'cidade': 'Porto Alegre',
              'estado': 'RS',
              'cep': '90000-001',
              'menorDeIdade': false,
              'motorista': null,
            }),
            201,
          );
        }),
      );

      final created = await service.createPassageiro(
        const AdminPassageiroCreateInput(
          nome: 'Passageiro Sem Motorista',
          email: 'sem.motorista@vanrides.dev',
          telefone: '51999999997',
          dataNascimento: '1998-08-12',
          senha: 'Portal!123',
          endereco: 'Rua B',
          bairro: 'Centro',
          cidade: 'Porto Alegre',
          estado: 'RS',
          cep: '90000-001',
          menorDeIdade: false,
        ),
      );

      expect(created.nome, 'Passageiro Sem Motorista');
      expect(created.motoristaId, isNull);
    },
  );

  test('atualiza passageiro administrativo', () async {
    final service = AdminPassageiroPortalService(
      session: session,
      client: MockClient((request) async {
        expect(request.method, 'PUT');
        expect(request.url.path, '/company-admins/passageiros/10');

        final body = jsonDecode(request.body) as Map<String, dynamic>;
        expect(body['nome'], 'Passageiro Atualizado');
        expect(body['menorDeIdade'], false);

        return http.Response(
          jsonEncode({
            'id': 10,
            'nome': 'Passageiro Atualizado',
            'email': 'passageiro@vanrides.dev',
            'telefone': '51999999998',
            'endereco': 'Rua A',
            'bairro': 'Centro',
            'cidade': 'Porto Alegre',
            'estado': 'RS',
            'cep': '90000-000',
            'menorDeIdade': false,
            'motorista': {'id': 3, 'nome': 'Motorista Vinculado'},
          }),
          200,
        );
      }),
    );

    final updated = await service.updatePassageiro(
      10,
      const AdminPassageiroUpdateInput(
        nome: 'Passageiro Atualizado',
        email: 'passageiro@vanrides.dev',
        telefone: '51999999998',
        endereco: 'Rua A',
        bairro: 'Centro',
        cidade: 'Porto Alegre',
        estado: 'RS',
        cep: '90000-000',
        menorDeIdade: false,
      ),
    );

    expect(updated.nome, 'Passageiro Atualizado');
    expect(updated.menorDeIdade, isFalse);
  });
}
