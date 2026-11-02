import 'package:flutter_test/flutter_test.dart';
import 'package:google_maps_flutter/google_maps_flutter.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_live_location_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_route_geometry_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_state_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/trip_live_map_state.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/vanrides_map_renderer_spike.dart';

void main() {
  test(
    'shared renderer scene preserves route layers and operational markers',
    () {
      final scene = buildVanRidesMapRendererScene(
        routeGeometry: _routeGeometry(),
        location: const TripLiveLocationRecord(
          latitude: -29.679,
          longitude: -51.129,
          freshness: TripLocationFreshness.live,
        ),
        trackingState: _trackingState,
        focusMode: TripLiveMapFocusMode.fullJourney,
      );

      expect(scene.routePointCount, 5);
      expect(scene.markers.map((marker) => marker.kind), [
        VanRidesMapSceneMarkerKind.vehicle,
        VanRidesMapSceneMarkerKind.stop,
        VanRidesMapSceneMarkerKind.stop,
        VanRidesMapSceneMarkerKind.passenger,
      ]);
      expect(scene.markers.map((marker) => marker.id), [
        'vehicle-live',
        'stop-300-1',
        'stop-301-2',
        'passenger-302-3',
      ]);
      expect(scene.markers.last.semanticLabel, contains('Passageiro'));
      expect(
        scene.routeLayers.map((layer) => layer.role),
        contains('remaining'),
      );
    },
  );

  test('renderer matrix compares candidates with the exact same scene', () {
    final scene = buildVanRidesMapRendererScene(
      routeGeometry: _routeGeometry(),
      location: const TripLiveLocationRecord(
        latitude: -29.679,
        longitude: -51.129,
        freshness: TripLocationFreshness.live,
      ),
      trackingState: _trackingState,
      focusMode: TripLiveMapFocusMode.fullJourney,
    );

    final matrix = buildVanRidesMapRendererSpikeMatrix(scene);
    final mapLibre = matrix.assessmentFor(
      VanRidesMapRendererCandidate.mapLibreVector,
    );
    final flutterMap = matrix.assessmentFor(
      VanRidesMapRendererCandidate.flutterMapRaster,
    );

    expect(
      matrix.recommendedCandidate,
      VanRidesMapRendererCandidate.mapLibreVector,
    );
    expect(mapLibre.routePointCount, scene.routePointCount);
    expect(flutterMap.routePointCount, scene.routePointCount);
    expect(mapLibre.markerCount, scene.markers.length);
    expect(flutterMap.markerCount, scene.markers.length);
    expect(mapLibre.supportsSelfHostedTiles, isTrue);
    expect(mapLibre.supportsOffline, isTrue);
    expect(mapLibre.tileModel, VanRidesMapTileModel.selfHostedVector);
    expect(flutterMap.tileModel, VanRidesMapTileModel.selfHostedRaster);
  });

  test('legacy and future renderers document runtime blockers honestly', () {
    final matrix = buildVanRidesMapRendererSpikeMatrix(
      buildVanRidesMapRendererScene(
        routeGeometry: _routeGeometry(),
        location: null,
        trackingState: _trackingState,
        focusMode: TripLiveMapFocusMode.fullJourney,
      ),
    );

    final google = matrix.assessmentFor(
      VanRidesMapRendererCandidate.googleLegacy,
    );
    final mapLibre = matrix.assessmentFor(
      VanRidesMapRendererCandidate.mapLibreVector,
    );
    final flutterMap = matrix.assessmentFor(
      VanRidesMapRendererCandidate.flutterMapRaster,
    );
    final deterministic = matrix.assessmentFor(
      VanRidesMapRendererCandidate.deterministicTest,
    );

    expect(google.supportsSelfHostedTiles, isFalse);
    expect(google.supportsOffline, isFalse);
    expect(google.runtimeReadiness, VanRidesMapRuntimeReadiness.activeFallback);
    expect(
      mapLibre.runtimeReadiness,
      VanRidesMapRuntimeReadiness.blockedByDependency,
    );
    expect(
      flutterMap.runtimeReadiness,
      VanRidesMapRuntimeReadiness.blockedByDependency,
    );
    expect(
      deterministic.runtimeReadiness,
      VanRidesMapRuntimeReadiness.testOnly,
    );
    expect(mapLibre.blockers, contains(contains('maplibre')));
    expect(flutterMap.blockers, contains(contains('flutter_map')));
    expect(matrix.tilePolicy, contains('publicos OSM nao sao backend'));
  });
}

const _trackingState = TripTrackingStateRecord(
  routeSnapshotStatus: TripRouteSnapshotStatus.materialized,
  locationFreshness: TripLocationFreshness.live,
  etaConfidence: TripEtaConfidence.high,
  stopsRemaining: 2,
  stopsCompleted: 1,
  nextStopOrder: 2,
  routeRecomputeReason: TripRouteRecomputeReason.none,
);

TripRouteGeometryRecord _routeGeometry() {
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
        latitude: -29.680,
        longitude: -51.130,
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
    legs: const [],
    filaTotalParadas: 3,
    posicaoPassageiroNaFila: 3,
    paradasAntesDoPassageiro: 2,
    routeSnapshotStatus: TripRouteSnapshotStatus.materialized,
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
