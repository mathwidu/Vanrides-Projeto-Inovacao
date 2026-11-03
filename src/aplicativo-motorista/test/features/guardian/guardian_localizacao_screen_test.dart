import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:vanrides_frontend_dev/features/guardian/data/guardian_localizacao_service.dart';
import 'package:vanrides_frontend_dev/features/guardian/data/guardian_passageiro_service.dart';
import 'package:vanrides_frontend_dev/features/guardian/domain/guardian_linked_passageiro_record.dart';
import 'package:vanrides_frontend_dev/features/guardian/domain/guardian_localizacao_snapshot.dart';
import 'package:vanrides_frontend_dev/features/guardian/domain/guardian_session.dart';
import 'package:vanrides_frontend_dev/features/guardian/presentation/guardian_localizacao_screen.dart';
import 'package:vanrides_frontend_dev/features/tracking/data/trip_tracking_service.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_route_geometry_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/trip_live_map_panel.dart';
import 'package:vanrides_frontend_dev/services/websocket_service.dart';

import '../../support/fakes/fake_trip_route_event_gateway.dart';
import 'support/guardian_test_actions.dart';

class _FakePassageiroGateway implements GuardianPassageiroGateway {
  const _FakePassageiroGateway(this.records);

  final List<GuardianLinkedPassageiroRecord> records;

  @override
  Future<List<GuardianLinkedPassageiroRecord>> fetchLinkedPassageiros() async {
    await Future<void>.delayed(Duration.zero);
    return records;
  }
}

class _FakeLocalizacaoGateway implements GuardianLocalizacaoGateway {
  const _FakeLocalizacaoGateway(this.snapshots);

  final List<GuardianLocalizacaoSnapshot> snapshots;

  @override
  Future<List<GuardianLocalizacaoSnapshot>> fetchHistoricoPorMotorista(
    int motoristaId,
  ) async {
    await Future<void>.delayed(Duration.zero);
    return snapshots;
  }
}

class _FakeWebSocketService implements WebSocketServiceBase {
  @override
  bool get isConnected => true;

  @override
  LocationMessageHandler? onLocationReceived;

  @override
  void connect({
    required String authorizationHeader,
    required int companyId,
    String? topicSuffix,
  }) {}

  @override
  void disconnect() {}

  @override
  void dispose() {}

  @override
  void sendLocation(
    double latitude,
    double longitude,
    int motoristaId, {
    int? companyId,
  }) {}
}

class _FakeTripTrackingGateway implements TripTrackingGateway {
  _FakeTripTrackingGateway(
    this.record, {
    this.dayTrips,
    this.dayTripsByPassageiroId = const {},
  });

  final TripTrackingRecord? record;
  final List<TripTrackingRecord>? dayTrips;
  final Map<int, List<TripTrackingRecord>> dayTripsByPassageiroId;
  int fetchCurrentTripCalls = 0;
  int fetchDayTripsCalls = 0;

  @override
  Future<TripTrackingRecord?> fetchCurrentTrip(int passageiroId) async {
    fetchCurrentTripCalls++;
    await Future<void>.delayed(Duration.zero);
    return record;
  }

  @override
  Future<List<TripTrackingRecord>> fetchDayTrips(int passageiroId) async {
    fetchDayTripsCalls++;
    await Future<void>.delayed(Duration.zero);
    final mappedTrips = dayTripsByPassageiroId[passageiroId];
    if (mappedTrips != null) {
      return mappedTrips;
    }
    final trips = dayTrips;
    if (trips != null) {
      return trips;
    }
    return record == null ? const [] : [record!];
  }
}

const _guardianRouteGeometry = TripRouteGeometryRecord(
  provider: 'GOOGLE_DIRECTIONS_V1',
  polyline: '',
  navegacao: TripNavigationRecord(
    provider: 'GOOGLE_MAPS_HANDOFF',
    url: 'https://maps.google.com/?q=feevale',
    origem: 'Origem',
    destino: 'Destino',
    quantidadeWaypoints: 1,
  ),
  waypoints: [
    TripRouteWaypointRecord(
      tipo: 'STOP',
      paradaId: 300,
      passageiroId: 10,
      nome: 'Ana',
      latitude: -30.1,
      longitude: -51.2,
      ordem: 1,
      proximaParada: true,
      paradaDoPassageiro: true,
    ),
  ],
  legs: [],
  filaTotalParadas: 1,
  posicaoPassageiroNaFila: 1,
  paradasAntesDoPassageiro: 0,
);

TripTrackingRecord _guardianTripRecord({
  required int id,
  required String servicoNome,
  required String tipoOperacao,
  required String horarioPartida,
  required String motoristaNome,
  TripTrackingStatus status = TripTrackingStatus.planned,
}) {
  return TripTrackingRecord(
    id: id,
    rotaNome: 'Rota Escolar',
    servicoId: id + 100,
    servicoNome: servicoNome,
    tipoOperacao: tipoOperacao,
    dataOperacao: DateTime(2026, 3, 12),
    status: status,
    rotaVersao: 2,
    etaMinutosAtePassageiro: 12,
    etaAtualizadoEm: DateTime(2026, 3, 18, 23, 38),
    motorista: TripTrackingDriverRecord(id: id + 3, nome: motoristaNome),
    paradaDoPassageiro: TripTrackingPassengerStopRecord(
      id: id + 300,
      ordem: 1,
      status: TripTrackingStopStatus.pending,
      etaMinutos: 12,
      confirmadoPresenca: true,
      proximaParada: true,
      passageiroId: 10,
      passageiroNome: 'Ana',
      menorDeIdade: true,
    ),
    rotaGeografica: _guardianRouteGeometry,
    horarioPartida: horarioPartida,
  );
}

void main() {
  setUpAll(() async {
    await initializeDateFormatting('pt_BR');
  });

  const session = GuardianSession(
    guardianId: 7,
    guardianName: 'Responsavel QA',
    companyId: 88,
    email: 'responsavel@vanrides.dev',
    accessToken: 'token.guardian',
  );

  testWidgets(
    'mostra status da viagem e atalho para mapa quando tracking existe',
    (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: GuardianLocalizacaoScreen(
              session: session,
              passageiroGateway: const _FakePassageiroGateway([
                GuardianLinkedPassageiroRecord(
                  id: 10,
                  nome: 'Ana',
                  email: 'ana@vanrides.dev',
                  telefone: '51999990000',
                  endereco: 'Rua A',
                  bairro: 'Centro',
                  cidade: 'Porto Alegre',
                  estado: 'RS',
                  cep: '90000-000',
                  menorDeIdade: true,
                  motoristaId: 3,
                  motoristaNome: 'Motorista Portal',
                ),
              ]),
              trackingGateway: _FakeTripTrackingGateway(
                TripTrackingRecord(
                  id: 30,
                  rotaNome: 'Rota Escolar Manha',
                  dataOperacao: DateTime(2026, 3, 12),
                  status: TripTrackingStatus.inProgress,
                  rotaVersao: 2,
                  etaMinutosAtePassageiro: 12,
                  etaAtualizadoEm: DateTime(2026, 3, 18, 23, 38),
                  motorista: const TripTrackingDriverRecord(
                    id: 3,
                    nome: 'Motorista Portal',
                  ),
                  paradaDoPassageiro: const TripTrackingPassengerStopRecord(
                    id: 300,
                    ordem: 1,
                    status: TripTrackingStopStatus.pending,
                    etaMinutos: 12,
                    confirmadoPresenca: true,
                    proximaParada: true,
                    passageiroId: 10,
                    passageiroNome: 'Ana',
                    menorDeIdade: true,
                  ),
                  rotaGeografica: _guardianRouteGeometry,
                ),
              ),
              localizacaoGateway: _FakeLocalizacaoGateway([
                GuardianLocalizacaoSnapshot(
                  latitude: -30.1,
                  longitude: -51.2,
                  dataHora: DateTime.now(),
                ),
              ]),
              tripRouteEventGateway: FakeTripRouteEventGateway(),
              webSocketService: _FakeWebSocketService(),
            ),
          ),
        ),
      );

      await tester.pumpAndSettle();

      expect(find.text('Parada 1 • Próximo da fila'), findsOneWidget);
      expect(find.textContaining('Você é o próximo da fila'), findsNothing);

      await expandGuardianTripSheet(tester, revealDetails: true);

      expect(find.text('Acompanhamento seguro'), findsOneWidget);
      expect(find.text('Pessoa acompanhada'), findsAtLeastNWidgets(1));
      expect(find.text('Van da viagem'), findsOneWidget);
      expect(find.text('Minha parada'), findsOneWidget);
      expect(find.text('Última atualização'), findsOneWidget);
      expect(find.text('Status da viagem'), findsOneWidget);
      expect(find.textContaining('Atualizada às'), findsOneWidget);

      await tester.tap(find.text('Van'));
      await tester.pumpAndSettle();

      final panel = tester.widget<TripLiveMapPanel>(
        find.byType(TripLiveMapPanel),
      );
      expect(panel.focusMode, TripLiveMapFocusMode.vehicleOnly);
    },
  );

  testWidgets(
    'mostra estado vazio quando nao existe viagem para o passageiro',
    (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: GuardianLocalizacaoScreen(
              session: session,
              passageiroGateway: const _FakePassageiroGateway([
                GuardianLinkedPassageiroRecord(
                  id: 10,
                  nome: 'Ana',
                  email: 'ana@vanrides.dev',
                  telefone: '51999990000',
                  endereco: 'Rua A',
                  bairro: 'Centro',
                  cidade: 'Porto Alegre',
                  estado: 'RS',
                  cep: '90000-000',
                  menorDeIdade: true,
                  motoristaId: 3,
                  motoristaNome: 'Motorista Portal',
                ),
              ]),
              trackingGateway: _FakeTripTrackingGateway(null),
              localizacaoGateway: const _FakeLocalizacaoGateway([]),
              tripRouteEventGateway: FakeTripRouteEventGateway(),
              webSocketService: _FakeWebSocketService(),
            ),
          ),
        ),
      );

      await tester.pumpAndSettle();
      await tester.scrollUntilVisible(
        find.text('Sem viagem hoje'),
        200,
        scrollable: find.byType(Scrollable).first,
      );

      expect(find.text('Sem viagem hoje'), findsOneWidget);
    },
  );

  testWidgets(
    'prioriza passageiro sem motorista fixo quando ele vem primeiro na familia',
    (tester) async {
      final trip = _guardianTripRecord(
        id: 44,
        servicoNome: 'Linha Manha',
        tipoOperacao: 'IDA',
        horarioPartida: '07:00',
        motoristaNome: 'Motorista do Dia',
        status: TripTrackingStatus.planned,
      );
      final trackingGateway = _FakeTripTrackingGateway(
        null,
        dayTripsByPassageiroId: {
          11: [trip],
          10: const [],
        },
      );

      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: GuardianLocalizacaoScreen(
              session: session,
              passageiroGateway: const _FakePassageiroGateway([
                GuardianLinkedPassageiroRecord(
                  id: 11,
                  nome: 'Bruno',
                  email: 'bruno@vanrides.dev',
                  telefone: '51999991111',
                  endereco: 'Rua B',
                  bairro: 'Centro',
                  cidade: 'Porto Alegre',
                  estado: 'RS',
                  cep: '90010-000',
                  menorDeIdade: true,
                ),
                GuardianLinkedPassageiroRecord(
                  id: 10,
                  nome: 'Ana',
                  email: 'ana@vanrides.dev',
                  telefone: '51999990000',
                  endereco: 'Rua A',
                  bairro: 'Centro',
                  cidade: 'Porto Alegre',
                  estado: 'RS',
                  cep: '90000-000',
                  menorDeIdade: true,
                  motoristaId: 3,
                  motoristaNome: 'Motorista Portal',
                ),
              ]),
              trackingGateway: trackingGateway,
              localizacaoGateway: const _FakeLocalizacaoGateway([]),
              tripRouteEventGateway: FakeTripRouteEventGateway(),
              webSocketService: _FakeWebSocketService(),
            ),
          ),
        ),
      );

      await tester.pumpAndSettle();

      expect(trackingGateway.fetchDayTripsCalls, 1);
      await expandGuardianTripSheet(tester);

      expect(find.text('Linha Manha'), findsOneWidget);
      expect(find.text('Motorista do Dia'), findsOneWidget);
      expect(find.text('Bruno'), findsAtLeastNWidgets(1));
      expect(find.text('Pessoa acompanhada'), findsAtLeastNWidgets(1));
    },
  );

  testWidgets('mostra tipo da operacao na viagem unica do responsavel', (
    tester,
  ) async {
    final trip = _guardianTripRecord(
      id: 32,
      servicoNome: 'Linha Almoco',
      tipoOperacao: 'VOLTA',
      horarioPartida: '12:00',
      motoristaNome: 'Motorista Almoco',
      status: TripTrackingStatus.planned,
    );

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: GuardianLocalizacaoScreen(
            session: session,
            passageiroGateway: const _FakePassageiroGateway([
              GuardianLinkedPassageiroRecord(
                id: 10,
                nome: 'Ana',
                email: 'ana@vanrides.dev',
                telefone: '51999990000',
                endereco: 'Rua A',
                bairro: 'Centro',
                cidade: 'Porto Alegre',
                estado: 'RS',
                cep: '90000-000',
                menorDeIdade: true,
                motoristaId: 3,
                motoristaNome: 'Motorista Portal',
              ),
            ]),
            trackingGateway: _FakeTripTrackingGateway(trip),
            localizacaoGateway: const _FakeLocalizacaoGateway([]),
            tripRouteEventGateway: FakeTripRouteEventGateway(),
            webSocketService: _FakeWebSocketService(),
          ),
        ),
      ),
    );

    await tester.pumpAndSettle();
    await expandGuardianTripSheet(tester);

    expect(find.text('Linha Almoco'), findsOneWidget);
    expect(find.text('Volta'), findsAtLeastNWidgets(1));
  });

  testWidgets(
    'abre viagem do deep link quando responsavel tem passageiros vinculados',
    (tester) async {
      final manha = _guardianTripRecord(
        id: 30,
        servicoNome: 'Linha Manha',
        tipoOperacao: 'IDA',
        horarioPartida: '07:00',
        motoristaNome: 'Motorista Manha',
        status: TripTrackingStatus.planned,
      );
      final tarde = _guardianTripRecord(
        id: 42,
        servicoNome: 'Linha Tarde',
        tipoOperacao: 'VOLTA',
        horarioPartida: '13:00',
        motoristaNome: 'Motorista Tarde',
        status: TripTrackingStatus.planned,
      );
      final trackingGateway = _FakeTripTrackingGateway(
        null,
        dayTripsByPassageiroId: {
          10: [manha],
          11: [tarde],
        },
      );

      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: GuardianLocalizacaoScreen(
              session: session,
              initialTripId: 42,
              passageiroGateway: const _FakePassageiroGateway([
                GuardianLinkedPassageiroRecord(
                  id: 10,
                  nome: 'Ana',
                  email: 'ana@vanrides.dev',
                  telefone: '51999990000',
                  endereco: 'Rua A',
                  bairro: 'Centro',
                  cidade: 'Porto Alegre',
                  estado: 'RS',
                  cep: '90000-000',
                  menorDeIdade: true,
                  motoristaId: 3,
                  motoristaNome: 'Motorista Portal',
                ),
                GuardianLinkedPassageiroRecord(
                  id: 11,
                  nome: 'Bruno',
                  email: 'bruno@vanrides.dev',
                  telefone: '51999991111',
                  endereco: 'Rua B',
                  bairro: 'Centro',
                  cidade: 'Porto Alegre',
                  estado: 'RS',
                  cep: '90000-000',
                  menorDeIdade: true,
                  motoristaId: 4,
                  motoristaNome: 'Motorista Tarde',
                ),
              ]),
              trackingGateway: trackingGateway,
              localizacaoGateway: const _FakeLocalizacaoGateway([]),
              tripRouteEventGateway: FakeTripRouteEventGateway(),
              webSocketService: _FakeWebSocketService(),
            ),
          ),
        ),
      );

      await tester.pumpAndSettle();

      expect(trackingGateway.fetchDayTripsCalls, 2);
      await expandGuardianTripSheet(tester);

      expect(find.text('Linha Tarde'), findsOneWidget);
      expect(find.text('Volta'), findsAtLeastNWidgets(1));
      expect(find.text('Motorista Tarde'), findsOneWidget);
      expect(find.text('Bruno'), findsAtLeastNWidgets(1));
    },
  );

  testWidgets('responsavel escolhe entre viagens do dia do passageiro', (
    tester,
  ) async {
    final manha = _guardianTripRecord(
      id: 30,
      servicoNome: 'Linha Manha',
      tipoOperacao: 'IDA',
      horarioPartida: '07:00',
      motoristaNome: 'Motorista Manha',
      status: TripTrackingStatus.planned,
    );
    final tarde = _guardianTripRecord(
      id: 31,
      servicoNome: 'Linha Tarde',
      tipoOperacao: 'VOLTA',
      horarioPartida: '13:00',
      motoristaNome: 'Motorista Tarde',
      status: TripTrackingStatus.planned,
    );
    final trackingGateway = _FakeTripTrackingGateway(
      manha,
      dayTrips: [manha, tarde],
    );

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: GuardianLocalizacaoScreen(
            session: session,
            passageiroGateway: const _FakePassageiroGateway([
              GuardianLinkedPassageiroRecord(
                id: 10,
                nome: 'Ana',
                email: 'ana@vanrides.dev',
                telefone: '51999990000',
                endereco: 'Rua A',
                bairro: 'Centro',
                cidade: 'Porto Alegre',
                estado: 'RS',
                cep: '90000-000',
                menorDeIdade: true,
                motoristaId: 3,
                motoristaNome: 'Motorista Portal',
              ),
            ]),
            trackingGateway: trackingGateway,
            localizacaoGateway: const _FakeLocalizacaoGateway([]),
            tripRouteEventGateway: FakeTripRouteEventGateway(),
            webSocketService: _FakeWebSocketService(),
          ),
        ),
      ),
    );

    await tester.pumpAndSettle();
    await expandGuardianTripSheet(tester, revealDetails: true);

    expect(trackingGateway.fetchDayTripsCalls, 1);
    expect(trackingGateway.fetchCurrentTripCalls, 0);
    expect(find.text('2 viagens hoje'), findsOneWidget);
    expect(find.text('Linha Manha'), findsOneWidget);
    expect(find.text('Motorista Manha'), findsOneWidget);
    final tardeKey = find.byKey(
      const Key('guardian.localizacao.agendaViagem.31'),
    );
    expect(tardeKey, findsOneWidget);
    await tester.tap(tardeKey);
    await tester.pumpAndSettle();

    expect(find.text('Linha Tarde'), findsOneWidget);
    expect(find.text('Motorista Tarde'), findsOneWidget);
  });
}
