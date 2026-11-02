import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:vanrides_frontend_dev/features/service_presence/data/service_presence_service.dart';
import 'package:vanrides_frontend_dev/features/service_presence/domain/passenger_available_service_record.dart';

void main() {
  test('busca servicos disponiveis do passageiro na data', () async {
    final service = ServicePresenceService(
      email: 'passageiro@vanrides.dev',
      senha: '',
      accessToken: 'token.passenger',
      client: MockClient((request) async {
        expect(request.method, 'GET');
        expect(request.url.path, '/passageiros/10/servicos-disponiveis');
        expect(request.url.queryParameters['data'], '2026-05-25');
        expect(request.headers['Authorization'], 'Bearer token.passenger');
        return _jsonResponse([
          {
            'servicoId': 15,
            'nome': 'Linha Manhã',
            'tipoOperacao': 'IDA',
            'dataOperacao': '2026-05-25',
            'horarioPartida': '07:30:00',
            'destinoFinalNome': 'Escola Central',
            'politicaPresenca': 'EXIGE_CONFIRMACAO',
            'motoristaPadrao': {
              'id': 7,
              'nome': 'Motorista Portal',
              'email': 'm@vanrides.dev',
              'telefone': '51999990000',
            },
            'presencaId': null,
            'presencaStatus': 'UNKNOWN',
          },
        ]);
      }),
    );

    final services = await service.fetchAvailableServices(
      passageiroId: 10,
      data: DateTime(2026, 5, 25),
    );

    expect(services, hasLength(1));
    expect(services.first.servicoId, 15);
    expect(services.first.horarioPartida, '07:30');
    expect(services.first.motoristaPadrao?.nome, 'Motorista Portal');
    expect(services.first.statusLabel, 'Sem decisão');
  });

  test('ignora servicos que nao exigem confirmacao do passageiro', () async {
    final service = ServicePresenceService(
      email: 'passageiro@vanrides.dev',
      senha: '',
      accessToken: 'token.passenger',
      client: MockClient((_) async {
        return _jsonResponse([
          {
            'servicoId': 15,
            'nome': 'Linha Manhã',
            'tipoOperacao': 'IDA',
            'dataOperacao': '2026-05-25',
            'horarioPartida': '07:30:00',
            'destinoFinalNome': 'Escola Central',
            'politicaPresenca': 'EXIGE_CONFIRMACAO',
            'presencaId': null,
            'presencaStatus': 'UNKNOWN',
          },
          {
            'servicoId': 31,
            'nome': 'Linha Automática',
            'tipoOperacao': 'VOLTA',
            'dataOperacao': '2026-05-25',
            'horarioPartida': '18:00:00',
            'destinoFinalNome': 'Casa',
            'politicaPresenca': 'INCLUIR_POR_PADRAO',
            'presencaId': null,
            'presencaStatus': 'UNKNOWN',
          },
        ]);
      }),
    );

    final services = await service.fetchAvailableServices(
      passageiroId: 10,
      data: DateTime(2026, 5, 25),
    );

    expect(services.map((service) => service.servicoId), [15]);
  });

  test('cria presenca por servico quando ainda nao existe decisao', () async {
    final service = ServicePresenceService(
      email: 'passageiro@vanrides.dev',
      senha: '',
      accessToken: 'token.passenger',
      client: MockClient((request) async {
        expect(request.method, 'POST');
        expect(request.url.path, '/presencas-servico');
        final payload = jsonDecode(request.body) as Map<String, dynamic>;
        expect(payload['servicoId'], 15);
        expect(payload['passageiroId'], 10);
        expect(payload['dataOperacao'], '2026-05-25');
        expect(payload['status'], 'CONFIRMED');
        return _jsonResponse({'id': 99}, 201);
      }),
    );

    await service.setPresenceStatus(
      service: _availableService(presencaId: null),
      passageiroId: 10,
      status: 'CONFIRMED',
    );
  });

  test('atualiza presenca por servico quando decisao ja existe', () async {
    final service = ServicePresenceService(
      email: 'passageiro@vanrides.dev',
      senha: '',
      accessToken: 'token.passenger',
      client: MockClient((request) async {
        expect(request.method, 'PUT');
        expect(request.url.path, '/presencas-servico/99');
        final payload = jsonDecode(request.body) as Map<String, dynamic>;
        expect(payload['status'], 'ABSENT');
        return _jsonResponse({'id': 99}, 200);
      }),
    );

    await service.setPresenceStatus(
      service: _availableService(presencaId: 99),
      passageiroId: 10,
      status: 'ABSENT',
    );
  });

  test('remove decisao explicita de presenca por servico', () async {
    final service = ServicePresenceService(
      email: 'passageiro@vanrides.dev',
      senha: '',
      accessToken: 'token.passenger',
      client: MockClient((request) async {
        expect(request.method, 'DELETE');
        expect(request.url.path, '/presencas-servico/99');
        return http.Response('', 204);
      }),
    );

    await service.clearPresence(99);
  });
}

PassengerAvailableServiceRecord _availableService({required int? presencaId}) {
  return PassengerAvailableServiceRecord(
    servicoId: 15,
    nome: 'Linha Manhã',
    tipoOperacao: 'IDA',
    dataOperacao: DateTime(2026, 5, 25),
    politicaPresenca: 'EXIGE_CONFIRMACAO',
    presencaStatus: presencaId == null ? 'UNKNOWN' : 'CONFIRMED',
    presencaId: presencaId,
  );
}

http.Response _jsonResponse(Object body, [int status = 200]) {
  return http.Response(
    jsonEncode(body),
    status,
    headers: {'content-type': 'application/json; charset=utf-8'},
  );
}
