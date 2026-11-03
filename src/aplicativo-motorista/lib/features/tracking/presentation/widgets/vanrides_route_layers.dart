import 'package:flutter/material.dart';
import 'package:google_maps_flutter/google_maps_flutter.dart';

import '../../domain/trip_route_geometry_record.dart';
import '../../domain/trip_tracking_state_record.dart';
import '../trip_live_map_state.dart';
import 'vanrides_map_tokens.dart';

enum VanRidesRouteLayerType {
  planned,
  remaining,
  completed,
  degraded,
  unavailable,
}

class VanRidesRouteLayerSpec {
  const VanRidesRouteLayerSpec({
    required this.type,
    required this.points,
    required this.color,
    required this.width,
    required this.patterns,
    required this.zIndex,
    required this.geodesic,
  });

  final VanRidesRouteLayerType type;
  final List<LatLng> points;
  final Color color;
  final int width;
  final List<PatternItem> patterns;
  final int zIndex;
  final bool geodesic;

  Polyline toPolyline() {
    return Polyline(
      polylineId: PolylineId('trip-route-${type.name}'),
      points: points,
      color: color,
      width: width,
      patterns: patterns,
      zIndex: zIndex,
      geodesic: geodesic,
    );
  }
}

List<VanRidesRouteLayerSpec> buildVanRidesRouteLayers({
  required TripRouteGeometryRecord? routeGeometry,
  required TripTrackingStateRecord trackingState,
}) {
  final routePoints = _routePointsFor(routeGeometry);
  if (routeGeometry == null || routePoints.length < 2) {
    return const [];
  }

  final snapshotStatus = _effectiveSnapshotStatus(routeGeometry, trackingState);
  if (snapshotStatus == TripRouteSnapshotStatus.unavailable ||
      snapshotStatus == TripRouteSnapshotStatus.unknown) {
    return const [];
  }

  final geodesic = !tripRouteUsesRealRoadRouting(routeGeometry);
  if (snapshotStatus == TripRouteSnapshotStatus.stale) {
    return [
      VanRidesRouteLayerSpec(
        type: VanRidesRouteLayerType.degraded,
        points: routePoints,
        color: VanRidesMapTokens.amberWarning,
        width: 5,
        patterns: [PatternItem.dash(22), PatternItem.gap(14)],
        zIndex: 3,
        geodesic: geodesic,
      ),
    ];
  }

  final splitIndex =
      trackingState.stopsRemaining <= 0 && trackingState.stopsCompleted > 0
          ? routePoints.length - 1
          : _completedRouteSplitIndex(routeGeometry, trackingState);
  final completedPoints =
      splitIndex == null || splitIndex <= 0
          ? const <LatLng>[]
          : routePoints.sublist(0, splitIndex + 1);
  final remainingPoints =
      splitIndex == null
          ? routePoints
          : splitIndex >= routePoints.length - 1
          ? const <LatLng>[]
          : routePoints.sublist(splitIndex);

  return [
    VanRidesRouteLayerSpec(
      type: VanRidesRouteLayerType.planned,
      points: routePoints,
      color: VanRidesMapTokens.border.withValues(alpha: 0.58),
      width: 3,
      patterns: [PatternItem.dash(24), PatternItem.gap(16)],
      zIndex: 1,
      geodesic: geodesic,
    ),
    if (completedPoints.length > 1)
      VanRidesRouteLayerSpec(
        type: VanRidesRouteLayerType.completed,
        points: completedPoints,
        color: VanRidesMapTokens.greenAction.withValues(alpha: 0.72),
        width: 5,
        patterns: const [],
        zIndex: 2,
        geodesic: geodesic,
      ),
    if (remainingPoints.length > 1)
      VanRidesRouteLayerSpec(
        type: VanRidesRouteLayerType.remaining,
        points: remainingPoints,
        color: VanRidesMapTokens.blueTracking,
        width: 6,
        patterns: const [],
        zIndex: 4,
        geodesic: geodesic,
      ),
  ];
}

Set<Polyline> buildVanRidesRoutePolylines(List<VanRidesRouteLayerSpec> layers) {
  return layers
      .where((layer) => layer.points.length > 1)
      .map((layer) => layer.toPolyline())
      .toSet();
}

TripRouteSnapshotStatus _effectiveSnapshotStatus(
  TripRouteGeometryRecord routeGeometry,
  TripTrackingStateRecord trackingState,
) {
  if (trackingState.routeSnapshotStatus !=
          TripRouteSnapshotStatus.unavailable &&
      trackingState.routeSnapshotStatus != TripRouteSnapshotStatus.unknown) {
    return trackingState.routeSnapshotStatus;
  }
  return routeGeometry.routeSnapshotStatus;
}

List<LatLng> _routePointsFor(TripRouteGeometryRecord? routeGeometry) {
  if (routeGeometry == null) {
    return const [];
  }
  if (routeGeometry.hasPolyline) {
    return decodeTripPolyline(routeGeometry.polyline);
  }
  return routeGeometry.orderedStops
      .where((waypoint) => waypoint.hasCoordinates)
      .map((waypoint) => LatLng(waypoint.latitude!, waypoint.longitude!))
      .toList(growable: false);
}

int? _completedRouteSplitIndex(
  TripRouteGeometryRecord routeGeometry,
  TripTrackingStateRecord trackingState,
) {
  if (trackingState.stopsCompleted <= 0) {
    return null;
  }
  TripRouteWaypointRecord? completedStop;
  for (final waypoint in routeGeometry.orderedStops) {
    if (waypoint.ordem == trackingState.stopsCompleted) {
      completedStop = waypoint;
      break;
    }
  }
  if (completedStop == null || !completedStop.hasCoordinates) {
    return null;
  }
  return _nearestPointIndex(
    _routePointsFor(routeGeometry),
    LatLng(completedStop.latitude!, completedStop.longitude!),
  );
}

int? _nearestPointIndex(List<LatLng> routePoints, LatLng target) {
  if (routePoints.isEmpty) {
    return null;
  }
  var bestIndex = 0;
  var bestDistance = double.infinity;
  for (var index = 0; index < routePoints.length; index += 1) {
    final point = routePoints[index];
    final distance =
        (point.latitude - target.latitude).abs() +
        (point.longitude - target.longitude).abs();
    if (distance < bestDistance) {
      bestDistance = distance;
      bestIndex = index;
    }
  }
  return bestIndex;
}
