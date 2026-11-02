import '../domain/trip_tracking_state_record.dart';
import 'trip_tracking_presenter.dart';

class TripOfflineContinuityPresentation {
  const TripOfflineContinuityPresentation({
    required this.visible,
    required this.title,
    required this.helper,
    required this.actionLabel,
  });

  const TripOfflineContinuityPresentation.hidden()
    : visible = false,
      title = '',
      helper = '',
      actionLabel = '';

  final bool visible;
  final String title;
  final String helper;
  final String actionLabel;
}

TripOfflineContinuityPresentation presentTripOfflineContinuity({
  required TripTrackingStateRecord trackingState,
  DateTime? etaUpdatedAt,
  TripSignalRole role = TripSignalRole.passenger,
}) {
  if (!_shouldShowContinuity(trackingState)) {
    return const TripOfflineContinuityPresentation.hidden();
  }

  final lastUsefulUpdatedAt =
      trackingState.driverLocationUpdatedAt ??
      etaUpdatedAt ??
      trackingState.routeRecomputedAt;
  if (lastUsefulUpdatedAt == null) {
    return const TripOfflineContinuityPresentation.hidden();
  }

  return TripOfflineContinuityPresentation(
    visible: true,
    title: 'Último estado útil às ${_formatTime(lastUsefulUpdatedAt)}',
    helper: _helperFor(role),
    actionLabel: _actionLabelFor(role),
  );
}

bool _shouldShowContinuity(TripTrackingStateRecord trackingState) {
  final locationNeedsContinuity =
      trackingState.locationFreshness != TripLocationFreshness.live;
  final routeNeedsContinuity = switch (trackingState.routeSnapshotStatus) {
    TripRouteSnapshotStatus.stale ||
    TripRouteSnapshotStatus.unavailable ||
    TripRouteSnapshotStatus.unknown => true,
    TripRouteSnapshotStatus.materialized ||
    TripRouteSnapshotStatus.planned => false,
  };
  return locationNeedsContinuity || routeNeedsContinuity;
}

String _helperFor(TripSignalRole role) {
  return switch (role) {
    TripSignalRole.passenger =>
      'Mantemos a última posição conhecida enquanto o sinal volta.',
    TripSignalRole.guardian =>
      'A família continua vendo o último estado da viagem enquanto o sinal volta.',
    TripSignalRole.driver =>
      'Passageiros veem o último estado da viagem; verifique conexão e localização quando estiver seguro.',
  };
}

String _actionLabelFor(TripSignalRole role) {
  return switch (role) {
    TripSignalRole.passenger || TripSignalRole.guardian => 'Atualizar sinal',
    TripSignalRole.driver => 'Verificar sinal',
  };
}

String _formatTime(DateTime value) {
  final hour = value.hour.toString().padLeft(2, '0');
  final minute = value.minute.toString().padLeft(2, '0');
  return '$hour:$minute';
}
