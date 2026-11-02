import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_route_geometry_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_state_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/trip_live_map_panel.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/widgets/passenger_home_trip_widgets.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/widgets/passenger_map_footer_sheet_scaffold.dart';

TripTrackingRecord _trip({
  required int id,
  required String servicoNome,
  required String tipoOperacao,
  required String horarioPartida,
  TripTrackingStatus status = TripTrackingStatus.planned,
  int? stopsBefore,
  TripTrackingStateRecord trackingState = TripTrackingStateRecord.unavailable,
}) {
  return TripTrackingRecord(
    id: id,
    rotaNome: 'Rota Escolar',
    servicoId: id + 100,
    servicoNome: servicoNome,
    tipoOperacao: tipoOperacao,
    dataOperacao: DateTime(2026, 3, 20),
    status: status,
    rotaVersao: 1,
    etaMinutosAtePassageiro: 12,
    etaAtualizadoEm: DateTime(2026, 3, 20, 7, 30),
    motorista: const TripTrackingDriverRecord(id: 7, nome: 'Motorista Portal'),
    rotaGeografica:
        stopsBefore == null
            ? null
            : TripRouteGeometryRecord(
              provider: 'GOOGLE_DIRECTIONS_V1',
              polyline: '',
              navegacao: null,
              waypoints: const [],
              legs: const [],
              filaTotalParadas: stopsBefore + 1,
              posicaoPassageiroNaFila: stopsBefore + 1,
              paradasAntesDoPassageiro: stopsBefore,
            ),
    paradaDoPassageiro: TripTrackingPassengerStopRecord(
      id: id + 300,
      ordem: 1,
      status: TripTrackingStopStatus.pending,
      etaMinutos: 12,
      confirmadoPresenca: true,
      proximaParada: true,
      passageiroId: 21,
      passageiroNome: 'Ana Escola',
      menorDeIdade: true,
    ),
    horarioPartida: horarioPartida,
    trackingState: trackingState,
  );
}

void main() {
  testWidgets('sheet premium do passageiro resume paradas e sinal', (
    tester,
  ) async {
    final trip = _trip(
      id: 30,
      servicoNome: 'Linha Manha',
      tipoOperacao: 'IDA',
      horarioPartida: '07:30',
      status: TripTrackingStatus.inProgress,
      stopsBefore: 2,
      trackingState: const TripTrackingStateRecord(
        routeSnapshotStatus: TripRouteSnapshotStatus.materialized,
        locationFreshness: TripLocationFreshness.live,
        etaConfidence: TripEtaConfidence.high,
        stopsRemaining: 3,
        stopsCompleted: 1,
        stopsBeforePassenger: 2,
        routeRecomputeReason: TripRouteRecomputeReason.none,
      ),
    );

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: SizedBox(
            width: 390,
            height: 800,
            child: PassengerHomeMapScene(
              trip: trip,
              dayTrips: [trip],
              selectedTripId: trip.id,
              effectiveMotoristaId: 7,
              email: 'passageiro@vanrides.dev',
              senha: 'senha',
              accessToken: 'token',
              liveLocationGateway: null,
              liveLocationWebSocketService: null,
              mapRenderMode: TripLiveMapRenderMode.placeholder,
              onRefresh: () {},
              onTripSelected: (_) {},
              onOpenTripDetails: () {},
              onOpenMap: () {},
              onOpenPresence: () {},
            ),
          ),
        ),
      ),
    );

    await tester.pumpAndSettle();

    expect(find.byType(DraggableScrollableSheet), findsOneWidget);
    expect(find.byKey(const Key('passenger.map.footerSheet')), findsOneWidget);
    expect(
      find.byKey(const Key('passenger.map.footerSheet.footer')),
      findsOneWidget,
    );
    final sheetSafeArea = tester.widget<SafeArea>(
      find.byKey(PassengerMapFooterSheetScaffold.sheetSafeAreaKey),
    );
    expect(sheetSafeArea.minimum.bottom, 0);
    expect(
      find.byKey(const Key('passenger.trip.sheet.resting')),
      findsOneWidget,
    );
    expect(
      find.byKey(const Key('passenger.trip.sheet.summary')),
      findsOneWidget,
    );
    expect(
      find.byKey(const Key('passenger.trip.sheet.expanded')),
      findsNothing,
    );
    expect(find.text('Viagem'), findsOneWidget);
    expect(find.text('Presença'), findsOneWidget);
    expect(find.text('Conta'), findsOneWidget);
    expect(find.text('Van a caminho'), findsOneWidget);
    expect(find.text('Hoje em acompanhamento'), findsOneWidget);
    expect(find.text('1 viagem no dia • Van a caminho'), findsOneWidget);
    expect(find.text('Faltam 2 paradas'), findsOneWidget);
    expect(
      find.text('12 min até sua parada • Rota validada • ETA confiável'),
      findsOneWidget,
    );
    expect(find.text('Ao vivo'), findsOneWidget);
    expect(find.text('Van'), findsNothing);
    expect(find.text('Detalhes'), findsNothing);
    expect(find.text('Minha parada'), findsOneWidget);
    expect(find.text('Mais'), findsNothing);
    expect(find.text('Agenda de hoje'), findsNothing);
    expect(find.text('Rota'), findsNothing);
    expect(find.text('Mapa Completo'), findsNothing);

    final draggable = tester.widget<DraggableScrollableSheet>(
      find.byType(DraggableScrollableSheet),
    );
    expect(draggable.snap, isTrue);
    expect(draggable.snapSizes, const [0.18, 0.34, 0.86]);
    expect(draggable.snapAnimationDuration, const Duration(milliseconds: 320));
    expect(draggable.initialChildSize, closeTo(0.34, 0.001));

    final mapPanel = tester.widget<TripLiveMapPanel>(
      find.byType(TripLiveMapPanel),
    );
    expect(mapPanel.showControls, isFalse);
    expect(mapPanel.mapPadding.bottom, greaterThanOrEqualTo(260));
  });

  testWidgets('topo do mapa destaca viagens e dispara selecao', (tester) async {
    final trips = [
      _trip(
        id: 30,
        servicoNome: 'Linha Manha',
        tipoOperacao: 'IDA',
        horarioPartida: '07:30',
        status: TripTrackingStatus.inProgress,
      ),
      _trip(
        id: 31,
        servicoNome: 'Linha Tarde',
        tipoOperacao: 'VOLTA',
        horarioPartida: '17:45',
      ),
    ];
    TripTrackingRecord? selectedTrip;

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: SizedBox(
            width: 390,
            height: 800,
            child: PassengerHomeMapScene(
              trip: trips.first,
              dayTrips: trips,
              selectedTripId: 30,
              effectiveMotoristaId: 7,
              email: 'passageiro@vanrides.dev',
              senha: 'senha',
              accessToken: 'token',
              liveLocationGateway: null,
              liveLocationWebSocketService: null,
              mapRenderMode: TripLiveMapRenderMode.placeholder,
              pendingPresenceCount: 1,
              onRefresh: () {},
              onTripSelected: (trip) => selectedTrip = trip,
              onOpenTripDetails: () {},
              onOpenMap: () {},
              onOpenPresence: () {},
              agendaKey: const Key('agenda-test'),
              tripKeyBuilder: (tripId) => Key('trip-$tripId'),
            ),
          ),
        ),
      ),
    );

    expect(find.byKey(const Key('agenda-test')), findsOneWidget);
    expect(find.text('2 viagens hoje'), findsNothing);
    expect(find.text('07:30 • Ida'), findsOneWidget);
    expect(find.text('17:45 • Volta'), findsOneWidget);
    expect(find.text('07:30 • Ida • Linha Manha'), findsNothing);
    expect(find.text('17:45 • Volta • Linha Tarde'), findsNothing);
    expect(find.text('Presenca pendente'), findsNothing);
    expect(find.text('1 presença pendente'), findsNothing);
    expect(find.text('Van'), findsNothing);
    expect(find.text('Voce'), findsNothing);

    await tester.tap(find.byKey(const Key('trip-31')));
    await tester.pump();

    expect(selectedTrip?.id, 31);
  });

  testWidgets('hud planejado usa copy curta para nao truncar estado critico', (
    tester,
  ) async {
    final trip = _trip(
      id: 32,
      servicoNome: 'Linha Manha',
      tipoOperacao: 'IDA',
      horarioPartida: '07:30',
      status: TripTrackingStatus.planned,
      stopsBefore: 0,
      trackingState: const TripTrackingStateRecord(
        routeSnapshotStatus: TripRouteSnapshotStatus.materialized,
        locationFreshness: TripLocationFreshness.paused,
        etaConfidence: TripEtaConfidence.low,
        stopsRemaining: 1,
        stopsCompleted: 0,
        stopsBeforePassenger: 0,
        routeRecomputeReason: TripRouteRecomputeReason.none,
      ),
    );

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: SizedBox(
            width: 390,
            height: 800,
            child: PassengerHomeMapScene(
              trip: trip,
              dayTrips: [trip],
              selectedTripId: trip.id,
              effectiveMotoristaId: 7,
              email: 'passageiro@vanrides.dev',
              senha: 'senha',
              accessToken: 'token',
              liveLocationGateway: null,
              liveLocationWebSocketService: null,
              mapRenderMode: TripLiveMapRenderMode.placeholder,
              onRefresh: () {},
              onTripSelected: (_) {},
              onOpenTripDetails: () {},
              onOpenMap: () {},
              onOpenPresence: () {},
            ),
          ),
        ),
      ),
    );

    await tester.pumpAndSettle();

    expect(find.text('Van ainda não iniciou'), findsOneWidget);
    expect(find.text('Aguardando saída'), findsOneWidget);
    expect(find.text('Aguardando inicio'), findsNothing);
    expect(find.text('Aguardando inicio da viagem'), findsNothing);
    expect(find.text('Última posição'), findsNothing);
    expect(find.text('Pausado'), findsOneWidget);
  });

  testWidgets('footer sheet starts resting and responds to detents', (
    tester,
  ) async {
    final trip = _trip(
      id: 33,
      servicoNome: 'Bom pastor - noturno',
      tipoOperacao: 'IDA',
      horarioPartida: '18:00',
      status: TripTrackingStatus.planned,
      stopsBefore: 0,
      trackingState: const TripTrackingStateRecord(
        routeSnapshotStatus: TripRouteSnapshotStatus.materialized,
        locationFreshness: TripLocationFreshness.paused,
        etaConfidence: TripEtaConfidence.low,
        stopsRemaining: 1,
        stopsCompleted: 0,
        stopsBeforePassenger: 0,
        routeRecomputeReason: TripRouteRecomputeReason.none,
      ),
    );

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: SizedBox(
            width: 390,
            height: 800,
            child: PassengerHomeMapScene(
              trip: trip,
              dayTrips: [trip],
              selectedTripId: trip.id,
              effectiveMotoristaId: 7,
              email: 'passageiro@vanrides.dev',
              senha: 'senha',
              accessToken: 'token',
              liveLocationGateway: null,
              liveLocationWebSocketService: null,
              mapRenderMode: TripLiveMapRenderMode.placeholder,
              pendingPresenceCount: 2,
              onRefresh: () {},
              onTripSelected: (_) {},
              onOpenTripDetails: () {},
              onOpenMap: () {},
              onOpenPresence: () {},
            ),
          ),
        ),
      ),
    );

    await tester.pumpAndSettle();

    expect(
      find.byKey(const Key('passenger.trip.sheet.resting')),
      findsOneWidget,
    );
    expect(
      find.byKey(const Key('passenger.trip.sheet.summary')),
      findsOneWidget,
    );
    expect(find.text('2 presenças pendentes'), findsNothing);
    expect(find.byType(DraggableScrollableSheet), findsOneWidget);
    expect(find.text('Minha parada'), findsOneWidget);
    expect(find.text('Detalhes'), findsNothing);

    await tester.drag(
      find.byKey(const Key('passenger.trip.sheet.handle')),
      const Offset(0, -240),
    );
    await tester.pumpAndSettle();

    expect(
      find.byKey(const Key('passenger.trip.sheet.expanded')),
      findsOneWidget,
    );
    expect(
      find.byKey(const Key('passenger.trip.sheet.summary')),
      findsOneWidget,
    );
    expect(find.text('2 presenças pendentes'), findsOneWidget);
    expect(find.text('Detalhes'), findsOneWidget);

    await tester.drag(
      find.byKey(const Key('passenger.trip.sheet.handle')),
      const Offset(0, 240),
    );
    await tester.pumpAndSettle();

    expect(
      find.byKey(const Key('passenger.trip.sheet.resting')),
      findsOneWidget,
    );
    expect(
      find.byKey(const Key('passenger.trip.sheet.expanded')),
      findsNothing,
    );

    final draggable = tester.widget<DraggableScrollableSheet>(
      find.byType(DraggableScrollableSheet),
    );
    expect(draggable.minChildSize, closeTo(0.18, 0.001));
    expect(draggable.snapSizes, const [0.18, 0.34, 0.86]);
    expect(draggable.snapAnimationDuration, const Duration(milliseconds: 320));

    await tester.tap(find.byKey(const Key('passenger.trip.sheet.chevron')));
    await tester.pumpAndSettle();

    expect(
      find.byKey(const Key('passenger.trip.sheet.expanded')),
      findsOneWidget,
    );
    expect(find.text('Detalhes'), findsOneWidget);
  });

  testWidgets('map focus actions minimize expanded sheet before focusing map', (
    tester,
  ) async {
    final trip = _trip(
      id: 34,
      servicoNome: 'Linha Manha',
      tipoOperacao: 'IDA',
      horarioPartida: '07:30',
      status: TripTrackingStatus.inProgress,
      stopsBefore: 2,
      trackingState: const TripTrackingStateRecord(
        routeSnapshotStatus: TripRouteSnapshotStatus.materialized,
        locationFreshness: TripLocationFreshness.live,
        etaConfidence: TripEtaConfidence.high,
        stopsRemaining: 3,
        stopsCompleted: 1,
        stopsBeforePassenger: 2,
        routeRecomputeReason: TripRouteRecomputeReason.none,
      ),
    );

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: SizedBox(
            width: 390,
            height: 800,
            child: PassengerHomeMapScene(
              trip: trip,
              dayTrips: [trip],
              selectedTripId: trip.id,
              effectiveMotoristaId: 7,
              email: 'passageiro@vanrides.dev',
              senha: 'senha',
              accessToken: 'token',
              liveLocationGateway: null,
              liveLocationWebSocketService: null,
              mapRenderMode: TripLiveMapRenderMode.placeholder,
              pendingPresenceCount: 0,
              onRefresh: () {},
              onTripSelected: (_) {},
              onOpenTripDetails: () {},
              onOpenMap: () {},
              onOpenPresence: () {},
            ),
          ),
        ),
      ),
    );

    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const Key('passenger.trip.sheet.chevron')));
    await tester.pumpAndSettle();

    expect(
      find.byKey(const Key('passenger.trip.sheet.expanded')),
      findsOneWidget,
    );

    await tester.tap(find.byKey(const Key('passenger.trip.action.focusVan')));
    await tester.pumpAndSettle();

    expect(
      find.byKey(const Key('passenger.trip.sheet.expanded')),
      findsNothing,
    );
    expect(
      find.byKey(const Key('passenger.map.footerSheet.footer')),
      findsOneWidget,
    );
    expect(find.text('Detalhes'), findsNothing);

    await tester.tap(find.byKey(const Key('passenger.trip.sheet.chevron')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('passenger.trip.sheet.chevron')));
    await tester.pumpAndSettle();

    expect(
      find.byKey(const Key('passenger.trip.sheet.expanded')),
      findsOneWidget,
    );

    await tester.tap(
      find.byKey(const Key('passenger.trip.action.focusPassenger')),
    );
    await tester.pumpAndSettle();

    expect(
      find.byKey(const Key('passenger.trip.sheet.expanded')),
      findsNothing,
    );
    expect(
      find.byKey(const Key('passenger.map.footerSheet.footer')),
      findsOneWidget,
    );
    expect(find.text('Detalhes'), findsNothing);
  });
}
