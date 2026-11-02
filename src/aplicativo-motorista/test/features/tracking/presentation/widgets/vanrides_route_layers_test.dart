import 'package:flutter_test/flutter_test.dart';
import 'package:google_maps_flutter/google_maps_flutter.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_live_location_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_route_geometry_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_state_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/trip_live_map_state.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/widgets/vanrides_route_layers.dart';

void main() {
  group('VanRides route layers', () {
    test('builds planned, completed and remaining layers for fresh route', () {
      final route = _routeGeometry();
      const trackingState = TripTrackingStateRecord(
        routeSnapshotStatus: TripRouteSnapshotStatus.materialized,
        locationFreshness: TripLocationFreshness.live,
        etaConfidence: TripEtaConfidence.high,
        stopsRemaining: 2,
        stopsCompleted: 1,
        nextStopOrder: 2,
        routeRecomputeReason: TripRouteRecomputeReason.none,
      );

      final layers = buildVanRidesRouteLayers(
        routeGeometry: route,
        trackingState: trackingState,
      );

      expect(
        layers.map((layer) => layer.type),
        containsAll([
          VanRidesRouteLayerType.planned,
          VanRidesRouteLayerType.completed,
          VanRidesRouteLayerType.remaining,
        ]),
      );

      final completed = layers.singleWhere(
        (layer) => layer.type == VanRidesRouteLayerType.completed,
      );
      final remaining = layers.singleWhere(
        (layer) => layer.type == VanRidesRouteLayerType.remaining,
      );

      expect(completed.color, isNot(remaining.color));
      expect(completed.width, lessThanOrEqualTo(remaining.width));
      expect(completed.points.length, greaterThanOrEqualTo(2));
      expect(remaining.points.length, greaterThanOrEqualTo(2));
      expect(completed.points.last.latitude, remaining.points.first.latitude);
      expect(completed.points.last.longitude, remaining.points.first.longitude);
    });

    test('builds a degraded dashed layer for stale route snapshots', () {
      final layers = buildVanRidesRouteLayers(
        routeGeometry: _routeGeometry(),
        trackingState: const TripTrackingStateRecord(
          routeSnapshotStatus: TripRouteSnapshotStatus.stale,
          locationFreshness: TripLocationFreshness.stale,
          etaConfidence: TripEtaConfidence.low,
          stopsRemaining: 3,
          stopsCompleted: 0,
          routeRecomputeReason: TripRouteRecomputeReason.providerFallback,
        ),
      );

      expect(layers, hasLength(1));
      expect(layers.single.type, VanRidesRouteLayerType.degraded);
      expect(layers.single.patterns, isNotEmpty);
    });

    test('does not draw a false line when route is unavailable', () {
      final layers = buildVanRidesRouteLayers(
        routeGeometry: _routeGeometry(
          routeSnapshotStatus: TripRouteSnapshotStatus.unavailable,
        ),
        trackingState: TripTrackingStateRecord.unavailable,
      );

      expect(layers, isEmpty);
      expect(buildVanRidesRoutePolylines(layers), isEmpty);
    });

    test('does not redraw remaining route when all stops are completed', () {
      final layers = buildVanRidesRouteLayers(
        routeGeometry: _routeGeometry(),
        trackingState: const TripTrackingStateRecord(
          routeSnapshotStatus: TripRouteSnapshotStatus.materialized,
          locationFreshness: TripLocationFreshness.live,
          etaConfidence: TripEtaConfidence.high,
          stopsRemaining: 0,
          stopsCompleted: 3,
          routeRecomputeReason: TripRouteRecomputeReason.none,
        ),
      );

      expect(
        layers.map((layer) => layer.type),
        contains(VanRidesRouteLayerType.completed),
      );
      expect(
        layers.map((layer) => layer.type),
        isNot(contains(VanRidesRouteLayerType.remaining)),
      );
    });
  });

  test('next-stop camera focus excludes the full route polyline', () {
    final points = collectTripLiveMapPoints(
      location: null,
      routeGeometry: _routeGeometry(),
      focusMode: TripLiveMapFocusMode.nextStopOnly,
    );

    expect(points, hasLength(1));
    expect(points.single.latitude, -29.681);
    expect(points.single.longitude, -51.131);
  });

  test('passenger camera focus keeps van, next stop and passenger stop', () {
    final points = collectTripLiveMapPoints(
      location: const TripLiveLocationRecord(
        latitude: -29.679,
        longitude: -51.129,
      ),
      routeGeometry: _routeGeometry(),
      focusMode: TripLiveMapFocusMode.passengerJourney,
    );

    expect(points, hasLength(3));
    expect(points.map((point) => point.latitude), [-29.679, -29.681, -29.682]);
    expect(points.map((point) => point.longitude), [-51.129, -51.131, -51.132]);
  });

  test('vehicle camera focus uses only the van position', () {
    final points = collectTripLiveMapPoints(
      location: const TripLiveLocationRecord(
        latitude: -29.679,
        longitude: -51.129,
      ),
      routeGeometry: _routeGeometry(),
      focusMode: TripLiveMapFocusMode.vehicleOnly,
    );

    expect(points, hasLength(1));
    expect(points.single.latitude, -29.679);
    expect(points.single.longitude, -51.129);
  });

  test('passenger-only camera focus uses only passenger stop', () {
    final points = collectTripLiveMapPoints(
      location: const TripLiveLocationRecord(
        latitude: -29.679,
        longitude: -51.129,
      ),
      routeGeometry: _routeGeometry(),
      focusMode: TripLiveMapFocusMode.passengerOnly,
    );

    expect(points, hasLength(1));
    expect(points.single.latitude, -29.682);
    expect(points.single.longitude, -51.132);
  });
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
    routeSnapshotStatus: routeSnapshotStatus,
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
