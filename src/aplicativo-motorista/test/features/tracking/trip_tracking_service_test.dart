import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:vanrides_frontend_dev/features/tracking/data/trip_tracking_service.dart';

void main() {
  test('carrega a viagem atual do passageiro', () async {
    final service = TripTrackingService(
      email: 'passageiro@vanrides.dev',
      senha: 'Secret123',
      accessToken: 'token.passageiro',
      now: () => DateTime(2026, 3, 20, 21, 30),
      client: MockClient((request) async {
        expect(request.url.path, '/viagens/passageiro/21/atual');
        expect(request.url.queryParameters['data'], '2026-03-20');
        expect(request.headers['Authorization'], 'Bearer token.passageiro');
        return http.Response(
          '''
          {
            "id": 30,
            "rotaNome": "Rota Escolar Manha",
            "servicoId": 15,
            "servicoNome": "Linha Manhã",
            "tipoOperacao": "IDA",
            "dataOperacao": "2026-03-12",
            "status": "IN_PROGRESS",
            "rotaVersao": 2,
            "etaMinutosAtePassageiro": 12,
            "etaAtualizadoEm": "2026-03-18T23:38:08.54997305",
            "motorista": {
              "id": 7,
              "nome": "Motorista Portal",
              "email": "motorista@vanrides.dev",
              "telefone": "51999990007"
            },
            "paradaDoPassageiro": {
              "id": 300,
              "ordem": 1,
              "status": "PENDING",
              "etaMinutos": 12,
              "confirmadoPresenca": true,
              "proximaParada": true,
              "passageiro": {
                "id": 21,
                "nome": "Ana",
                "menorDeIdade": true
              }
            }
          }
          ''',
          200,
          headers: {'content-type': 'application/json'},
        );
      }),
    );

    final trip = await service.fetchCurrentTrip(21);

    expect(trip, isNotNull);
    expect(trip!.rotaNome, 'Rota Escolar Manha');
    expect(trip.servicoId, 15);
    expect(trip.nomeOperacional, 'Linha Manhã');
    expect(trip.tipoOperacaoLabel, 'Ida');
    expect(trip.rotaVersao, 2);
    expect(trip.etaMinutosAtePassageiro, 12);
    expect(trip.motorista?.nome, 'Motorista Portal');
    expect(trip.paradaDoPassageiro?.etaMinutos, 12);
    expect(trip.paradaDoPassageiro?.confirmadoPresenca, isTrue);
  });

  test('carrega agenda do passageiro com multiplas viagens do dia', () async {
    final service = TripTrackingService(
      email: 'passageiro@vanrides.dev',
      senha: 'Secret123',
      accessToken: 'token.passageiro',
      now: () => DateTime(2026, 3, 20, 21, 30),
      client: MockClient((request) async {
        expect(request.url.path, '/viagens/passageiro/21/dia');
        expect(request.url.queryParameters['data'], '2026-03-20');
        return http.Response(
          '''
          [
            {
              "id": 30,
              "rotaNome": "Rota Escolar Manha",
              "servicoId": 15,
              "servicoNome": "Linha Manhã",
              "tipoOperacao": "IDA",
              "dataOperacao": "2026-03-20",
              "status": "COMPLETED",
              "rotaVersao": 1,
              "horarioPartida": "07:00:00",
              "etaMinutosAtePassageiro": 0
            },
            {
              "id": 31,
              "rotaNome": "Rota Escolar Tarde",
              "servicoId": 16,
              "servicoNome": "Linha Tarde",
              "tipoOperacao": "VOLTA",
              "dataOperacao": "2026-03-20",
              "status": "PLANNED",
              "rotaVersao": 1,
              "horarioPartida": "13:00:00",
              "etaMinutosAtePassageiro": 18
            }
          ]
          ''',
          200,
          headers: {'content-type': 'application/json'},
        );
      }),
    );

    final trips = await service.fetchDayTrips(21);

    expect(trips.map((trip) => trip.id), [30, 31]);
    expect(trips.first.horarioPartida, '07:00');
    expect(trips.last.nomeOperacional, 'Linha Tarde');
    expect(trips.last.tipoOperacaoLabel, 'Volta');
  });

  test('retorna nulo quando o passageiro nao tem viagem hoje', () async {
    final service = TripTrackingService(
      email: 'passageiro@vanrides.dev',
      senha: 'Secret123',
      accessToken: 'token.passageiro',
      now: () => DateTime(2026, 3, 20, 21, 30),
      client: MockClient((request) async => http.Response('', 204)),
    );

    final trip = await service.fetchCurrentTrip(21);

    expect(trip, isNull);
  });

  test('propaga mensagem amigavel em falha de contrato', () async {
    final service = TripTrackingService(
      email: 'passageiro@vanrides.dev',
      senha: 'Secret123',
      accessToken: 'token.passageiro',
      now: () => DateTime(2026, 3, 20, 21, 30),
      client: MockClient(
        (request) async =>
            http.Response('{"message":"Passageiro nao encontrado"}', 404),
      ),
    );

    expect(
      () => service.fetchCurrentTrip(99),
      throwsA(
        predicate(
          (error) => error.toString().contains('Passageiro nao encontrado'),
        ),
      ),
    );
  });

  test('envia a data local explicita ao carregar a viagem atual', () async {
    final service = TripTrackingService(
      email: 'passageiro@vanrides.dev',
      senha: 'Secret123',
      accessToken: 'token.passageiro',
      now: () => DateTime(2026, 3, 20, 23, 59),
      client: MockClient((request) async {
        expect(request.url.path, '/viagens/passageiro/21/atual');
        expect(request.url.queryParameters['data'], '2026-03-20');
        return http.Response('', 204);
      }),
    );

    final trip = await service.fetchCurrentTrip(21);

    expect(trip, isNull);
  });

  test('usa data operacional explicita quando fixture E2E define override', () async {
    final service = TripTrackingService(
      email: 'passageiro@vanrides.dev',
      senha: 'Secret123',
      accessToken: 'token.passageiro',
      now: () => DateTime(2026, 3, 20, 23, 59),
      operationDateOverride: '2026-03-21',
      client: MockClient((request) async {
        expect(request.url.path, '/viagens/passageiro/21/dia');
        expect(request.url.queryParameters['data'], '2026-03-21');
        return http.Response('[]', 200);
      }),
    );

    final trips = await service.fetchDayTrips(21);

    expect(trips, isEmpty);
  });
}
