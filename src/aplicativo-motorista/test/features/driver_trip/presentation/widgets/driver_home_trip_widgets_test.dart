import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/domain/driver_trip_record.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/presentation/widgets/driver_home_trip_widgets.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_route_geometry_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_state_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/trip_live_map_panel.dart';

void main() {
  setUpAll(() async {
    await initializeDateFormatting('pt_BR');
  });

  testWidgets('DriverHomeTripTab renders unavailable state and refreshes', (
    tester,
  ) async {
    var refreshTaps = 0;

    await _pumpTripTab(
      tester,
      DriverHomeTripTab(
        isLoading: true,
        errorMessage: null,
        trip: null,
        motoristaId: 7,
        email: 'motorista@vanrides.dev',
        senha: 'Secret123',
        mapRenderMode: TripLiveMapRenderMode.placeholder,
        sharingLocation: false,
        editingRoute: false,
        dayTrips: const [],
        selectedTripId: null,
        editableStops: const [],
        savingRoute: false,
        openingNavigation: false,
        runningLifecycleAction: false,
        refreshKey: const Key('driver.refresh'),
        agendaKey: const Key('driver.agenda'),
        tripKeyBuilder: (id) => Key('driver.trip.$id'),
        startTripKey: const Key('driver.start'),
        detailsKey: const Key('driver.details'),
        mapKey: const Key('driver.map'),
        editRouteKey: const Key('driver.edit'),
        saveRouteKey: const Key('driver.saveRoute'),
        cancelRouteKey: const Key('driver.cancelRoute'),
        completeStopKey: const Key('driver.complete'),
        navigationKey: const Key('driver.navigation'),
        skipStopKey: const Key('driver.skip'),
        noShowKey: const Key('driver.noShow'),
        endTripKey: const Key('driver.end'),
        onRefresh: () async {
          refreshTaps++;
        },
        onSelectTrip: (_) {},
        onReorderStops: (_, _) {},
        onSaveRoute: () {},
        onCancelRoute: () {},
        onStartTrip: () {},
        onOpenStops: () {},
        onOpenMap: () {},
        onEditRoute: () {},
        onCompleteStop: () {},
        onOpenNavigation: () {},
        onSkipStop: () {},
        onNoShowStop: () {},
        onEndTrip: () {},
      ),
    );

    expect(find.byType(CircularProgressIndicator), findsOneWidget);

    await tester.tap(find.byKey(const Key('driver.refresh')));
    await tester.pump();

    expect(refreshTaps, 1);
  });

  testWidgets(
    'DriverHomeTripTab renders planning mode and dispatches actions',
    (tester) async {
      var refreshTaps = 0;
      var startTaps = 0;
      var listTaps = 0;
      var mapTaps = 0;
      var editTaps = 0;
      int? selectedTripId;

      await _pumpTripTab(
        tester,
        DriverHomeTripTab(
          isLoading: false,
          errorMessage: null,
          trip: _plannedTrip,
          motoristaId: 7,
          email: 'motorista@vanrides.dev',
          senha: 'Secret123',
          mapRenderMode: TripLiveMapRenderMode.placeholder,
          sharingLocation: false,
          editingRoute: false,
          dayTrips: [_plannedTrip, _secondTrip],
          selectedTripId: _plannedTrip.id,
          editableStops: const [_stop],
          savingRoute: false,
          openingNavigation: false,
          runningLifecycleAction: false,
          refreshKey: const Key('driver.refresh'),
          agendaKey: const Key('driver.agenda'),
          tripKeyBuilder: (id) => Key('driver.trip.$id'),
          startTripKey: const Key('driver.start'),
          detailsKey: const Key('driver.details'),
          mapKey: const Key('driver.map'),
          editRouteKey: const Key('driver.edit'),
          saveRouteKey: const Key('driver.saveRoute'),
          cancelRouteKey: const Key('driver.cancelRoute'),
          completeStopKey: const Key('driver.complete'),
          navigationKey: const Key('driver.navigation'),
          skipStopKey: const Key('driver.skip'),
          noShowKey: const Key('driver.noShow'),
          endTripKey: const Key('driver.end'),
          onRefresh: () async {
            refreshTaps++;
          },
          onSelectTrip: (trip) => selectedTripId = trip.id,
          onReorderStops: (_, _) {},
          onSaveRoute: () {},
          onCancelRoute: () {},
          onStartTrip: () => startTaps++,
          onOpenStops: () => listTaps++,
          onOpenMap: () => mapTaps++,
          onEditRoute: () => editTaps++,
          onCompleteStop: () {},
          onOpenNavigation: () {},
          onSkipStop: () {},
          onNoShowStop: () {},
          onEndTrip: () {},
        ),
      );

      expect(find.text('Linha Manha'), findsWidgets);
      expect(find.text('13/03/2026'), findsOneWidget);
      expect(find.text('Agenda de hoje'), findsOneWidget);
      expect(find.text('07:30 • Ida • Linha Manha'), findsOneWidget);
      expect(
        find.text('1 paradas planejadas • Primeira coleta em 9 min'),
        findsOneWidget,
      );

      await _tapVisible(tester, const Key('driver.trip.92'));
      await tester.tap(find.byKey(const Key('driver.refresh')));
      await _tapVisible(tester, const Key('driver.start'));
      await _tapVisible(tester, const Key('driver.details'));
      await _tapVisible(tester, const Key('driver.map'));
      await _tapVisible(tester, const Key('driver.edit'));
      await tester.pumpAndSettle();

      expect(selectedTripId, 92);
      expect(refreshTaps, 1);
      expect(startTaps, 1);
      expect(listTaps, 1);
      expect(mapTaps, 1);
      expect(editTaps, 1);
    },
  );

  testWidgets(
    'Driver home planning widgets render agenda and dispatch actions',
    (tester) async {
      var refreshTaps = 0;
      var startTaps = 0;
      var listTaps = 0;
      var mapTaps = 0;
      var editTaps = 0;
      int? selectedTripId;

      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: SingleChildScrollView(
              child: Column(
                children: [
                  DriverHomePlanningTopBar(
                    trip: _plannedTrip,
                    dateLabel: '13/03/2026',
                    refreshKey: const Key('driver.refresh'),
                    onRefresh: () => refreshTaps++,
                  ),
                  DriverHomePlanningSheet(
                    trip: _plannedTrip,
                    nextStop: _plannedTrip.proximaParada,
                    dayTrips: [_plannedTrip, _secondTrip],
                    selectedTripId: _plannedTrip.id,
                    agendaKey: const Key('driver.agenda'),
                    tripKeyBuilder: (id) => Key('driver.trip.$id'),
                    startTripKey: const Key('driver.start'),
                    detailsKey: const Key('driver.details'),
                    mapKey: const Key('driver.map'),
                    editRouteKey: const Key('driver.edit'),
                    runningLifecycleAction: false,
                    onSelectTrip: (trip) => selectedTripId = trip.id,
                    onStartTrip: () => startTaps++,
                    onOpenStops: () => listTaps++,
                    onOpenMap: () => mapTaps++,
                    onEditRoute: () => editTaps++,
                  ),
                ],
              ),
            ),
          ),
        ),
      );

      expect(find.text('Linha Manha'), findsWidgets);
      expect(find.text('Ida'), findsWidgets);
      expect(find.text('13/03/2026'), findsOneWidget);
      expect(find.text('Agenda de hoje'), findsOneWidget);
      expect(find.text('2 viagens hoje'), findsOneWidget);
      expect(find.text('Hoje pronto para sair'), findsOneWidget);
      expect(
        find.text('2 viagens no dia • primeira coleta em 9 min'),
        findsOneWidget,
      );
      expect(find.text('07:30 • Ida • Linha Manha'), findsOneWidget);
      expect(find.text('09:00 • Volta • Linha Tarde'), findsOneWidget);
      expect(find.text('Proxima acao'), findsOneWidget);
      expect(find.text('Revisar rota'), findsOneWidget);
      expect(
        find.text('1 paradas planejadas • Primeira coleta em 9 min'),
        findsOneWidget,
      );

      await tester.tap(find.byKey(const Key('driver.trip.92')));
      await tester.tap(find.byKey(const Key('driver.refresh')));
      await tester.tap(find.byKey(const Key('driver.start')));
      await tester.tap(find.byKey(const Key('driver.details')));
      await tester.tap(find.byKey(const Key('driver.map')));
      await tester.tap(find.byKey(const Key('driver.edit')));
      await tester.pumpAndSettle();

      expect(selectedTripId, 92);
      expect(refreshTaps, 1);
      expect(startTaps, 1);
      expect(listTaps, 1);
      expect(mapTaps, 1);
      expect(editTaps, 1);
    },
  );

  testWidgets(
    'Driver home active sheet renders next stop and dispatches actions',
    (tester) async {
      var completeTaps = 0;
      var navigationTaps = 0;
      var skipTaps = 0;
      var noShowTaps = 0;
      var endTaps = 0;

      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: SingleChildScrollView(
              child: DriverHomeActiveSheet(
                trip: _activeTripWithNavigation,
                nextStop: _activeTripWithNavigation.proximaParada,
                dayTrips: const [],
                selectedTripId: _activeTripWithNavigation.id,
                agendaKey: const Key('driver.agenda'),
                tripKeyBuilder: (id) => Key('driver.trip.$id'),
                completeStopKey: const Key('driver.complete'),
                navigationKey: const Key('driver.navigation'),
                skipStopKey: const Key('driver.skip'),
                noShowKey: const Key('driver.noShow'),
                endTripKey: const Key('driver.end'),
                sharingLocation: false,
                openingNavigation: false,
                runningLifecycleAction: false,
                onSelectTrip: (_) {},
                onCompleteStop: () => completeTaps++,
                onOpenNavigation: () => navigationTaps++,
                onSkipStop: () => skipTaps++,
                onNoShowStop: () => noShowTaps++,
                onEndTrip: () => endTaps++,
              ),
            ),
          ),
        ),
      );

      expect(find.text('Proximo passageiro'), findsOneWidget);
      expect(find.text('Aluno A'), findsOneWidget);
      expect(find.text('Parada 1 • Linha Manha'), findsOneWidget);
      expect(find.text('9 min'), findsOneWidget);
      expect(find.text('Transmitindo ao vivo'), findsOneWidget);
      expect(find.text('Hoje em execução'), findsOneWidget);
      expect(find.text('1 viagem no dia • próxima: Aluno A'), findsOneWidget);
      expect(
        find.text('Continue com o app aberto durante a corrida.'),
        findsOneWidget,
      );
      expect(find.text('Fallback recomendado'), findsOneWidget);
      expect(find.text('Proxima acao'), findsOneWidget);
      expect(find.text('Agora: ir para Aluno A'), findsOneWidget);
      expect(find.text('Parada 1 • 9 min • Maps pronto'), findsOneWidget);
      expect(find.text('Rota validada'), findsOneWidget);
      expect(find.text('Ir para proxima parada'), findsOneWidget);
      expect(find.text('Confirmar embarque'), findsWidgets);
      expect(find.text('Pular'), findsWidgets);
      expect(find.text('Ative a localizacao para transmitir.'), findsOneWidget);

      await _tapVisible(tester, const Key('driver.navigation'));
      await _tapVisible(tester, const Key('driver.complete'));
      await _tapVisible(tester, const Key('driver.skip'));
      await _tapVisible(tester, const Key('driver.noShow'));
      await _tapVisible(tester, const Key('driver.end'));
      await tester.pumpAndSettle();

      expect(navigationTaps, 1);
      expect(completeTaps, 1);
      expect(skipTaps, 1);
      expect(noShowTaps, 1);
      expect(endTaps, 1);
    },
  );

  testWidgets('Driver home active sheet explains stale signal safely', (
    tester,
  ) async {
    final staleTrip = _driverTripWithFreshness(
      TripLocationFreshness.stale,
      driverLocationUpdatedAt: DateTime(2026, 7, 5, 7, 6),
    );

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: SingleChildScrollView(
            child: DriverHomeActiveSheet(
              trip: staleTrip,
              nextStop: staleTrip.proximaParada,
              dayTrips: const [],
              selectedTripId: staleTrip.id,
              agendaKey: const Key('driver.agenda'),
              tripKeyBuilder: (id) => Key('driver.trip.$id'),
              completeStopKey: const Key('driver.complete'),
              navigationKey: const Key('driver.navigation'),
              skipStopKey: const Key('driver.skip'),
              noShowKey: const Key('driver.noShow'),
              endTripKey: const Key('driver.end'),
              sharingLocation: true,
              openingNavigation: false,
              runningLifecycleAction: false,
              onSelectTrip: (_) {},
              onCompleteStop: () {},
              onOpenNavigation: () {},
              onSkipStop: () {},
              onNoShowStop: () {},
              onEndTrip: () {},
            ),
          ),
        ),
      ),
    );

    expect(find.text('Sinal antigo'), findsOneWidget);
    expect(find.text('Último estado útil às 07:06'), findsOneWidget);
    expect(
      find.text(
        'Passageiros veem o último estado da viagem; verifique conexão e localização quando estiver seguro.',
      ),
      findsOneWidget,
    );
    expect(find.text('Ir para proxima parada'), findsOneWidget);
  });

  testWidgets('Driver home active sheet renders terminal trip state', (
    tester,
  ) async {
    var endTaps = 0;

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: DriverHomeActiveSheet(
            trip: _terminalTrip,
            nextStop: null,
            dayTrips: const [],
            selectedTripId: _terminalTrip.id,
            agendaKey: const Key('driver.agenda'),
            tripKeyBuilder: (id) => Key('driver.trip.$id'),
            completeStopKey: const Key('driver.complete'),
            navigationKey: const Key('driver.navigation'),
            skipStopKey: const Key('driver.skip'),
            noShowKey: const Key('driver.noShow'),
            endTripKey: const Key('driver.end'),
            sharingLocation: true,
            openingNavigation: false,
            runningLifecycleAction: false,
            onSelectTrip: (_) {},
            onCompleteStop: () {},
            onOpenNavigation: () {},
            onSkipStop: () {},
            onNoShowStop: () {},
            onEndTrip: () => endTaps++,
          ),
        ),
      ),
    );

    expect(find.text('Corrida concluida'), findsOneWidget);
    expect(
      find.text('Nao existem mais paradas pendentes para esta viagem.'),
      findsOneWidget,
    );

    await tester.tap(find.byKey(const Key('driver.end')));
    await tester.pumpAndSettle();

    expect(endTaps, 1);
  });
}

Future<void> _tapVisible(WidgetTester tester, Key key) async {
  final target = find.byKey(key);
  await tester.ensureVisible(target);
  await tester.pumpAndSettle();
  await tester.tap(target);
  await tester.pump();
}

Future<void> _pumpTripTab(WidgetTester tester, Widget child) async {
  await tester.binding.setSurfaceSize(const Size(390, 844));
  addTearDown(() => tester.binding.setSurfaceSize(null));

  await tester.pumpWidget(
    MaterialApp(
      home: Scaffold(body: SizedBox(width: 390, height: 844, child: child)),
    ),
  );
}

const _stop = DriverTripStopRecord(
  id: 11,
  ordem: 1,
  status: TripTrackingStopStatus.pending,
  etaMinutos: 9,
  confirmadoPresenca: true,
  proximaParada: true,
  passageiroId: 42,
  passageiroNome: 'Aluno A',
  menorDeIdade: false,
);

final _plannedTrip = DriverTripRecord(
  id: 91,
  rotaBaseId: 4,
  rotaNome: 'Rota Centro',
  servicoNome: 'Linha Manha',
  tipoOperacao: 'IDA',
  dataOperacao: _operationDate,
  status: TripTrackingStatus.planned,
  rotaVersao: 2,
  etaMinutosAteProximaParada: 9,
  etaAtualizadoEm: null,
  motorista: null,
  proximaParada: _stop,
  paradas: [_stop],
  rotaGeografica: null,
  trackingState: const TripTrackingStateRecord(
    routeSnapshotStatus: TripRouteSnapshotStatus.materialized,
    locationFreshness: TripLocationFreshness.live,
    etaConfidence: TripEtaConfidence.high,
    stopsRemaining: 1,
    stopsCompleted: 0,
    routeRecomputeReason: TripRouteRecomputeReason.none,
  ),
  horarioPartida: '07:30',
);

final _secondTrip = DriverTripRecord(
  id: 92,
  rotaBaseId: 5,
  rotaNome: 'Rota Tarde',
  servicoNome: 'Linha Tarde',
  tipoOperacao: 'VOLTA',
  dataOperacao: _operationDate,
  status: TripTrackingStatus.planned,
  rotaVersao: 1,
  etaMinutosAteProximaParada: 12,
  etaAtualizadoEm: null,
  motorista: null,
  proximaParada: _stop,
  paradas: [_stop],
  rotaGeografica: null,
  horarioPartida: '09:00',
);

final _activeTripWithNavigation = DriverTripRecord(
  id: 95,
  rotaBaseId: 4,
  rotaNome: 'Rota Centro',
  servicoNome: 'Linha Manha',
  tipoOperacao: 'IDA',
  dataOperacao: _operationDate,
  status: TripTrackingStatus.inProgress,
  rotaVersao: 2,
  etaMinutosAteProximaParada: 9,
  etaAtualizadoEm: null,
  motorista: null,
  proximaParada: _stop,
  paradas: [_stop],
  rotaGeografica: const TripRouteGeometryRecord(
    provider: 'GOOGLE_DIRECTIONS_V1',
    polyline: '',
    navegacao: TripNavigationRecord(
      provider: 'GOOGLE_MAPS_HANDOFF',
      url: 'https://maps.google.com/?q=proxima',
      origem: 'Motorista',
      destino: 'Aluno A',
      quantidadeWaypoints: 1,
    ),
    waypoints: [],
    legs: [],
    filaTotalParadas: 1,
    posicaoPassageiroNaFila: null,
    paradasAntesDoPassageiro: null,
  ),
  trackingState: const TripTrackingStateRecord(
    routeSnapshotStatus: TripRouteSnapshotStatus.materialized,
    locationFreshness: TripLocationFreshness.live,
    etaConfidence: TripEtaConfidence.high,
    stopsRemaining: 1,
    stopsCompleted: 0,
    routeRecomputeReason: TripRouteRecomputeReason.none,
  ),
  horarioPartida: '07:30',
);

DriverTripRecord _driverTripWithFreshness(
  TripLocationFreshness freshness, {
  DateTime? driverLocationUpdatedAt,
}) {
  return DriverTripRecord(
    id: _activeTripWithNavigation.id,
    rotaBaseId: _activeTripWithNavigation.rotaBaseId,
    rotaNome: _activeTripWithNavigation.rotaNome,
    servicoNome: _activeTripWithNavigation.servicoNome,
    tipoOperacao: _activeTripWithNavigation.tipoOperacao,
    dataOperacao: _activeTripWithNavigation.dataOperacao,
    status: _activeTripWithNavigation.status,
    rotaVersao: _activeTripWithNavigation.rotaVersao,
    etaMinutosAteProximaParada:
        _activeTripWithNavigation.etaMinutosAteProximaParada,
    etaAtualizadoEm: _activeTripWithNavigation.etaAtualizadoEm,
    motorista: _activeTripWithNavigation.motorista,
    proximaParada: _activeTripWithNavigation.proximaParada,
    paradas: _activeTripWithNavigation.paradas,
    rotaGeografica: _activeTripWithNavigation.rotaGeografica,
    trackingState: TripTrackingStateRecord(
      routeSnapshotStatus:
          _activeTripWithNavigation.trackingState.routeSnapshotStatus,
      locationFreshness: freshness,
      etaConfidence: _activeTripWithNavigation.trackingState.etaConfidence,
      stopsRemaining: _activeTripWithNavigation.trackingState.stopsRemaining,
      stopsCompleted: _activeTripWithNavigation.trackingState.stopsCompleted,
      routeRecomputeReason:
          _activeTripWithNavigation.trackingState.routeRecomputeReason,
      driverLocationUpdatedAt: driverLocationUpdatedAt,
    ),
    horarioPartida: _activeTripWithNavigation.horarioPartida,
  );
}

final _terminalTrip = DriverTripRecord(
  id: 94,
  rotaBaseId: 4,
  rotaNome: 'Rota Centro',
  servicoNome: 'Linha Manha',
  tipoOperacao: 'IDA',
  dataOperacao: _operationDate,
  status: TripTrackingStatus.inProgress,
  rotaVersao: 2,
  etaMinutosAteProximaParada: null,
  etaAtualizadoEm: null,
  motorista: null,
  proximaParada: null,
  paradas: [],
  rotaGeografica: null,
  horarioPartida: '07:30',
);

final _operationDate = DateTime(2026, 3, 13);
