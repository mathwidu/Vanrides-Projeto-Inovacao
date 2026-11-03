enum TripRouteSnapshotStatus {
  materialized,
  planned,
  stale,
  unavailable,
  unknown,
}

enum TripLocationFreshness {
  live,
  delayed,
  stale,
  paused,
  unavailable,
  unknown,
}

enum TripEtaConfidence { high, medium, low, unavailable, unknown }

enum TripRouteRecomputeReason {
  none,
  tripStarted,
  routeReordered,
  driverChanged,
  destinationChanged,
  majorDeviation,
  manualRefresh,
  providerFallback,
  unknown,
}

class TripTrackingStateRecord {
  const TripTrackingStateRecord({
    required this.routeSnapshotStatus,
    required this.locationFreshness,
    required this.etaConfidence,
    required this.stopsRemaining,
    required this.stopsCompleted,
    required this.routeRecomputeReason,
    this.stopsBeforePassenger,
    this.nextStopId,
    this.nextStopOrder,
    this.driverLocationUpdatedAt,
    this.routeRecomputedAt,
  });

  static const unavailable = TripTrackingStateRecord(
    routeSnapshotStatus: TripRouteSnapshotStatus.unavailable,
    locationFreshness: TripLocationFreshness.unavailable,
    etaConfidence: TripEtaConfidence.unavailable,
    stopsRemaining: 0,
    stopsCompleted: 0,
    routeRecomputeReason: TripRouteRecomputeReason.none,
  );

  final TripRouteSnapshotStatus routeSnapshotStatus;
  final TripLocationFreshness locationFreshness;
  final TripEtaConfidence etaConfidence;
  final int stopsRemaining;
  final int stopsCompleted;
  final int? stopsBeforePassenger;
  final int? nextStopId;
  final int? nextStopOrder;
  final DateTime? driverLocationUpdatedAt;
  final TripRouteRecomputeReason routeRecomputeReason;
  final DateTime? routeRecomputedAt;

  factory TripTrackingStateRecord.fromJson(Map<String, dynamic> json) {
    return TripTrackingStateRecord(
      routeSnapshotStatus: parseTripRouteSnapshotStatus(
        json['routeSnapshotStatus'] as String?,
      ),
      locationFreshness: parseTripLocationFreshness(
        json['locationFreshness'] as String?,
      ),
      etaConfidence: parseTripEtaConfidence(json['etaConfidence'] as String?),
      stopsRemaining: (json['stopsRemaining'] as num?)?.toInt() ?? 0,
      stopsCompleted: (json['stopsCompleted'] as num?)?.toInt() ?? 0,
      stopsBeforePassenger: (json['stopsBeforePassenger'] as num?)?.toInt(),
      nextStopId: (json['nextStopId'] as num?)?.toInt(),
      nextStopOrder: (json['nextStopOrder'] as num?)?.toInt(),
      driverLocationUpdatedAt: parseTripOptionalDateTime(
        json['driverLocationUpdatedAt'],
      ),
      routeRecomputeReason: parseTripRouteRecomputeReason(
        json['routeRecomputeReason'] as String?,
      ),
      routeRecomputedAt: parseTripOptionalDateTime(json['routeRecomputedAt']),
    );
  }
}

TripTrackingStateRecord parseTripTrackingState(Object? rawValue) {
  if (rawValue is Map<String, dynamic>) {
    return TripTrackingStateRecord.fromJson(rawValue);
  }
  return TripTrackingStateRecord.unavailable;
}

TripTrackingStateRecord? parseOptionalTripTrackingState(Object? rawValue) {
  if (rawValue is Map<String, dynamic>) {
    return TripTrackingStateRecord.fromJson(rawValue);
  }
  return null;
}

TripRouteSnapshotStatus parseTripRouteSnapshotStatus(String? value) {
  switch ((value ?? '').trim().toUpperCase()) {
    case '':
      return TripRouteSnapshotStatus.unavailable;
    case 'MATERIALIZED':
      return TripRouteSnapshotStatus.materialized;
    case 'PLANNED':
      return TripRouteSnapshotStatus.planned;
    case 'STALE':
      return TripRouteSnapshotStatus.stale;
    case 'UNAVAILABLE':
      return TripRouteSnapshotStatus.unavailable;
    default:
      return TripRouteSnapshotStatus.unknown;
  }
}

TripLocationFreshness parseTripLocationFreshness(String? value) {
  switch ((value ?? '').trim().toUpperCase()) {
    case '':
      return TripLocationFreshness.unavailable;
    case 'LIVE':
      return TripLocationFreshness.live;
    case 'DELAYED':
      return TripLocationFreshness.delayed;
    case 'STALE':
      return TripLocationFreshness.stale;
    case 'PAUSED':
      return TripLocationFreshness.paused;
    case 'UNAVAILABLE':
      return TripLocationFreshness.unavailable;
    default:
      return TripLocationFreshness.unknown;
  }
}

TripEtaConfidence parseTripEtaConfidence(String? value) {
  switch ((value ?? '').trim().toUpperCase()) {
    case '':
      return TripEtaConfidence.unavailable;
    case 'HIGH':
      return TripEtaConfidence.high;
    case 'MEDIUM':
      return TripEtaConfidence.medium;
    case 'LOW':
      return TripEtaConfidence.low;
    case 'UNAVAILABLE':
      return TripEtaConfidence.unavailable;
    default:
      return TripEtaConfidence.unknown;
  }
}

TripRouteRecomputeReason parseTripRouteRecomputeReason(String? value) {
  switch ((value ?? '').trim().toUpperCase()) {
    case '':
    case 'NONE':
      return TripRouteRecomputeReason.none;
    case 'TRIP_STARTED':
      return TripRouteRecomputeReason.tripStarted;
    case 'ROUTE_REORDERED':
      return TripRouteRecomputeReason.routeReordered;
    case 'DRIVER_CHANGED':
      return TripRouteRecomputeReason.driverChanged;
    case 'DESTINATION_CHANGED':
      return TripRouteRecomputeReason.destinationChanged;
    case 'MAJOR_DEVIATION':
      return TripRouteRecomputeReason.majorDeviation;
    case 'MANUAL_REFRESH':
      return TripRouteRecomputeReason.manualRefresh;
    case 'PROVIDER_FALLBACK':
      return TripRouteRecomputeReason.providerFallback;
    default:
      return TripRouteRecomputeReason.unknown;
  }
}

DateTime? parseTripOptionalDateTime(Object? rawValue) {
  if (rawValue is! String || rawValue.trim().isEmpty) {
    return null;
  }
  return DateTime.tryParse(rawValue.trim());
}
