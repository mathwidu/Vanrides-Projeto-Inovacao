import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

import 'package:vanrides_frontend_dev/features/tracking/data/trip_live_location_service.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_live_location_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_route_geometry_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_state_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/trip_live_map_panel.dart';
import 'package:vanrides_frontend_dev/services/websocket_service.dart';

const _mapLibreProofEnabled = bool.fromEnvironment(
  'VANRIDES_MAPLIBRE_PROOF',
  defaultValue: false,
);
const _deviceSmokeEnabled = bool.fromEnvironment(
  'VANRIDES_MAPLIBRE_DEVICE_SMOKE',
  defaultValue: false,
);

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets(
    'MapLibre proof renders on device behind explicit smoke flags',
    (tester) async {
      if (!_mapLibreProofEnabled || !_deviceSmokeEnabled) {
        throw TestFailure(
          'Run with VANRIDES_MAPLIBRE_PROOF=true and '
          'VANRIDES_MAPLIBRE_DEVICE_SMOKE=true.',
        );
      }

      await tester.binding.setSurfaceSize(const Size(390, 844));
      addTearDown(() => tester.binding.setSurfaceSize(null));

      await tester.pumpWidget(
        MaterialApp(
          debugShowCheckedModeBanner: false,
          home: Scaffold(
            body: TripLiveMapPanel(
              motoristaId: 7,
              email: 'maplibre.smoke@vanrides.dev',
              senha: 'senha123',
              gateway: const _FakeTripLiveLocationGateway(),
              webSocketService: _FakeWebSocketService(),
              renderMode: TripLiveMapRenderMode.interactive,
              renderer: TripLiveMapRenderer.mapLibreProof,
              mapLibreProofEnabled: _mapLibreProofEnabled,
              routeGeometry: _sampleGeometry,
              seedLocation: const TripLiveLocationRecord(
                latitude: -29.68,
                longitude: -51.13,
              ),
              trackingState: const TripTrackingStateRecord(
                routeSnapshotStatus: TripRouteSnapshotStatus.materialized,
                locationFreshness: TripLocationFreshness.live,
                etaConfidence: TripEtaConfidence.medium,
                stopsRemaining: 2,
                stopsCompleted: 1,
                nextStopOrder: 2,
                routeRecomputeReason: TripRouteRecomputeReason.none,
              ),
            ),
          ),
        ),
      );

      await tester.pumpAndSettle(const Duration(seconds: 1));

      expect(find.byKey(const Key('trip.live.map.maplibre')), findsOneWidget);
      expect(find.byKey(const Key('trip.live.map')), findsNothing);
      expect(tester.takeException(), isNull);
      debugPrintSynchronously(
        'VANRIDES_MAPLIBRE_DEVICE_SMOKE_READY:tracking-maplibre-proof',
      );
      await tester.pump(const Duration(seconds: 2));
      debugPrintSynchronously(
        'VANRIDES_MAPLIBRE_DEVICE_SMOKE_VISUAL_READY:tracking-maplibre-proof',
      );
      await tester.pump(const Duration(seconds: 3));
    },
    timeout: const Timeout(Duration(minutes: 4)),
  );
}

const _sampleGeometry = TripRouteGeometryRecord(
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

class _FakeTripLiveLocationGateway implements TripLiveLocationGateway {
  const _FakeTripLiveLocationGateway();

  @override
  Future<TripLiveLocationRecord?> fetchLatestLocation(int motoristaId) async {
    return const TripLiveLocationRecord(latitude: -29.68, longitude: -51.13);
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
