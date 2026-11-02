import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/domain/driver_trip_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_live_location_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_route_event.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_state_record.dart';

void main() {
  test(
    'parseia trackingState e auditoria de rota no tracking do passageiro',
    () {
      final trip = TripTrackingRecord.fromJson({
        'id': 30,
        'rotaNome': 'Rota Escolar',
        'dataOperacao': '2026-03-20',
        'status': 'IN_PROGRESS',
        'rotaVersao': 3,
        'etaMinutosAtePassageiro': 12,
        'trackingState': _trackingStateJson(
          routeSnapshotStatus: 'MATERIALIZED',
          locationFreshness: 'LIVE',
          etaConfidence: 'HIGH',
        ),
        'rotaGeografica': _routeGeometryJson(
          routeSnapshotStatus: 'MATERIALIZED',
          routeRecomputeReason: 'TRIP_STARTED',
        ),
      });

      expect(
        trip.trackingState.routeSnapshotStatus,
        TripRouteSnapshotStatus.materialized,
      );
      expect(trip.trackingState.locationFreshness, TripLocationFreshness.live);
      expect(trip.trackingState.etaConfidence, TripEtaConfidence.high);
      expect(trip.trackingState.stopsBeforePassenger, 1);
      expect(
        trip.trackingState.driverLocationUpdatedAt,
        DateTime(2026, 3, 20, 7, 35),
      );
      expect(trip.rotaGeografica?.snapshotKey, 'company:1:viagem:30:rota:3');
      expect(trip.rotaGeografica?.routeInputHash, 'abc123');
      expect(
        trip.rotaGeografica?.routeRecomputeReason,
        TripRouteRecomputeReason.tripStarted,
      );
      expect(
        trip.rotaGeografica?.routeRecomputeAllowedAfter,
        DateTime(2026, 3, 20, 7, 37),
      );
    },
  );

  test('payload legado continua seguro e enum desconhecido cai em unknown', () {
    final trip = TripTrackingRecord.fromJson({
      'id': 31,
      'rotaNome': 'Rota Legada',
      'dataOperacao': '2026-03-20',
      'status': 'IN_PROGRESS',
      'rotaVersao': 1,
      'trackingState': _trackingStateJson(
        routeSnapshotStatus: 'SURPRISE',
        locationFreshness: 'MISSING',
        etaConfidence: 'ALIEN',
      ),
      'rotaGeografica': _routeGeometryJson(
        routeSnapshotStatus: 'CACHED_BUT_WEIRD',
        routeRecomputeReason: 'MAGIC',
      ),
    });
    final legacyTrip = TripTrackingRecord.fromJson({
      'id': 32,
      'rotaNome': 'Rota Antiga',
      'dataOperacao': '2026-03-20',
      'status': 'PLANNED',
      'rotaVersao': 1,
    });

    expect(
      trip.trackingState.routeSnapshotStatus,
      TripRouteSnapshotStatus.unknown,
    );
    expect(trip.trackingState.locationFreshness, TripLocationFreshness.unknown);
    expect(trip.trackingState.etaConfidence, TripEtaConfidence.unknown);
    expect(
      trip.rotaGeografica?.routeSnapshotStatus,
      TripRouteSnapshotStatus.unknown,
    );
    expect(
      trip.rotaGeografica?.routeRecomputeReason,
      TripRouteRecomputeReason.unknown,
    );
    expect(legacyTrip.trackingState, TripTrackingStateRecord.unavailable);
  });

  test('motorista e evento realtime preservam trackingState', () {
    final trip = DriverTripRecord.fromJson({
      'id': 40,
      'rotaBaseId': 4,
      'rotaNome': 'Rota Centro',
      'dataOperacao': '2026-03-20',
      'status': 'IN_PROGRESS',
      'rotaVersao': 2,
      'paradas': [],
      'trackingState': _trackingStateJson(
        routeSnapshotStatus: 'PLANNED',
        locationFreshness: 'DELAYED',
        etaConfidence: 'MEDIUM',
      ),
    });
    final event = TripRouteEvent.fromJson({
      'tipo': 'VIAGEM_ATUALIZADA',
      'viagemId': 40,
      'motoristaId': 7,
      'dataOperacao': '2026-03-20',
      'status': 'IN_PROGRESS',
      'rotaVersao': 3,
      'trackingState': _trackingStateJson(
        routeSnapshotStatus: 'STALE',
        locationFreshness: 'STALE',
        etaConfidence: 'LOW',
      ),
      'avisosOperacionais': [
        {
          'tipo': 'DRIVER_APPROACHING',
          'severidade': 'URGENT',
          'titulo': 'Van chegando',
          'mensagem': 'Voce e o proximo da fila.',
          'passageiroId': 21,
          'paradaId': 90,
          'paradaOrdem': 3,
        },
      ],
    });

    final updatedTrip = trip.copyWithRealtime(event);
    final passengerRecord = updatedTrip.toTrackingRecord();

    expect(trip.trackingState.locationFreshness, TripLocationFreshness.delayed);
    expect(
      event.trackingState?.routeSnapshotStatus,
      TripRouteSnapshotStatus.stale,
    );
    expect(updatedTrip.trackingState.etaConfidence, TripEtaConfidence.low);
    expect(
      passengerRecord.trackingState.locationFreshness,
      TripLocationFreshness.stale,
    );
    expect(event.avisosOperacionais, hasLength(1));
    expect(
      event.avisosOperacionais.single.tipo,
      TripOperationalAlertType.driverApproaching,
    );
    expect(
      event.avisosOperacionais.single.severidade,
      TripOperationalAlertSeverity.urgent,
    );
    expect(event.avisosOperacionais.single.titulo, 'Van chegando');
    expect(event.avisosOperacionais.single.passageiroId, 21);
  });

  test('localizacao ao vivo parseia updatedAt e freshness', () {
    final location = TripLiveLocationRecord.fromJson({
      'latitude': -29.68,
      'longitude': -51.13,
      'updatedAt': '2026-03-20T07:35:00',
      'freshness': 'PAUSED',
    });
    final legacyLocation = TripLiveLocationRecord.fromJson({
      'latitude': -29.68,
      'longitude': -51.13,
    });

    expect(location.updatedAt, DateTime(2026, 3, 20, 7, 35));
    expect(location.freshness, TripLocationFreshness.paused);
    expect(legacyLocation.freshness, TripLocationFreshness.unavailable);
  });
}

Map<String, dynamic> _trackingStateJson({
  required String routeSnapshotStatus,
  required String locationFreshness,
  required String etaConfidence,
}) {
  return {
    'routeSnapshotStatus': routeSnapshotStatus,
    'locationFreshness': locationFreshness,
    'etaConfidence': etaConfidence,
    'stopsRemaining': 4,
    'stopsCompleted': 2,
    'stopsBeforePassenger': 1,
    'nextStopId': 90,
    'nextStopOrder': 3,
    'driverLocationUpdatedAt': '2026-03-20T07:35:00',
    'routeRecomputeReason': 'TRIP_STARTED',
    'routeRecomputedAt': '2026-03-20T07:33:00',
  };
}

Map<String, dynamic> _routeGeometryJson({
  required String routeSnapshotStatus,
  required String routeRecomputeReason,
}) {
  return {
    'provider': 'GOOGLE_DIRECTIONS_V1',
    'polyline': '',
    'waypoints': [],
    'legs': [],
    'snapshotKey': 'company:1:viagem:30:rota:3',
    'routeInputHash': 'abc123',
    'routeSnapshotStatus': routeSnapshotStatus,
    'routeRecomputeReason': routeRecomputeReason,
    'routeRecomputeAllowedAfter': '2026-03-20T07:37:00',
  };
}
