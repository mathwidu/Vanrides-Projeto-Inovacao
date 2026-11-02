import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_state_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/trip_offline_continuity_presenter.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/trip_tracking_presenter.dart';

void main() {
  test(
    'keeps passenger last useful state with timestamp and refresh action',
    () {
      final presentation = presentTripOfflineContinuity(
        trackingState: _state(
          freshness: TripLocationFreshness.stale,
          driverLocationUpdatedAt: DateTime(2026, 6, 15, 7, 8),
        ),
        etaUpdatedAt: DateTime(2026, 6, 15, 7, 10),
        role: TripSignalRole.passenger,
      );

      expect(presentation.visible, isTrue);
      expect(presentation.title, 'Último estado útil às 07:08');
      expect(
        presentation.helper,
        'Mantemos a última posição conhecida enquanto o sinal volta.',
      );
      expect(presentation.actionLabel, 'Atualizar sinal');
    },
  );

  test('guides driver without promising live data', () {
    final presentation = presentTripOfflineContinuity(
      trackingState: _state(
        freshness: TripLocationFreshness.delayed,
        driverLocationUpdatedAt: DateTime(2026, 6, 15, 7, 6),
      ),
      etaUpdatedAt: null,
      role: TripSignalRole.driver,
    );

    expect(presentation.visible, isTrue);
    expect(presentation.title, 'Último estado útil às 07:06');
    expect(
      presentation.helper,
      'Passageiros veem o último estado da viagem; verifique conexão e localização quando estiver seguro.',
    );
    expect(presentation.actionLabel, 'Verificar sinal');
  });

  test('hides continuity copy when signal is live', () {
    final presentation = presentTripOfflineContinuity(
      trackingState: _state(
        freshness: TripLocationFreshness.live,
        driverLocationUpdatedAt: DateTime(2026, 6, 15, 7, 12),
      ),
      etaUpdatedAt: DateTime(2026, 6, 15, 7, 12),
      role: TripSignalRole.guardian,
    );

    expect(presentation.visible, isFalse);
  });
}

TripTrackingStateRecord _state({
  required TripLocationFreshness freshness,
  DateTime? driverLocationUpdatedAt,
}) {
  return TripTrackingStateRecord(
    routeSnapshotStatus: TripRouteSnapshotStatus.materialized,
    locationFreshness: freshness,
    etaConfidence: TripEtaConfidence.medium,
    stopsRemaining: 3,
    stopsCompleted: 1,
    stopsBeforePassenger: 2,
    routeRecomputeReason: TripRouteRecomputeReason.none,
    driverLocationUpdatedAt: driverLocationUpdatedAt,
  );
}
