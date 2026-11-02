import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:vanrides_frontend_dev/features/guardian/domain/guardian_linked_passageiro_record.dart';
import 'package:vanrides_frontend_dev/features/guardian/domain/guardian_localizacao_snapshot.dart';
import 'package:vanrides_frontend_dev/features/guardian/presentation/widgets/guardian_localizacao_widgets.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_route_geometry_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_state_record.dart';

void main() {
  setUpAll(() async {
    await initializeDateFormatting('pt_BR');
  });

  testWidgets('card do passageiro selecionado mostra dados operacionais', (
    tester,
  ) async {
    await tester.pumpWidget(
      _wrap(
        const GuardianLocalizacaoSelectedPassageiroCard(
          passageiro: GuardianLinkedPassageiroRecord(
            id: 10,
            nome: 'Ana Passageira',
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
        ),
      ),
    );

    expect(find.text('Ana Passageira'), findsOneWidget);
    expect(find.text('Passageiro menor de idade'), findsOneWidget);
    expect(find.text('Motorista cadastrado: Motorista Portal'), findsOneWidget);
    expect(
      find.text('Endereço: Rua A, Centro, Porto Alegre, RS'),
      findsOneWidget,
    );
  });

  testWidgets('timeline mostra pontos recentes e estado vazio', (tester) async {
    await tester.pumpWidget(
      _wrap(
        GuardianLocalizacaoTimelineCard(
          historico: [
            GuardianLocalizacaoSnapshot(
              latitude: -30.123456,
              longitude: -51.654321,
              dataHora: DateTime(2026, 6, 12, 8, 45),
            ),
          ],
        ),
      ),
    );

    expect(find.text('Atualizações recentes'), findsOneWidget);
    expect(find.text('-30.12346, -51.65432'), findsOneWidget);
    expect(find.text('12/06/2026 08:45'), findsOneWidget);

    await tester.pumpWidget(
      _wrap(const GuardianLocalizacaoTimelineCard(historico: [])),
    );

    expect(
      find.text('Nenhum ponto de localização foi recebido ainda.'),
      findsOneWidget,
    );
  });

  testWidgets('card de mensagem aciona callback de retry', (tester) async {
    var retried = false;

    await tester.pumpWidget(
      _wrap(
        GuardianLocalizacaoMessageCard(
          icon: Icons.warning_amber_outlined,
          color: const Color(0xFFB3261E),
          title: 'Erro ao carregar rastreio',
          description: 'Tente novamente em alguns instantes.',
          actionLabel: 'Tentar novamente',
          onAction: () async {
            retried = true;
          },
        ),
      ),
    );

    expect(find.text('Erro ao carregar rastreio'), findsOneWidget);
    expect(find.text('Tente novamente em alguns instantes.'), findsOneWidget);

    await tester.tap(find.text('Tentar novamente'));
    await tester.pump();

    expect(retried, isTrue);
  });

  testWidgets('agenda do dia mostra viagens e seleciona item por callback', (
    tester,
  ) async {
    TripTrackingRecord? selectedTrip;
    final morning = _trip(
      id: 30,
      servicoNome: 'Linha Manha',
      tipoOperacao: 'IDA',
      horarioPartida: '07:00',
      status: TripTrackingStatus.inProgress,
    );
    final afternoon = _trip(
      id: 31,
      servicoNome: 'Linha Tarde',
      tipoOperacao: 'VOLTA',
      horarioPartida: '13:00',
      status: TripTrackingStatus.planned,
    );

    await tester.pumpWidget(
      _wrap(
        GuardianLocalizacaoDayAgendaSelector(
          agendaKey: const Key('guardian.localizacao.agendaViagens'),
          trips: [morning, afternoon],
          selectedTripId: morning.id,
          tripButtonKeyBuilder:
              (tripId) => Key('guardian.localizacao.agendaViagem.$tripId'),
          onTripSelected: (trip) {
            selectedTrip = trip;
          },
        ),
      ),
    );

    expect(
      find.byKey(const Key('guardian.localizacao.agendaViagens')),
      findsOneWidget,
    );
    expect(find.text('2 viagens hoje'), findsOneWidget);
    expect(find.text('07:00 • Ida • Linha Manha'), findsOneWidget);
    expect(find.text('13:00 • Volta • Linha Tarde'), findsOneWidget);

    await tester.tap(
      find.byKey(const Key('guardian.localizacao.agendaViagem.31')),
    );
    await tester.pump();

    expect(selectedTrip?.id, 31);
  });

  testWidgets('sheet ativo mostra viagem e despacha callbacks principais', (
    tester,
  ) async {
    final passageiro = _passageiro(id: 10, nome: 'Ana');
    final bruno = _passageiro(id: 11, nome: 'Bruno');
    final morning = _trip(
      id: 30,
      servicoNome: 'Linha Manha',
      tipoOperacao: 'IDA',
      horarioPartida: '07:00',
      status: TripTrackingStatus.inProgress,
      stopsBefore: 2,
      motoristaNome: 'Motorista Manha',
      trackingState: TripTrackingStateRecord(
        routeSnapshotStatus: TripRouteSnapshotStatus.materialized,
        locationFreshness: TripLocationFreshness.delayed,
        etaConfidence: TripEtaConfidence.medium,
        stopsRemaining: 3,
        stopsCompleted: 1,
        stopsBeforePassenger: 2,
        routeRecomputeReason: TripRouteRecomputeReason.none,
        driverLocationUpdatedAt: DateTime(2026, 6, 13, 7, 2),
      ),
    );
    final afternoon = _trip(
      id: 31,
      servicoNome: 'Linha Tarde',
      tipoOperacao: 'VOLTA',
      horarioPartida: '13:00',
      status: TripTrackingStatus.planned,
      motoristaNome: 'Motorista Tarde',
    );
    TripTrackingRecord? selectedTrip;
    GuardianLinkedPassageiroRecord? selectedPassageiro;
    var openedMap = false;

    await tester.pumpWidget(
      _wrap(
        GuardianLocalizacaoActiveTripSheet(
          passageiro: passageiro,
          trip: morning,
          dayTrips: [morning, afternoon],
          selectedTripId: morning.id,
          agendaKey: const Key('guardian.localizacao.agendaViagens'),
          tripButtonKeyBuilder:
              (tripId) => Key('guardian.localizacao.agendaViagem.$tripId'),
          onTripSelected: (trip) {
            selectedTrip = trip;
          },
          passageiros: [passageiro, bruno],
          selectedPassageiroId: passageiro.id,
          onPassageiroSelected: (item) {
            selectedPassageiro = item;
          },
          onOpenMap: () {
            openedMap = true;
          },
        ),
      ),
    );

    expect(find.text('Linha Manha'), findsOneWidget);
    expect(find.text('Ida'), findsOneWidget);
    expect(find.text('Ana'), findsAtLeastNWidgets(1));
    expect(find.text('Passageiro selecionado'), findsOneWidget);
    expect(find.text('Motorista Manha'), findsNothing);
    expect(find.text('8 min'), findsNothing);
    expect(find.text('Van a 2 paradas'), findsOneWidget);
    expect(find.text('Posição recente da van'), findsOneWidget);
    expect(find.text('Último estado útil às 07:02'), findsOneWidget);
    expect(
      find.byKey(const Key('guardian.localizacao.agendaViagens')),
      findsOneWidget,
    );

    await tester.tap(find.text('Mapa completo'));
    await tester.pump();

    expect(openedMap, isTrue);

    await tester.tap(
      find.byKey(const Key('guardian.localizacao.agendaViagem.31')),
    );
    await tester.pump();

    expect(selectedTrip?.id, 31);

    await tester.tap(find.widgetWithText(ChoiceChip, 'Bruno'));
    await tester.pump();

    expect(selectedPassageiro?.id, 11);
  });
}

Widget _wrap(Widget child) {
  return MaterialApp(
    home: Scaffold(
      body: SingleChildScrollView(child: SizedBox(width: 900, child: child)),
    ),
  );
}

TripTrackingRecord _trip({
  required int id,
  required String servicoNome,
  required String tipoOperacao,
  required String horarioPartida,
  required TripTrackingStatus status,
  int? stopsBefore,
  String? motoristaNome,
  TripTrackingStateRecord trackingState = const TripTrackingStateRecord(
    routeSnapshotStatus: TripRouteSnapshotStatus.materialized,
    locationFreshness: TripLocationFreshness.delayed,
    etaConfidence: TripEtaConfidence.medium,
    stopsRemaining: 3,
    stopsCompleted: 1,
    stopsBeforePassenger: 2,
    routeRecomputeReason: TripRouteRecomputeReason.none,
  ),
}) {
  return TripTrackingRecord(
    id: id,
    rotaNome: 'Rota Escolar',
    servicoId: id + 100,
    servicoNome: servicoNome,
    tipoOperacao: tipoOperacao,
    dataOperacao: DateTime(2026, 6, 13),
    status: status,
    rotaVersao: 1,
    etaMinutosAtePassageiro: 8,
    etaAtualizadoEm: DateTime(2026, 6, 13, 7),
    motorista: TripTrackingDriverRecord(
      id: id + 1,
      nome: motoristaNome ?? 'Motorista $id',
    ),
    paradaDoPassageiro: TripTrackingPassengerStopRecord(
      id: id + 300,
      ordem: 1,
      status: TripTrackingStopStatus.pending,
      etaMinutos: 10,
      confirmadoPresenca: true,
      proximaParada: true,
      passageiroId: 10,
      passageiroNome: 'Ana',
      menorDeIdade: true,
    ),
    rotaGeografica:
        stopsBefore == null
            ? null
            : TripRouteGeometryRecord(
              provider: 'manual',
              polyline: '',
              navegacao: null,
              waypoints: const [],
              legs: const [],
              filaTotalParadas: 4,
              posicaoPassageiroNaFila: stopsBefore + 1,
              paradasAntesDoPassageiro: stopsBefore,
            ),
    horarioPartida: horarioPartida,
    trackingState: trackingState,
  );
}

GuardianLinkedPassageiroRecord _passageiro({
  required int id,
  required String nome,
}) {
  return GuardianLinkedPassageiroRecord(
    id: id,
    nome: nome,
    email: '${nome.toLowerCase()}@vanrides.dev',
    telefone: '51999990000',
    endereco: 'Rua A',
    bairro: 'Centro',
    cidade: 'Porto Alegre',
    estado: 'RS',
    cep: '90000-000',
    menorDeIdade: true,
  );
}
