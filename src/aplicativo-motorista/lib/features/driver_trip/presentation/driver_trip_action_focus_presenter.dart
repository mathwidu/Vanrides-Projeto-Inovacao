import '../domain/driver_trip_record.dart';
import '../../tracking/domain/trip_tracking_state_record.dart';

enum DriverTripActionFocusTone { ready, review, degraded }

class DriverTripActionFocusPresentation {
  const DriverTripActionFocusPresentation({
    required this.title,
    required this.actionLabel,
    required this.summary,
    required this.routeLabel,
    required this.signalLabel,
    required this.helper,
    required this.tone,
  });

  final String title;
  final String actionLabel;
  final String summary;
  final String routeLabel;
  final String signalLabel;
  final String helper;
  final DriverTripActionFocusTone tone;
}

DriverTripActionFocusPresentation? presentDriverTripActionFocus(
  DriverTripRecord trip,
) {
  final stop = trip.proximaParada;
  if (stop == null) {
    return null;
  }

  final hasNavigation =
      trip.rotaGeografica?.navegacaoPreferencial?.hasUrl ?? false;
  final signalLabel = _signalLabelFor(trip.trackingState.locationFreshness);
  final routeLabel = _routeLabelFor(_resolveRouteStatus(trip));
  final degradedSignal = _isDegradedSignal(
    trip.trackingState.locationFreshness,
  );
  final actionLabel =
      hasNavigation ? 'Ir para proxima parada' : 'Confirmar embarque';

  return DriverTripActionFocusPresentation(
    title:
        hasNavigation
            ? 'Agora: ir para ${stop.passageiroNome}'
            : 'Agora: confirmar embarque',
    actionLabel: actionLabel,
    summary:
        hasNavigation
            ? '${_stopLabel(stop)} • ${_etaLabel(stop, trip)} • Maps pronto'
            : '${stop.passageiroNome} • ${_stopLabel(stop)} • Sem navegacao pronta',
    routeLabel: routeLabel,
    signalLabel: signalLabel,
    helper:
        degradedSignal
            ? 'Sinal degradado; siga a acao quando estiver seguro.'
            : '$routeLabel, $signalLabel e acao principal alinhados.',
    tone:
        degradedSignal
            ? DriverTripActionFocusTone.degraded
            : hasNavigation
            ? DriverTripActionFocusTone.ready
            : DriverTripActionFocusTone.review,
  );
}

TripRouteSnapshotStatus _resolveRouteStatus(DriverTripRecord trip) {
  final stateStatus = trip.trackingState.routeSnapshotStatus;
  if (!_isUnavailableRoute(stateStatus)) {
    return stateStatus;
  }
  return trip.rotaGeografica?.routeSnapshotStatus ?? stateStatus;
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

String _signalLabelFor(TripLocationFreshness freshness) {
  return switch (freshness) {
    TripLocationFreshness.live => 'Sinal ao vivo',
    TripLocationFreshness.delayed => 'Posicao recente',
    TripLocationFreshness.stale => 'Sinal antigo',
    TripLocationFreshness.paused => 'Sinal pausado',
    TripLocationFreshness.unavailable ||
    TripLocationFreshness.unknown => 'Sem sinal ao vivo',
  };
}

String _etaLabel(DriverTripStopRecord stop, DriverTripRecord trip) {
  final eta = stop.etaMinutos ?? trip.etaMinutosAteProximaParada;
  if (eta == null) {
    return 'ETA em calculo';
  }
  if (eta <= 0) {
    return 'Chegando';
  }
  return eta == 1 ? '1 min' : '$eta min';
}

String _stopLabel(DriverTripStopRecord stop) => 'Parada ${stop.ordem}';

bool _isDegradedSignal(TripLocationFreshness freshness) {
  return freshness == TripLocationFreshness.delayed ||
      freshness == TripLocationFreshness.stale ||
      freshness == TripLocationFreshness.paused ||
      freshness == TripLocationFreshness.unavailable ||
      freshness == TripLocationFreshness.unknown;
}

bool _isUnavailableRoute(TripRouteSnapshotStatus status) {
  return status == TripRouteSnapshotStatus.unavailable ||
      status == TripRouteSnapshotStatus.unknown;
}
