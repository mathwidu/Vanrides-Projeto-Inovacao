import 'package:flutter/material.dart';

import '../../domain/trip_route_geometry_record.dart';
import '../../domain/trip_tracking_state_record.dart';
import 'vanrides_map_tokens.dart';

enum VanRidesMapMarkerKind { vehicle, passenger, stop }

enum VanRidesMapMarkerVisualLanguage { vehicle, person, stop }

enum VanRidesStopMarkerState { completed, next, future, skipped, noShow }

class VanRidesMapMarkerSpec {
  const VanRidesMapMarkerSpec({
    required this.kind,
    required this.visualLanguage,
    required this.semanticLabel,
    required this.stateLabel,
    required this.badgeLabel,
    required this.communicatesLiveMotion,
    required this.accentColor,
    required this.fillColor,
    required this.strokeColor,
  });

  final VanRidesMapMarkerKind kind;
  final VanRidesMapMarkerVisualLanguage visualLanguage;
  final String semanticLabel;
  final String stateLabel;
  final String badgeLabel;
  final bool communicatesLiveMotion;
  final Color accentColor;
  final Color fillColor;
  final Color strokeColor;
}

VanRidesMapMarkerSpec vanRidesVehicleMarkerSpec(
  TripLocationFreshness freshness,
) {
  switch (freshness) {
    case TripLocationFreshness.live:
      return const VanRidesMapMarkerSpec(
        kind: VanRidesMapMarkerKind.vehicle,
        visualLanguage: VanRidesMapMarkerVisualLanguage.vehicle,
        semanticLabel: 'Van ao vivo',
        stateLabel: 'ao vivo',
        badgeLabel: 'LIVE',
        communicatesLiveMotion: true,
        accentColor: VanRidesMapTokens.blueTracking,
        fillColor: VanRidesMapTokens.backgroundDeep,
        strokeColor: VanRidesMapTokens.blueTracking,
      );
    case TripLocationFreshness.delayed:
      return const VanRidesMapMarkerSpec(
        kind: VanRidesMapMarkerKind.vehicle,
        visualLanguage: VanRidesMapMarkerVisualLanguage.vehicle,
        semanticLabel: 'Van com sinal atrasado',
        stateLabel: 'sinal atrasado',
        badgeLabel: '~',
        communicatesLiveMotion: false,
        accentColor: VanRidesMapTokens.amberWarning,
        fillColor: VanRidesMapTokens.backgroundDeep,
        strokeColor: VanRidesMapTokens.amberWarning,
      );
    case TripLocationFreshness.stale:
      return const VanRidesMapMarkerSpec(
        kind: VanRidesMapMarkerKind.vehicle,
        visualLanguage: VanRidesMapMarkerVisualLanguage.vehicle,
        semanticLabel: 'Van com ultima posicao conhecida',
        stateLabel: 'sinal antigo',
        badgeLabel: '!',
        communicatesLiveMotion: false,
        accentColor: VanRidesMapTokens.amberWarning,
        fillColor: VanRidesMapTokens.surface,
        strokeColor: VanRidesMapTokens.amberWarning,
      );
    case TripLocationFreshness.paused:
      return const VanRidesMapMarkerSpec(
        kind: VanRidesMapMarkerKind.vehicle,
        visualLanguage: VanRidesMapMarkerVisualLanguage.vehicle,
        semanticLabel: 'Van com compartilhamento pausado',
        stateLabel: 'pausado',
        badgeLabel: 'II',
        communicatesLiveMotion: false,
        accentColor: VanRidesMapTokens.textMuted,
        fillColor: VanRidesMapTokens.surface,
        strokeColor: VanRidesMapTokens.textMuted,
      );
    case TripLocationFreshness.unavailable:
    case TripLocationFreshness.unknown:
      return const VanRidesMapMarkerSpec(
        kind: VanRidesMapMarkerKind.vehicle,
        visualLanguage: VanRidesMapMarkerVisualLanguage.vehicle,
        semanticLabel: 'Van sem sinal confiavel',
        stateLabel: 'sem sinal',
        badgeLabel: '-',
        communicatesLiveMotion: false,
        accentColor: VanRidesMapTokens.redDanger,
        fillColor: VanRidesMapTokens.surface,
        strokeColor: VanRidesMapTokens.redDanger,
      );
  }
}

VanRidesMapMarkerSpec vanRidesPassengerMarkerSpec({
  required String label,
  bool selected = false,
}) {
  final cleanLabel = label.trim().isEmpty ? 'passageiro' : label.trim();
  return VanRidesMapMarkerSpec(
    kind: VanRidesMapMarkerKind.passenger,
    visualLanguage: VanRidesMapMarkerVisualLanguage.person,
    semanticLabel: 'Passageiro $cleanLabel',
    stateLabel: selected ? 'sua parada selecionada' : 'sua parada',
    badgeLabel: 'P',
    communicatesLiveMotion: false,
    accentColor:
        selected
            ? VanRidesMapTokens.blueTracking
            : VanRidesMapTokens.greenAction,
    fillColor: VanRidesMapTokens.backgroundDeep,
    strokeColor:
        selected
            ? VanRidesMapTokens.blueTracking
            : VanRidesMapTokens.greenAction,
  );
}

VanRidesMapMarkerSpec vanRidesStopMarkerSpec({
  required VanRidesStopMarkerState state,
  required int? order,
  required String label,
}) {
  final cleanLabel = label.trim().isEmpty ? 'parada' : label.trim();
  final orderLabel = order == null ? '' : '$order';

  switch (state) {
    case VanRidesStopMarkerState.completed:
      return VanRidesMapMarkerSpec(
        kind: VanRidesMapMarkerKind.stop,
        visualLanguage: VanRidesMapMarkerVisualLanguage.stop,
        semanticLabel: '$cleanLabel concluida',
        stateLabel: 'concluida',
        badgeLabel: 'OK',
        communicatesLiveMotion: false,
        accentColor: VanRidesMapTokens.greenAction,
        fillColor: VanRidesMapTokens.surface,
        strokeColor: VanRidesMapTokens.greenAction,
      );
    case VanRidesStopMarkerState.next:
      return VanRidesMapMarkerSpec(
        kind: VanRidesMapMarkerKind.stop,
        visualLanguage: VanRidesMapMarkerVisualLanguage.stop,
        semanticLabel: '$cleanLabel proxima',
        stateLabel: 'proxima',
        badgeLabel: orderLabel.isEmpty ? 'N' : orderLabel,
        communicatesLiveMotion: false,
        accentColor: VanRidesMapTokens.amberWarning,
        fillColor: VanRidesMapTokens.backgroundDeep,
        strokeColor: VanRidesMapTokens.amberWarning,
      );
    case VanRidesStopMarkerState.future:
      return VanRidesMapMarkerSpec(
        kind: VanRidesMapMarkerKind.stop,
        visualLanguage: VanRidesMapMarkerVisualLanguage.stop,
        semanticLabel: '$cleanLabel futura',
        stateLabel: 'futura',
        badgeLabel: orderLabel.isEmpty ? 'F' : orderLabel,
        communicatesLiveMotion: false,
        accentColor: VanRidesMapTokens.blueTracking,
        fillColor: VanRidesMapTokens.surface,
        strokeColor: VanRidesMapTokens.border,
      );
    case VanRidesStopMarkerState.skipped:
      return VanRidesMapMarkerSpec(
        kind: VanRidesMapMarkerKind.stop,
        visualLanguage: VanRidesMapMarkerVisualLanguage.stop,
        semanticLabel: '$cleanLabel pulada',
        stateLabel: 'pulada',
        badgeLabel: 'SK',
        communicatesLiveMotion: false,
        accentColor: VanRidesMapTokens.textMuted,
        fillColor: VanRidesMapTokens.surface,
        strokeColor: VanRidesMapTokens.textMuted,
      );
    case VanRidesStopMarkerState.noShow:
      return VanRidesMapMarkerSpec(
        kind: VanRidesMapMarkerKind.stop,
        visualLanguage: VanRidesMapMarkerVisualLanguage.stop,
        semanticLabel: '$cleanLabel passageiro ausente',
        stateLabel: 'passageiro ausente',
        badgeLabel: 'NO',
        communicatesLiveMotion: false,
        accentColor: VanRidesMapTokens.redDanger,
        fillColor: VanRidesMapTokens.surface,
        strokeColor: VanRidesMapTokens.redDanger,
      );
  }
}

VanRidesStopMarkerState vanRidesStopMarkerStateFor(
  TripRouteWaypointRecord waypoint,
  TripTrackingStateRecord trackingState,
) {
  final order = waypoint.ordem;
  if (order != null &&
      trackingState.stopsCompleted > 0 &&
      order <= trackingState.stopsCompleted) {
    return VanRidesStopMarkerState.completed;
  }
  if (trackingState.nextStopOrder != null &&
      order == trackingState.nextStopOrder) {
    return VanRidesStopMarkerState.next;
  }
  if (trackingState.nextStopId != null &&
      waypoint.paradaId == trackingState.nextStopId) {
    return VanRidesStopMarkerState.next;
  }
  if (waypoint.proximaParada) {
    return VanRidesStopMarkerState.next;
  }
  return VanRidesStopMarkerState.future;
}
