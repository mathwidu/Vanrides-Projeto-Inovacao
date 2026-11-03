import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/data/driver_trip_service.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_record.dart';

void main() {
  test('retorna null quando o backend responde 204', () async {
    final service = DriverTripService(
      email: 'motorista@vanrides.dev',
      senha: 'Secret123',
      accessToken: 'token.driver',
      now: () => DateTime(2026, 3, 20, 21, 30),
      client: MockClient((request) async {
        expect(request.url.path, '/viagens/motorista/7/hoje');
        expect(request.url.queryParameters['data'], '2026-03-20');
        return http.Response('', 204);
      }),
    );

    final trip = await service.fetchTodayTrip(7);

    expect(trip, isNull);
  });

  test('faz parsing da viagem do motorista com lista de paradas', () async {
    final service = DriverTripService(
      email: 'motorista@vanrides.dev',
      senha: 'Secret123',
      accessToken: 'token.driver',
      now: () => DateTime(2026, 3, 20, 21, 30),
      client: MockClient((request) async {
        expect(request.url.queryParameters['data'], '2026-03-20');
        return http.Response(
          '''
          {
            "id": 11,
            "rotaBaseId": 4,
            "rotaNome": "Rota Centro",
            "servicoId": 15,
            "servicoNome": "Linha Manhã",
            "tipoOperacao": "IDA",
            "dataOperacao": "2026-03-13",
            "status": "IN_PROGRESS",
            "rotaVersao": 3,
            "etaMinutosAteProximaParada": 9,
            "etaAtualizadoEm": "2026-03-18T23:38:08.54997305",
            "motorista": {
              "id": 7,
              "nome": "Motorista QA"
            },
            "proximaParada": {
              "id": 90,
              "ordem": 2,
              "status": "PENDING",
              "etaMinutos": 9,
              "confirmadoPresenca": false,
              "proximaParada": true,
              "passageiro": {
                "id": 40,
                "nome": "Joao",
                "menorDeIdade": false
              }
            },
            "paradas": [
              {
                "id": 80,
                "ordem": 1,
                "status": "COMPLETED",
                "etaMinutos": 0,
                "confirmadoPresenca": true,
                "proximaParada": false,
                "passageiro": {
                  "id": 30,
                  "nome": "Ana",
                  "menorDeIdade": true
                }
              },
              {
                "id": 90,
                "ordem": 2,
                "status": "PENDING",
                "etaMinutos": 9,
                "confirmadoPresenca": false,
                "proximaParada": true,
                "passageiro": {
                  "id": 40,
                  "nome": "Joao",
                  "menorDeIdade": false
                }
              }
            ],
            "rotaGeografica": {
              "provider": "GOOGLE_DIRECTIONS_V1",
              "polyline": "",
              "navegacao": {
                "provider": "GOOGLE_MAPS_HANDOFF",
                "url": "https://maps.google.com/?q=original",
                "origem": "Origem inicial",
                "destino": "Destino final",
                "quantidadeWaypoints": 2
              },
              "navegacaoRestante": {
                "provider": "GOOGLE_MAPS_HANDOFF",
                "url": "https://maps.google.com/?q=restante",
                "origem": "Motorista",
                "destino": "Destino final",
                "quantidadeWaypoints": 1
              },
              "waypoints": [],
              "legs": [],
              "filaTotalParadas": 2
            },
            "navigationRoutePlan": {
              "routeId": "route-br-rs-001",
              "provider": "valhalla",
              "region": "BR-RS",
              "vehicleType": "van_school_safe",
              "coverageStatus": "validada",
              "offlinePackStatus": "ready",
              "qualityScore": 0.95,
              "estimatedDurationSeconds": 780,
              "estimatedDistanceMeters": 4200,
              "polyline": [
                [-30.0346, -51.2177],
                [-30.0350, -51.2180]
              ],
              "legs": [
                {
                  "fromStopId": "driver",
                  "toStopId": "stop-90",
                  "distanceMeters": 2100,
                  "durationSeconds": 390
                }
              ],
              "instructions": [
                {
                  "index": 0,
                  "text": "Siga pela Avenida Ipiranga.",
                  "voiceText": "Em 200 metros, siga pela Avenida Ipiranga.",
                  "distanceMeters": 200,
                  "maneuver": "continue"
                }
              ]
            }
          }
          ''',
          200,
          headers: {'content-type': 'application/json'},
        );
      }),
    );

    final trip = await service.fetchTodayTrip(7);

    expect(trip, isNotNull);
    expect(trip!.rotaNome, 'Rota Centro');
    expect(trip.servicoId, 15);
    expect(trip.nomeOperacional, 'Linha Manhã');
    expect(trip.tipoOperacaoLabel, 'Ida');
    expect(trip.rotaVersao, 3);
    expect(trip.etaMinutosAteProximaParada, 9);
    expect(trip.confirmadosCount, 1);
    expect(trip.pendentesCount, 1);
    expect(trip.proximaParada?.passageiroNome, 'Joao');
    expect(trip.proximaParada?.etaMinutos, 9);
    expect(trip.paradas, hasLength(2));
    expect(
      trip.rotaGeografica?.navegacaoRestante?.url,
      'https://maps.google.com/?q=restante',
    );
    expect(trip.rotaGeografica?.navegacao?.url, contains('original'));
    expect(trip.navigationRoutePlan?.routeId, 'route-br-rs-001');
    expect(trip.navigationRoutePlan?.coverageStatus, 'validada');
    expect(
      trip.navigationRoutePlan?.instructions.single.voiceText,
      'Em 200 metros, siga pela Avenida Ipiranga.',
    );
  });

  test('carrega agenda do motorista com multiplas viagens do dia', () async {
    final service = DriverTripService(
      email: 'motorista@vanrides.dev',
      senha: 'Secret123',
      accessToken: 'token.driver',
      now: () => DateTime(2026, 3, 20, 21, 30),
      client: MockClient((request) async {
        expect(request.url.path, '/viagens/motorista/7/dia');
        expect(request.url.queryParameters['data'], '2026-03-20');
        return http.Response(
          '''
          [
            {
              "id": 11,
              "rotaBaseId": 4,
              "rotaNome": "Rota Centro",
              "servicoId": 15,
              "servicoNome": "Linha Manhã",
              "tipoOperacao": "IDA",
              "dataOperacao": "2026-03-20",
              "status": "COMPLETED",
              "rotaVersao": 1,
              "horarioPartida": "07:00:00",
              "paradas": []
            },
            {
              "id": 12,
              "rotaBaseId": 4,
              "rotaNome": "Rota Centro",
              "servicoId": 16,
              "servicoNome": "Linha Tarde",
              "tipoOperacao": "VOLTA",
              "dataOperacao": "2026-03-20",
              "status": "PLANNED",
              "rotaVersao": 1,
              "horarioPartida": "13:00:00",
              "paradas": []
            }
          ]
          ''',
          200,
          headers: {'content-type': 'application/json'},
        );
      }),
    );

    final trips = await service.fetchDayTrips(7);

    expect(trips.map((trip) => trip.id), [11, 12]);
    expect(trips.first.horarioPartida, '07:00');
    expect(trips.last.nomeOperacional, 'Linha Tarde');
    expect(trips.last.tipoOperacaoLabel, 'Volta');
  });

  test(
    'usa data operacional explicita quando fixture E2E define override',
    () async {
      final service = DriverTripService(
        email: 'motorista@vanrides.dev',
        senha: 'Secret123',
        accessToken: 'token.driver',
        now: () => DateTime(2026, 3, 20, 23, 59),
        operationDateOverride: '2026-03-21',
        client: MockClient((request) async {
          expect(request.url.path, '/viagens/motorista/7/dia');
          expect(request.url.queryParameters['data'], '2026-03-21');
          return http.Response('[]', 200);
        }),
      );

      final trips = await service.fetchDayTrips(7);

      expect(trips, isEmpty);
    },
  );

  test('salva a nova ordem da rota do motorista', () async {
    final service = DriverTripService(
      email: 'motorista@vanrides.dev',
      senha: 'Secret123',
      accessToken: 'token.driver',
      now: () => DateTime(2026, 3, 20, 21, 30),
      client: MockClient((request) async {
        expect(request.method, 'PUT');
        expect(request.url.path, '/viagens/11/rota');
        expect(request.body, '{"paradaIds":[90,80]}');
        return http.Response(
          '''
          {
            "id": 11,
            "rotaBaseId": 4,
            "rotaNome": "Rota Centro",
            "dataOperacao": "2026-03-13",
            "status": "IN_PROGRESS",
            "rotaVersao": 4,
            "etaMinutosAteProximaParada": 6,
            "etaAtualizadoEm": "2026-03-18T23:44:23.240719494",
            "motorista": {
              "id": 7,
              "nome": "Motorista QA"
            },
            "proximaParada": {
              "id": 90,
              "ordem": 1,
              "status": "PENDING",
              "etaMinutos": 6,
              "confirmadoPresenca": false,
              "proximaParada": true,
              "passageiro": {
                "id": 40,
                "nome": "Joao",
                "menorDeIdade": false
              }
            },
            "paradas": [
              {
                "id": 90,
                "ordem": 1,
                "status": "PENDING",
                "etaMinutos": 6,
                "confirmadoPresenca": false,
                "proximaParada": true,
                "passageiro": {
                  "id": 40,
                  "nome": "Joao",
                  "menorDeIdade": false
                }
              },
              {
                "id": 80,
                "ordem": 2,
                "status": "COMPLETED",
                "etaMinutos": 0,
                "confirmadoPresenca": true,
                "proximaParada": false,
                "passageiro": {
                  "id": 30,
                  "nome": "Ana",
                  "menorDeIdade": true
                }
              }
            ]
          }
          ''',
          200,
          headers: {'content-type': 'application/json'},
        );
      }),
    );

    final trip = await service.reorderTrip(11, const [90, 80]);

    expect(trip.rotaVersao, 4);
    expect(trip.proximaParada?.ordem, 1);
    expect(trip.paradas.first.id, 90);
    expect(trip.paradas.first.etaMinutos, 6);
  });

  test(
    'envia a data local explicita ao carregar a viagem do motorista',
    () async {
      final service = DriverTripService(
        email: 'motorista@vanrides.dev',
        senha: 'Secret123',
        accessToken: 'token.driver',
        now: () => DateTime(2026, 3, 20, 23, 59),
        client: MockClient((request) async {
          expect(request.url.path, '/viagens/motorista/7/hoje');
          expect(request.url.queryParameters['data'], '2026-03-20');
          return http.Response('', 204);
        }),
      );

      final trip = await service.fetchTodayTrip(7);

      expect(trip, isNull);
    },
  );

  test('inicia a corrida com endpoint explicito do lifecycle', () async {
    final service = DriverTripService(
      email: 'motorista@vanrides.dev',
      senha: 'Secret123',
      accessToken: 'token.driver',
      client: MockClient((request) async {
        expect(request.method, 'POST');
        expect(request.url.path, '/viagens/11/iniciar');
        return http.Response(
          '''
          {
            "id": 11,
            "rotaBaseId": 4,
            "rotaNome": "Rota Centro",
            "dataOperacao": "2026-03-20",
            "status": "IN_PROGRESS",
            "rotaVersao": 1,
            "etaMinutosAteProximaParada": 12,
            "paradas": []
          }
          ''',
          200,
          headers: {'content-type': 'application/json'},
        );
      }),
    );

    final trip = await service.startTrip(11);

    expect(trip.status.name, 'inProgress');
  });

  test('conclui a parada atual com endpoint explicito do lifecycle', () async {
    final service = DriverTripService(
      email: 'motorista@vanrides.dev',
      senha: 'Secret123',
      accessToken: 'token.driver',
      client: MockClient((request) async {
        expect(request.method, 'POST');
        expect(request.url.path, '/viagens/11/paradas/90/concluir');
        return http.Response(
          '''
          {
            "id": 11,
            "rotaBaseId": 4,
            "rotaNome": "Rota Centro",
            "dataOperacao": "2026-03-20",
            "status": "IN_PROGRESS",
            "rotaVersao": 1,
            "etaMinutosAteProximaParada": 7,
            "paradas": []
          }
          ''',
          200,
          headers: {'content-type': 'application/json'},
        );
      }),
    );

    final trip = await service.completeStop(11, 90);

    expect(trip.etaMinutosAteProximaParada, 7);
  });

  test('marca no-show da parada atual com endpoint explicito', () async {
    final service = DriverTripService(
      email: 'motorista@vanrides.dev',
      senha: 'Secret123',
      accessToken: 'token.driver',
      client: MockClient((request) async {
        expect(request.method, 'POST');
        expect(request.url.path, '/viagens/11/paradas/90/no-show');
        return http.Response(
          '''
          {
            "id": 11,
            "rotaBaseId": 4,
            "rotaNome": "Rota Centro",
            "dataOperacao": "2026-03-20",
            "status": "IN_PROGRESS",
            "rotaVersao": 1,
            "etaMinutosAteProximaParada": 11,
            "proximaParada": {
              "id": 91,
              "ordem": 3,
              "status": "PENDING",
              "etaMinutos": 11,
              "confirmadoPresenca": true,
              "proximaParada": true,
              "passageiro": {
                "id": 41,
                "nome": "Maria",
                "menorDeIdade": true
              }
            },
            "paradas": [
              {
                "id": 90,
                "ordem": 2,
                "status": "NO_SHOW",
                "etaMinutos": 0,
                "confirmadoPresenca": true,
                "proximaParada": false,
                "passageiro": {
                  "id": 40,
                  "nome": "Joao",
                  "menorDeIdade": false
                }
              },
              {
                "id": 91,
                "ordem": 3,
                "status": "PENDING",
                "etaMinutos": 11,
                "confirmadoPresenca": true,
                "proximaParada": true,
                "passageiro": {
                  "id": 41,
                  "nome": "Maria",
                  "menorDeIdade": true
                }
              }
            ]
          }
          ''',
          200,
          headers: {'content-type': 'application/json'},
        );
      }),
    );

    final trip = await service.noShowStop(11, 90);

    expect(trip.paradas.first.status, TripTrackingStopStatus.noShow);
    expect(trip.proximaParada?.id, 91);
    expect(trip.etaMinutosAteProximaParada, 11);
  });
}
