import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:google_maps_flutter/google_maps_flutter.dart';
import 'package:maplibre_gl/maplibre_gl.dart' as maplibre;
import 'package:vanrides_frontend_dev/l10n/app_strings.dart';
import 'package:vanrides_frontend_dev/features/tracking/data/trip_live_location_service.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_live_location_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_route_geometry_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_state_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/trip_live_map_panel.dart';

import '../../support/fakes/fake_websocket_service.dart';

class _FakeTripLiveLocationGateway implements TripLiveLocationGateway {
  const _FakeTripLiveLocationGateway(this.record);

  final TripLiveLocationRecord? record;

  @override
  Future<TripLiveLocationRecord?> fetchLatestLocation(int motoristaId) async {
    await Future<void>.delayed(Duration.zero);
    return record;
  }
}

class _ThrowingTripLiveLocationGateway implements TripLiveLocationGateway {
  const _ThrowingTripLiveLocationGateway(this.message);

  final String message;

  @override
  Future<TripLiveLocationRecord?> fetchLatestLocation(int motoristaId) async {
    throw TripLiveLocationException(message);
  }
}

TripRouteGeometryRecord _sampleGeometry() {
  return const TripRouteGeometryRecord(
    provider: 'GOOGLE_DIRECTIONS_V1',
    polyline: '',
    navegacao: TripNavigationRecord(
      provider: 'GOOGLE_MAPS_HANDOFF',
      url: 'https://maps.google.com/?q=feevale',
      origem: 'Origem',
      destino: 'Destino',
      quantidadeWaypoints: 3,
    ),
    waypoints: [
      TripRouteWaypointRecord(
        tipo: 'STOP',
        paradaId: 300,
        passageiroId: 21,
        nome: 'Parada 1',
        latitude: -29.68,
        longitude: -51.13,
        ordem: 1,
        proximaParada: false,
        paradaDoPassageiro: false,
      ),
      TripRouteWaypointRecord(
        tipo: 'STOP',
        paradaId: 301,
        passageiroId: 22,
        nome: 'Parada 2',
        latitude: -29.681,
        longitude: -51.131,
        ordem: 2,
        proximaParada: true,
        paradaDoPassageiro: false,
      ),
      TripRouteWaypointRecord(
        tipo: 'STOP',
        paradaId: 302,
        passageiroId: 23,
        nome: 'Parada 3',
        latitude: -29.682,
        longitude: -51.132,
        ordem: 3,
        proximaParada: false,
        paradaDoPassageiro: true,
      ),
    ],
    legs: [],
    filaTotalParadas: 3,
    posicaoPassageiroNaFila: 3,
    paradasAntesDoPassageiro: 2,
  );
}

TripRouteGeometryRecord _sampleOperationalSnapshotGeometry() {
  return TripRouteGeometryRecord(
    provider: 'GOOGLE_DIRECTIONS_V1',
    polyline: '',
    navegacao: const TripNavigationRecord(
      provider: 'GOOGLE_MAPS_HANDOFF',
      url: 'https://maps.google.com/?q=feevale',
      origem: 'Origem',
      destino: 'Destino',
      quantidadeWaypoints: 3,
    ),
    waypoints: const [
      TripRouteWaypointRecord(
        tipo: 'STOP',
        paradaId: 300,
        passageiroId: 21,
        nome: 'Parada 1',
        latitude: -29.68,
        longitude: -51.13,
        ordem: 1,
        proximaParada: true,
        paradaDoPassageiro: false,
      ),
    ],
    legs: const [],
    filaTotalParadas: 1,
    posicaoPassageiroNaFila: null,
    paradasAntesDoPassageiro: null,
    calculadaEm: DateTime(2026, 3, 12, 7, 35),
    snapshotOperacional: true,
  );
}

Widget _buildSubject({
  TripLiveMapRenderMode renderMode = TripLiveMapRenderMode.placeholder,
  TripLiveMapRenderer renderer = TripLiveMapRenderer.googleLegacy,
  bool mapLibreProofEnabled = false,
  TripRouteGeometryRecord? routeGeometry,
  TripTrackingStateRecord trackingState = TripTrackingStateRecord.unavailable,
  TripLiveLocationGateway? gateway,
  TripLiveLocationRecord? seedLocation,
}) {
  return MaterialApp(
    home: Scaffold(
      body: Center(
        child: TripLiveMapPanel(
          motoristaId: 7,
          email: 'passageiro@test.dev',
          senha: 'senha123',
          gateway: gateway,
          webSocketService: FakeWebSocketService(),
          renderMode: renderMode,
          renderer: renderer,
          mapLibreProofEnabled: mapLibreProofEnabled,
          routeGeometry: routeGeometry,
          trackingState: trackingState,
          seedLocation: seedLocation,
        ),
      ),
    ),
  );
}

Future<void> _pumpPanel(
  WidgetTester tester, {
  TripLiveMapRenderMode renderMode = TripLiveMapRenderMode.placeholder,
  TripLiveMapRenderer renderer = TripLiveMapRenderer.googleLegacy,
  bool mapLibreProofEnabled = false,
  TripRouteGeometryRecord? routeGeometry,
  TripTrackingStateRecord trackingState = TripTrackingStateRecord.unavailable,
  TripLiveLocationGateway? gateway,
  TripLiveLocationRecord? seedLocation,
}) async {
  await tester.binding.setSurfaceSize(const Size(390, 844));
  addTearDown(() => tester.binding.setSurfaceSize(null));

  await tester.pumpWidget(
    _buildSubject(
      renderMode: renderMode,
      renderer: renderer,
      mapLibreProofEnabled: mapLibreProofEnabled,
      routeGeometry: routeGeometry,
      trackingState: trackingState,
      gateway: gateway,
      seedLocation: seedLocation,
    ),
  );
  await tester.pumpAndSettle();
}

void main() {
  test('decide o renderer sem promover MapLibre por acidente', () {
    expect(
      resolveTripLiveMapRenderer(
        renderMode: TripLiveMapRenderMode.interactive,
        requestedRenderer: TripLiveMapRenderer.googleLegacy,
        mapLibreProofEnabled: false,
        e2eMode: false,
        isWeb: false,
        googleApiKey: '',
      ),
      TripLiveMapRenderer.googleLegacy,
    );
    expect(
      resolveTripLiveMapRenderer(
        renderMode: TripLiveMapRenderMode.interactive,
        requestedRenderer: TripLiveMapRenderer.mapLibreProof,
        mapLibreProofEnabled: false,
        e2eMode: false,
        isWeb: false,
        googleApiKey: '',
      ),
      TripLiveMapRenderer.googleLegacy,
    );
    expect(
      resolveTripLiveMapRenderer(
        renderMode: TripLiveMapRenderMode.interactive,
        requestedRenderer: TripLiveMapRenderer.mapLibreProof,
        mapLibreProofEnabled: true,
        e2eMode: false,
        isWeb: false,
        googleApiKey: '',
      ),
      TripLiveMapRenderer.mapLibreProof,
    );
    expect(
      resolveTripLiveMapRenderer(
        renderMode: TripLiveMapRenderMode.interactive,
        requestedRenderer: TripLiveMapRenderer.googleLegacy,
        mapLibreProofEnabled: false,
        e2eMode: true,
        isWeb: false,
        googleApiKey: 'AIza-test',
      ),
      TripLiveMapRenderer.deterministic,
    );
    expect(
      resolveTripLiveMapRenderer(
        renderMode: TripLiveMapRenderMode.placeholder,
        requestedRenderer: TripLiveMapRenderer.mapLibreProof,
        mapLibreProofEnabled: true,
        e2eMode: false,
        isWeb: false,
        googleApiKey: 'AIza-test',
      ),
      TripLiveMapRenderer.placeholder,
    );
  });

  test('decide quando pode usar mapa de plataforma', () {
    expect(
      shouldUseTripLivePlatformMap(
        renderMode: TripLiveMapRenderMode.interactive,
        e2eMode: false,
        isWeb: false,
        googleApiKey: '',
      ),
      isTrue,
    );
    expect(
      shouldUseTripLivePlatformMap(
        renderMode: TripLiveMapRenderMode.interactive,
        e2eMode: false,
        isWeb: true,
        googleApiKey: '',
      ),
      isFalse,
    );
    expect(
      shouldUseTripLivePlatformMap(
        renderMode: TripLiveMapRenderMode.interactive,
        e2eMode: false,
        isWeb: true,
        googleApiKey: 'AIza-test',
      ),
      isTrue,
    );
    expect(
      shouldUseTripLivePlatformMap(
        renderMode: TripLiveMapRenderMode.placeholder,
        e2eMode: false,
        isWeb: false,
        googleApiKey: 'AIza-test',
      ),
      isFalse,
    );
    expect(
      shouldUseTripLivePlatformMap(
        renderMode: TripLiveMapRenderMode.interactive,
        e2eMode: true,
        isWeb: false,
        googleApiKey: 'AIza-test',
      ),
      isFalse,
    );
  });

  testWidgets('exibe estado de rota pronta aguardando sinal ao vivo', (
    tester,
  ) async {
    await _pumpPanel(tester, routeGeometry: _sampleGeometry());

    expect(
      find.text(AppStrings.passageiro.rotaProntaAguardandoSinal),
      findsOneWidget,
    );
    expect(find.byIcon(Icons.alt_route_rounded), findsOneWidget);
    expect(
      find.textContaining('A rota de ruas já foi publicada'),
      findsOneWidget,
    );
    expect(
      find.byKey(const Key('vanrides.map.placeholder.surface')),
      findsOneWidget,
    );
    expect(find.byType(Card), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('exibe quando a rota ja foi calculada para a execucao', (
    tester,
  ) async {
    await _pumpPanel(
      tester,
      routeGeometry: _sampleOperationalSnapshotGeometry(),
    );

    expect(
      find.textContaining('Rota operacional calculada às 07:35'),
      findsOneWidget,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('exibe estado de rota com motorista ao vivo', (tester) async {
    await _pumpPanel(
      tester,
      routeGeometry: _sampleGeometry(),
      seedLocation: const TripLiveLocationRecord(
        latitude: -29.68,
        longitude: -51.13,
      ),
    );

    expect(
      find.text(AppStrings.passageiro.rotaComMotoristaAoVivo),
      findsOneWidget,
    );
    expect(find.byIcon(Icons.alt_route_rounded), findsOneWidget);
    expect(
      find.textContaining('O motorista já aparece no mapa'),
      findsOneWidget,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('renderiza mapa interativo quando ha alvo inicial', (
    tester,
  ) async {
    await _pumpPanel(
      tester,
      renderMode: TripLiveMapRenderMode.interactive,
      routeGeometry: _sampleGeometry(),
      seedLocation: const TripLiveLocationRecord(
        latitude: -29.68,
        longitude: -51.13,
      ),
    );

    expect(find.byKey(const Key('trip.live.map')), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'renderiza prova MapLibre apenas quando explicitamente habilitada',
    (tester) async {
      await _pumpPanel(
        tester,
        renderMode: TripLiveMapRenderMode.interactive,
        renderer: TripLiveMapRenderer.mapLibreProof,
        mapLibreProofEnabled: true,
        routeGeometry: _sampleGeometry(),
        seedLocation: const TripLiveLocationRecord(
          latitude: -29.68,
          longitude: -51.13,
        ),
      );

      expect(find.byKey(const Key('trip.live.map.maplibre')), findsOneWidget);
      expect(find.byKey(const Key('trip.live.map')), findsNothing);
      final mapLibre = tester.widget<maplibre.MapLibreMap>(
        find.byKey(const Key('trip.live.map.maplibre')),
      );
      expect(mapLibre.styleString, contains('VanRides MapLibre proof'));
      final styleJson =
          jsonDecode(mapLibre.styleString) as Map<String, dynamic>;
      final metadata = styleJson['metadata'] as Map<String, dynamic>;
      expect(metadata['vanrides:region'], 'BR-RS');
      expect(metadata['vanrides:styleVersion'], 'style-smoke-v1');
      expect(metadata['vanrides:tileSetVersion'], 'br-rs-smoke-v1');
      final sources = styleJson['sources'] as Map<String, dynamic>;
      final vectorSource =
          sources['vanrides-vector-tiles'] as Map<String, dynamic>;
      expect(vectorSource['type'], 'vector');
      expect(
        vectorSource['tiles'],
        contains(
          'http://127.0.0.1:8099/vanrides/maps/BR-RS/vector/{z}/{x}/{y}.pbf',
        ),
      );
      expect(mapLibre.styleString, isNot(contains('tile.openstreetmap.org')));
      expect(mapLibre.styleString, isNot(contains('demotiles.maplibre.org')));
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('mapa interativo usa marcadores VanRides customizados', (
    tester,
  ) async {
    await _pumpPanel(
      tester,
      renderMode: TripLiveMapRenderMode.interactive,
      routeGeometry: _sampleGeometry(),
      trackingState: const TripTrackingStateRecord(
        routeSnapshotStatus: TripRouteSnapshotStatus.materialized,
        locationFreshness: TripLocationFreshness.stale,
        etaConfidence: TripEtaConfidence.medium,
        stopsRemaining: 2,
        stopsCompleted: 1,
        nextStopOrder: 2,
        routeRecomputeReason: TripRouteRecomputeReason.none,
      ),
      seedLocation: const TripLiveLocationRecord(
        latitude: -29.68,
        longitude: -51.13,
        freshness: TripLocationFreshness.stale,
      ),
    );

    final map = tester.widget<GoogleMap>(
      find.byKey(const Key('trip.live.map')),
    );
    final driver = map.markers.singleWhere(
      (marker) => marker.markerId == const MarkerId('motorista'),
    );
    final passenger = map.markers.singleWhere(
      (marker) => marker.markerId.value.startsWith('passenger-'),
    );
    final completedStop = map.markers.singleWhere(
      (marker) => marker.markerId.value.contains('stop-300'),
    );
    final nextStop = map.markers.singleWhere(
      (marker) => marker.markerId.value.contains('stop-301'),
    );

    expect(driver.infoWindow.title, contains('Van'));
    expect(driver.infoWindow.snippet, contains('sinal'));
    expect(passenger.infoWindow.title, contains('Passageiro'));
    expect(completedStop.infoWindow.snippet, contains('concluida'));
    expect(nextStop.infoWindow.snippet, contains('proxima'));

    for (final marker in [driver, passenger, completedStop, nextStop]) {
      final payload = marker.icon.toJson().toString();

      expect(payload, isNot(contains('defaultMarker')));
      expect(payload, isNot(contains('defaultMarkerWithHue')));
    }
    expect(tester.takeException(), isNull);
  });

  testWidgets('mapa interativo reserva padding inferior para sheet premium', (
    tester,
  ) async {
    await _pumpPanel(
      tester,
      renderMode: TripLiveMapRenderMode.interactive,
      routeGeometry: _sampleGeometry(),
      seedLocation: const TripLiveLocationRecord(
        latitude: -29.68,
        longitude: -51.13,
        freshness: TripLocationFreshness.live,
      ),
    );

    final map = tester.widget<GoogleMap>(
      find.byKey(const Key('trip.live.map')),
    );

    expect(map.padding.bottom, greaterThan(map.padding.top));
    expect(map.padding.bottom, greaterThanOrEqualTo(120));
    expect(tester.takeException(), isNull);
  });

  testWidgets('exibe fallback de erro quando a localizacao falha', (
    tester,
  ) async {
    await _pumpPanel(
      tester,
      renderMode: TripLiveMapRenderMode.interactive,
      gateway: const _ThrowingTripLiveLocationGateway('Falha ao carregar mapa'),
    );

    expect(
      find.text(AppStrings.passageiro.mapaIndisponivelNoMomento),
      findsOneWidget,
    );
    expect(find.text('Falha ao carregar mapa'), findsOneWidget);
    expect(find.byIcon(Icons.warning_amber_outlined), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('renderiza sem rota nem waypoints sem lançar excecao', (
    tester,
  ) async {
    await _pumpPanel(tester, gateway: const _FakeTripLiveLocationGateway(null));

    expect(
      find.text(AppStrings.passageiro.aguardandoLocalizacaoAoVivo),
      findsOneWidget,
    );
    expect(find.byIcon(Icons.wifi_tethering_off_rounded), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
