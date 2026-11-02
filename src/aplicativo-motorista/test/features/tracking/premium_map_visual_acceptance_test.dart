import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:google_maps_flutter/google_maps_flutter.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/domain/driver_trip_record.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/presentation/widgets/driver_home_trip_widgets.dart';
import 'package:vanrides_frontend_dev/features/guardian/domain/guardian_linked_passageiro_record.dart';
import 'package:vanrides_frontend_dev/features/guardian/presentation/widgets/guardian_localizacao_widgets.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_route_geometry_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_state_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/trip_live_map_panel.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/trip_live_map_state.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/widgets/passenger_home_trip_widgets.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/widgets/vanrides_map_marker_specs.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/widgets/vanrides_map_shell.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/widgets/vanrides_route_layers.dart';

void main() {
  test(
    'visual acceptance handoff lists viewports, states and E2E entrypoints',
    () {
      final handoff = File('backlog/TRACKING-PREMIUM-MAP-VISUAL-ACCEPTANCE.md');

      expect(handoff.existsSync(), isTrue);

      final content = handoff.readAsStringSync();
      for (final expected in [
        '375x667',
        '390x844',
        '430x932',
        '768x1024',
        'passageiro live',
        'passageiro stale',
        'responsavel selected passenger',
        'motorista planned',
        'motorista in progress',
        'no route',
        'no signal',
        'integration_test/passageiro/all.dart',
        'integration_test/guardian/all.dart',
        'integration_test/motorista/all.dart',
        'PHASE-0502',
      ]) {
        expect(content, contains(expected));
      }
    },
  );

  test('markers and route semantics cover premium map acceptance states', () {
    final liveVan = vanRidesVehicleMarkerSpec(TripLocationFreshness.live);
    final staleVan = vanRidesVehicleMarkerSpec(TripLocationFreshness.stale);
    final passenger = vanRidesPassengerMarkerSpec(
      label: 'Ana Escola',
      selected: true,
    );
    final degradedLayers = buildVanRidesRouteLayers(
      routeGeometry: _routeGeometry(),
      trackingState: const TripTrackingStateRecord(
        routeSnapshotStatus: TripRouteSnapshotStatus.stale,
        locationFreshness: TripLocationFreshness.stale,
        etaConfidence: TripEtaConfidence.low,
        stopsRemaining: 2,
        stopsCompleted: 0,
        routeRecomputeReason: TripRouteRecomputeReason.providerFallback,
      ),
    );
    final unavailableLayers = buildVanRidesRouteLayers(
      routeGeometry: _routeGeometry(
        routeSnapshotStatus: TripRouteSnapshotStatus.unavailable,
      ),
      trackingState: TripTrackingStateRecord.unavailable,
    );

    expect(liveVan.visualLanguage, VanRidesMapMarkerVisualLanguage.vehicle);
    expect(liveVan.communicatesLiveMotion, isTrue);
    expect(staleVan.communicatesLiveMotion, isFalse);
    expect(staleVan.semanticLabel, contains('ultima posicao conhecida'));
    expect(passenger.visualLanguage, VanRidesMapMarkerVisualLanguage.person);
    expect(passenger.semanticLabel, contains('Ana Escola'));
    expect(degradedLayers.single.type, VanRidesRouteLayerType.degraded);
    expect(degradedLayers.single.patterns, isNotEmpty);
    expect(unavailableLayers, isEmpty);
  });

  for (final viewport in _criticalViewports) {
    testWidgets('premium map roles fit ${viewport.label}', (tester) async {
      await tester.binding.setSurfaceSize(viewport.size);
      addTearDown(() => tester.binding.setSurfaceSize(null));

      for (final scenario in _visualScenarios) {
        await _pumpAcceptanceScenario(
          tester,
          viewport: viewport,
          scenario: scenario,
        );

        scenario.expectations(tester);
        expect(tester.takeException(), isNull, reason: scenario.name);
      }
    });
  }
}

Future<void> _pumpAcceptanceScenario(
  WidgetTester tester, {
  required _AcceptanceViewport viewport,
  required _VisualScenario scenario,
}) async {
  await tester.pumpWidget(
    MaterialApp(
      home: MediaQuery(
        data: MediaQueryData(
          size: viewport.size,
          textScaler: const TextScaler.linear(1.12),
        ),
        child: Scaffold(
          backgroundColor: VanRidesMapTokens.backgroundDeep,
          body: SizedBox(
            width: viewport.size.width,
            height: viewport.size.height,
            child: scenario.builder(),
          ),
        ),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

final _criticalViewports = [
  const _AcceptanceViewport(label: '375x667', size: Size(375, 667)),
  const _AcceptanceViewport(label: '390x844', size: Size(390, 844)),
  const _AcceptanceViewport(label: '430x932', size: Size(430, 932)),
  const _AcceptanceViewport(label: '768x1024', size: Size(768, 1024)),
];

final _visualScenarios = [
  _VisualScenario(
    name: 'passageiro live',
    builder:
        () => PassengerHomeMapScene(
          trip: _trackingTrip(freshness: TripLocationFreshness.live),
          dayTrips: [_trackingTrip(freshness: TripLocationFreshness.live)],
          selectedTripId: 30,
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
    expectations: (tester) {
      expect(
        find.textContaining(RegExp('Sinal ao vivo|Ao vivo')),
        findsOneWidget,
      );
      expect(find.text('Faltam 2 paradas'), findsOneWidget);
      expect(find.text('Mapa Completo'), findsNothing);
      expect(find.text('Van'), findsNothing);
      expect(find.text('Voce'), findsNothing);
      expect(find.text('Presenca pendente'), findsNothing);
      expect(
        find.byKey(const Key('passenger.trip.sheet.resting')),
        findsWidgets,
      );
      expect(find.text('Minha parada'), findsWidgets);
      expect(find.text('Mais'), findsNothing);
      expect(find.text('Rota'), findsNothing);
    },
  ),
  _VisualScenario(
    name: 'passageiro stale',
    builder:
        () => PassengerHomeMapScene(
          trip: _trackingTrip(
            id: 31,
            freshness: TripLocationFreshness.stale,
            routeSnapshotStatus: TripRouteSnapshotStatus.stale,
          ),
          dayTrips: const [],
          selectedTripId: 31,
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
    expectations: (tester) {
      expect(find.text('Posição antiga'), findsOneWidget);
      expect(find.text('Antiga'), findsOneWidget);
      expect(find.text('A van pode ter mudado de lugar.'), findsOneWidget);
    },
  ),
  _VisualScenario(
    name: 'responsavel selected passenger',
    builder:
        () => _SheetSurface(
          child: GuardianLocalizacaoActiveTripSheet(
            passageiro: _guardianPassageiro(id: 10, nome: 'Ana'),
            trip: _trackingTrip(
              freshness: TripLocationFreshness.delayed,
              motoristaNome: 'Motorista Manha',
            ),
            dayTrips: const [],
            selectedTripId: 30,
            onTripSelected: (_) {},
            passageiros: [
              _guardianPassageiro(id: 10, nome: 'Ana'),
              _guardianPassageiro(id: 11, nome: 'Bruno'),
            ],
            selectedPassageiroId: 10,
            onPassageiroSelected: (_) {},
            onOpenMap: () {},
          ),
        ),
    expectations: (tester) {
      expect(find.text('Passageiro selecionado'), findsOneWidget);
      expect(find.text('Posição recente da van'), findsOneWidget);
      expect(
        find.text('Acompanhe pela última posição enquanto o sinal volta.'),
        findsOneWidget,
      );
      expect(find.text('Ana'), findsAtLeastNWidgets(1));
    },
  ),
  _VisualScenario(
    name: 'motorista planned',
    builder:
        () => _SheetSurface(
          child: DriverHomePlanningSheet(
            trip: _driverTrip(status: TripTrackingStatus.planned),
            nextStop: _driverStop,
            dayTrips: const [],
            selectedTripId: 91,
            agendaKey: const Key('driver.agenda'),
            tripKeyBuilder: (id) => Key('driver.trip.$id'),
            startTripKey: const Key('driver.start'),
            detailsKey: const Key('driver.details'),
            mapKey: const Key('driver.map'),
            editRouteKey: const Key('driver.edit'),
            runningLifecycleAction: false,
            onSelectTrip: (_) {},
            onStartTrip: () {},
            onOpenStops: () {},
            onOpenMap: () {},
            onEditRoute: () {},
          ),
        ),
    expectations: (tester) {
      expect(find.text('Transmitindo ao vivo'), findsOneWidget);
      expect(find.text('Iniciar Corrida'), findsOneWidget);
      expect(find.text('Mapa'), findsOneWidget);
    },
  ),
  _VisualScenario(
    name: 'motorista in progress',
    builder:
        () => _SheetSurface(
          child: DriverHomeActiveSheet(
            trip: _driverTrip(status: TripTrackingStatus.inProgress),
            nextStop: _driverStop,
            dayTrips: const [],
            selectedTripId: 92,
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
    expectations: (tester) {
      expect(find.text('Transmitindo ao vivo'), findsOneWidget);
      expect(find.text('Proximo passageiro'), findsOneWidget);
      expect(find.text('Confirmar embarque'), findsWidgets);
      expect(find.text('Proxima acao'), findsOneWidget);
      expect(find.text('Faltou'), findsWidgets);
    },
  ),
  _VisualScenario(
    name: 'no route / no signal',
    builder:
        () => const VanRidesMapPlaceholderSurface(
          content: TripLiveMapPlaceholderContent(
            loading: false,
            title: 'Aguardando localizacao ao vivo',
            description: 'A van ainda nao publicou coordenadas para esta rota.',
            routeReady: false,
            waitingForSignal: true,
          ),
        ),
    expectations: (tester) {
      expect(find.text('Aguardando localizacao ao vivo'), findsOneWidget);
      expect(find.byIcon(Icons.wifi_tethering_off_rounded), findsOneWidget);
    },
  ),
];

class _SheetSurface extends StatelessWidget {
  const _SheetSurface({required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: const BoxDecoration(color: VanRidesMapTokens.backgroundDeep),
      child: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(16),
          child: DecoratedBox(
            decoration: BoxDecoration(
              color: VanRidesMapTokens.surface,
              borderRadius: BorderRadius.circular(28),
            ),
            child: Padding(padding: const EdgeInsets.all(18), child: child),
          ),
        ),
      ),
    );
  }
}

class _AcceptanceViewport {
  const _AcceptanceViewport({required this.label, required this.size});

  final String label;
  final Size size;
}

class _VisualScenario {
  const _VisualScenario({
    required this.name,
    required this.builder,
    required this.expectations,
  });

  final String name;
  final Widget Function() builder;
  final void Function(WidgetTester tester) expectations;
}

TripTrackingRecord _trackingTrip({
  int id = 30,
  TripLocationFreshness freshness = TripLocationFreshness.live,
  TripRouteSnapshotStatus routeSnapshotStatus =
      TripRouteSnapshotStatus.materialized,
  String motoristaNome = 'Motorista Portal',
}) {
  return TripTrackingRecord(
    id: id,
    rotaNome: 'Rota Escolar',
    servicoId: 130,
    servicoNome: 'Linha Manha',
    tipoOperacao: 'IDA',
    dataOperacao: DateTime(2026, 3, 20),
    status: TripTrackingStatus.inProgress,
    rotaVersao: 1,
    etaMinutosAtePassageiro: 8,
    etaAtualizadoEm: DateTime(2026, 3, 20, 7, 30),
    motorista: TripTrackingDriverRecord(id: 7, nome: motoristaNome),
    rotaGeografica: _routeGeometry(routeSnapshotStatus: routeSnapshotStatus),
    paradaDoPassageiro: const TripTrackingPassengerStopRecord(
      id: 330,
      ordem: 3,
      status: TripTrackingStopStatus.pending,
      etaMinutos: 8,
      confirmadoPresenca: true,
      proximaParada: true,
      passageiroId: 21,
      passageiroNome: 'Ana Escola',
      menorDeIdade: true,
    ),
    horarioPartida: '07:30',
    trackingState: TripTrackingStateRecord(
      routeSnapshotStatus: routeSnapshotStatus,
      locationFreshness: freshness,
      etaConfidence:
          freshness == TripLocationFreshness.live
              ? TripEtaConfidence.high
              : TripEtaConfidence.low,
      stopsRemaining: 3,
      stopsCompleted: 1,
      stopsBeforePassenger: 2,
      routeRecomputeReason: TripRouteRecomputeReason.none,
    ),
  );
}

DriverTripRecord _driverTrip({required TripTrackingStatus status}) {
  return DriverTripRecord(
    id: status == TripTrackingStatus.planned ? 91 : 92,
    rotaBaseId: 4,
    rotaNome: 'Rota Centro',
    servicoNome: 'Linha Manha',
    tipoOperacao: 'IDA',
    dataOperacao: DateTime(2026, 3, 20),
    status: status,
    rotaVersao: 2,
    etaMinutosAteProximaParada: 9,
    etaAtualizadoEm: null,
    motorista: null,
    proximaParada: _driverStop,
    paradas: const [_driverStop],
    rotaGeografica: _routeGeometry(),
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
}

const _driverStop = DriverTripStopRecord(
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

GuardianLinkedPassageiroRecord _guardianPassageiro({
  required int id,
  required String nome,
}) {
  return GuardianLinkedPassageiroRecord(
    id: id,
    nome: nome,
    email: '$nome@vanrides.dev',
    telefone: '51999990000',
    endereco: 'Rua A',
    bairro: 'Centro',
    cidade: 'Porto Alegre',
    estado: 'RS',
    cep: '90000-000',
    menorDeIdade: true,
    motoristaId: 7,
    motoristaNome: 'Motorista Portal',
  );
}

TripRouteGeometryRecord _routeGeometry({
  TripRouteSnapshotStatus routeSnapshotStatus =
      TripRouteSnapshotStatus.materialized,
}) {
  final routePoints = const [
    LatLng(-29.679, -51.129),
    LatLng(-29.680, -51.130),
    LatLng(-29.681, -51.131),
    LatLng(-29.682, -51.132),
    LatLng(-29.683, -51.133),
  ];

  return TripRouteGeometryRecord(
    provider: 'GOOGLE_DIRECTIONS_V1',
    polyline: _encodePolyline(routePoints),
    navegacao: null,
    waypoints: const [],
    legs: const [],
    routeSnapshotStatus: routeSnapshotStatus,
    filaTotalParadas: 3,
    posicaoPassageiroNaFila: 3,
    paradasAntesDoPassageiro: 2,
  );
}

String _encodePolyline(List<LatLng> points) {
  final buffer = StringBuffer();
  var previousLatitude = 0;
  var previousLongitude = 0;

  for (final point in points) {
    final latitude = (point.latitude * 1e5).round();
    final longitude = (point.longitude * 1e5).round();
    _encodeValue(latitude - previousLatitude, buffer);
    _encodeValue(longitude - previousLongitude, buffer);
    previousLatitude = latitude;
    previousLongitude = longitude;
  }

  return buffer.toString();
}

void _encodeValue(int value, StringBuffer buffer) {
  var encoded = value < 0 ? ~(value << 1) : value << 1;
  while (encoded >= 0x20) {
    buffer.writeCharCode((0x20 | (encoded & 0x1f)) + 63);
    encoded >>= 5;
  }
  buffer.writeCharCode(encoded + 63);
}
