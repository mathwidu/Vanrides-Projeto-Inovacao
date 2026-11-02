import 'package:flutter_test/flutter_test.dart';
import 'package:google_maps_flutter/google_maps_flutter.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_route_geometry_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_state_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/widgets/vanrides_map_markers.dart';

void main() {
  group('VanRides map marker specs', () {
    test('vehicle specs express freshness without stale movement cues', () {
      final live = vanRidesVehicleMarkerSpec(TripLocationFreshness.live);
      final delayed = vanRidesVehicleMarkerSpec(TripLocationFreshness.delayed);
      final stale = vanRidesVehicleMarkerSpec(TripLocationFreshness.stale);
      final unavailable = vanRidesVehicleMarkerSpec(
        TripLocationFreshness.unavailable,
      );

      expect(live.kind, VanRidesMapMarkerKind.vehicle);
      expect(live.visualLanguage, VanRidesMapMarkerVisualLanguage.vehicle);
      expect(live.semanticLabel, contains('Van'));
      expect(live.communicatesLiveMotion, isTrue);

      expect(delayed.communicatesLiveMotion, isFalse);
      expect(stale.communicatesLiveMotion, isFalse);
      expect(unavailable.communicatesLiveMotion, isFalse);
      expect(stale.semanticLabel, contains('ultima posicao conhecida'));
      expect(unavailable.semanticLabel, contains('sem sinal'));
      expect(stale.badgeLabel, isNotEmpty);
      expect(unavailable.badgeLabel, isNotEmpty);
    });

    test('passenger marker is not a generic pin', () {
      final passenger = vanRidesPassengerMarkerSpec(label: 'Ana Souza');

      expect(passenger.kind, VanRidesMapMarkerKind.passenger);
      expect(passenger.visualLanguage, VanRidesMapMarkerVisualLanguage.person);
      expect(passenger.semanticLabel, contains('Ana Souza'));
      expect(passenger.communicatesLiveMotion, isFalse);
      expect(passenger.badgeLabel, isNotEmpty);
    });

    test('stop markers cover route states with non-color-only badges', () {
      const states = VanRidesStopMarkerState.values;

      for (final state in states) {
        final spec = vanRidesStopMarkerSpec(
          state: state,
          order: 2,
          label: 'Parada 2',
        );

        expect(spec.kind, VanRidesMapMarkerKind.stop);
        expect(spec.visualLanguage, VanRidesMapMarkerVisualLanguage.stop);
        expect(spec.semanticLabel, contains('Parada 2'));
        expect(spec.stateLabel, isNotEmpty);
        expect(spec.badgeLabel, isNotEmpty);
        expect(spec.communicatesLiveMotion, isFalse);
      }
    });

    test('infers stop state from waypoint order and tracking state', () {
      const completed = TripRouteWaypointRecord(
        tipo: 'STOP',
        paradaId: 11,
        passageiroId: 21,
        nome: 'Primeira parada',
        latitude: -29.68,
        longitude: -51.13,
        ordem: 1,
        proximaParada: false,
        paradaDoPassageiro: false,
      );
      const next = TripRouteWaypointRecord(
        tipo: 'STOP',
        paradaId: 12,
        passageiroId: 22,
        nome: 'Proxima parada',
        latitude: -29.681,
        longitude: -51.131,
        ordem: 2,
        proximaParada: true,
        paradaDoPassageiro: false,
      );
      const future = TripRouteWaypointRecord(
        tipo: 'STOP',
        paradaId: 13,
        passageiroId: 23,
        nome: 'Terceira parada',
        latitude: -29.682,
        longitude: -51.132,
        ordem: 3,
        proximaParada: false,
        paradaDoPassageiro: false,
      );
      const state = TripTrackingStateRecord(
        routeSnapshotStatus: TripRouteSnapshotStatus.materialized,
        locationFreshness: TripLocationFreshness.live,
        etaConfidence: TripEtaConfidence.high,
        stopsRemaining: 2,
        stopsCompleted: 1,
        nextStopOrder: 2,
        routeRecomputeReason: TripRouteRecomputeReason.none,
      );

      expect(
        vanRidesStopMarkerStateFor(completed, state),
        VanRidesStopMarkerState.completed,
      );
      expect(
        vanRidesStopMarkerStateFor(next, state),
        VanRidesStopMarkerState.next,
      );
      expect(
        vanRidesStopMarkerStateFor(future, state),
        VanRidesStopMarkerState.future,
      );
    });
  });

  testWidgets(
    'icon factory creates asset descriptors instead of default pins',
    (_) async {
      final icons =
          await const VanRidesMapMarkerIconFactory(pixelRatio: 1).create();

      final descriptors = <BitmapDescriptor>[
        icons.vehicleIconFor(TripLocationFreshness.live),
        icons.vehicleIconFor(TripLocationFreshness.stale),
        icons.passengerIcon(),
        icons.stopIconFor(VanRidesStopMarkerState.completed),
        icons.stopIconFor(VanRidesStopMarkerState.next),
        icons.stopIconFor(VanRidesStopMarkerState.future),
        icons.stopIconFor(VanRidesStopMarkerState.skipped),
        icons.stopIconFor(VanRidesStopMarkerState.noShow),
      ];

      for (final descriptor in descriptors) {
        final payload = descriptor.toJson().toString();

        expect(payload, isNot(contains('defaultMarker')));
        expect(payload, isNot(contains('defaultMarkerWithHue')));
      }
    },
  );
}
