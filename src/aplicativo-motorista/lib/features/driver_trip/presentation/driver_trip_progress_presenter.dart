import 'package:vanrides_frontend_dev/features/driver_trip/domain/driver_trip_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_record.dart';
import 'package:vanrides_frontend_dev/l10n/app_strings.dart';

int resolveDriverStopsRemaining(DriverTripRecord trip) {
  final fromState = trip.trackingState.stopsRemaining;
  if (fromState > 0) {
    return _capToTotal(fromState, trip.totalParadas);
  }

  return trip.paradas
      .where((stop) => stop.status == TripTrackingStopStatus.pending)
      .length;
}

int resolveDriverStopsCompleted(DriverTripRecord trip) {
  final fromState = trip.trackingState.stopsCompleted;
  if (fromState > 0) {
    return _capToTotal(fromState, trip.totalParadas);
  }

  return trip.paradas
      .where((stop) => stop.status != TripTrackingStopStatus.pending)
      .length;
}

String presentDriverProgressLabel(DriverTripRecord trip) {
  return AppStrings.motorista.progressoParadas(
    resolveDriverStopsCompleted(trip),
    trip.totalParadas,
  );
}

String presentDriverRemainingStopsLabel(DriverTripRecord trip) {
  return AppStrings.motorista.paradasRestantes(
    resolveDriverStopsRemaining(trip),
  );
}

int _capToTotal(int value, int total) {
  if (total <= 0) {
    return 0;
  }
  if (value < 0) {
    return 0;
  }
  return value > total ? total : value;
}
