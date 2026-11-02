import '../domain/trip_tracking_record.dart';
import '../domain/trip_tracking_state_record.dart';

enum PassengerTripReadinessTone { reliable, observing, fallback }

class PassengerTripReadinessPresentation {
  const PassengerTripReadinessPresentation({
    required this.title,
    required this.etaLabel,
    required this.routeLabel,
    required this.confidenceLabel,
    required this.helper,
    required this.tone,
  });

  final String title;
  final String etaLabel;
  final String routeLabel;
  final String confidenceLabel;
  final String helper;
  final PassengerTripReadinessTone tone;

  String get summary => '$etaLabel • $routeLabel • $confidenceLabel';
}

PassengerTripReadinessPresentation presentPassengerTripReadiness(
  TripTrackingRecord trip,
) {
  final routeStatus = _resolveRouteStatus(trip);
  final freshness = trip.trackingState.locationFreshness;
  final etaConfidence = trip.trackingState.etaConfidence;
  final reliable =
      trip.status == TripTrackingStatus.inProgress &&
      trip.etaMinutosAtePassageiro != null &&
      routeStatus == TripRouteSnapshotStatus.materialized &&
      freshness == TripLocationFreshness.live &&
      etaConfidence == TripEtaConfidence.high;
  final fallback =
      _isUnavailableFreshness(freshness) ||
      _isUnavailableRoute(routeStatus) ||
      _isUnavailableEta(etaConfidence);

  return PassengerTripReadinessPresentation(
    title: _titleFor(trip, reliable: reliable, fallback: fallback),
    etaLabel: _etaLabelFor(trip, reliable: reliable, fallback: fallback),
    routeLabel: _routeLabelFor(routeStatus),
    confidenceLabel: _confidenceLabelFor(
      reliable: reliable,
      fallback: fallback,
    ),
    helper: _helperFor(reliable: reliable, fallback: fallback),
    tone:
        reliable
            ? PassengerTripReadinessTone.reliable
            : fallback
            ? PassengerTripReadinessTone.fallback
            : PassengerTripReadinessTone.observing,
  );
}

TripRouteSnapshotStatus _resolveRouteStatus(TripTrackingRecord trip) {
  final stateStatus = trip.trackingState.routeSnapshotStatus;
  if (!_isUnavailableRoute(stateStatus)) {
    return stateStatus;
  }
  return trip.rotaGeografica?.routeSnapshotStatus ?? stateStatus;
}

String _titleFor(
  TripTrackingRecord trip, {
  required bool reliable,
  required bool fallback,
}) {
  return switch (trip.status) {
    TripTrackingStatus.planned => 'Viagem em preparo',
    TripTrackingStatus.completed => 'Viagem encerrada',
    TripTrackingStatus.canceled => 'Viagem cancelada',
    TripTrackingStatus.inProgress =>
      fallback
          ? 'Aguardando sinal da van'
          : reliable
          ? 'Van a caminho da sua parada'
          : 'Previsão em observação',
    TripTrackingStatus.unknown => 'Acompanhamento da viagem',
  };
}

String _etaLabelFor(
  TripTrackingRecord trip, {
  required bool reliable,
  required bool fallback,
}) {
  if (trip.status == TripTrackingStatus.planned) {
    return 'ETA após início';
  }
  if (trip.status == TripTrackingStatus.completed) {
    return 'Viagem encerrada';
  }
  if (trip.status == TripTrackingStatus.canceled) {
    return 'Viagem cancelada';
  }
  if (fallback) {
    return 'ETA indisponível';
  }

  final eta = trip.etaMinutosAtePassageiro;
  if (eta == null) {
    return 'ETA em cálculo';
  }
  if (eta <= 0) {
    return reliable ? 'Chegando agora' : 'Chegada em observação';
  }
  if (reliable) {
    return eta == 1 ? '1 min até sua parada' : '$eta min até sua parada';
  }
  return eta == 1 ? '1 min em observação' : '$eta min em observação';
}

String _routeLabelFor(TripRouteSnapshotStatus status) {
  return switch (status) {
    TripRouteSnapshotStatus.materialized => 'Rota validada',
    TripRouteSnapshotStatus.planned => 'Rota em preparo',
    TripRouteSnapshotStatus.stale => 'Rota antiga',
    TripRouteSnapshotStatus.unavailable ||
    TripRouteSnapshotStatus.unknown => 'Sem rota validada',
  };
}

String _confidenceLabelFor({required bool reliable, required bool fallback}) {
  if (reliable) {
    return 'ETA confiável';
  }
  if (fallback) {
    return 'Sem ETA confiável';
  }
  return 'ETA em observação';
}

String _helperFor({required bool reliable, required bool fallback}) {
  if (reliable) {
    return 'Sinal ao vivo, rota validada e ETA alinhados.';
  }
  if (fallback) {
    return 'Acompanhe pelo último estado enquanto o sinal volta.';
  }
  return 'Use como previsão até a próxima atualização.';
}

bool _isUnavailableFreshness(TripLocationFreshness freshness) {
  return freshness == TripLocationFreshness.unavailable ||
      freshness == TripLocationFreshness.unknown;
}

bool _isUnavailableRoute(TripRouteSnapshotStatus status) {
  return status == TripRouteSnapshotStatus.unavailable ||
      status == TripRouteSnapshotStatus.unknown;
}

bool _isUnavailableEta(TripEtaConfidence confidence) {
  return confidence == TripEtaConfidence.unavailable ||
      confidence == TripEtaConfidence.unknown;
}
