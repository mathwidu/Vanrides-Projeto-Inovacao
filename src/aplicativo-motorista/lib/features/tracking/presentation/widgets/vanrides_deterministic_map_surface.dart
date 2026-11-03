import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:google_maps_flutter/google_maps_flutter.dart';

import '../../domain/trip_live_location_record.dart';
import '../../domain/trip_route_geometry_record.dart';
import '../../domain/trip_tracking_state_record.dart';
import '../trip_live_map_state.dart';
import 'vanrides_map_marker_specs.dart';
import 'vanrides_map_tokens.dart';
import 'vanrides_route_layers.dart';

class VanRidesDeterministicMapSurface extends StatelessWidget {
  const VanRidesDeterministicMapSurface({
    super.key,
    required this.routeLayers,
    required this.routeGeometry,
    required this.location,
    required this.focusMode,
    required this.trackingState,
  });

  final List<VanRidesRouteLayerSpec> routeLayers;
  final TripRouteGeometryRecord? routeGeometry;
  final TripLiveLocationRecord? location;
  final TripLiveMapFocusMode focusMode;
  final TripTrackingStateRecord trackingState;

  @override
  Widget build(BuildContext context) {
    final points = collectTripLiveMapPoints(
      location: location,
      routeGeometry: routeGeometry,
      focusMode: focusMode,
    );

    return DecoratedBox(
      key: const Key('vanrides.map.deterministic.surface'),
      decoration: const BoxDecoration(color: VanRidesMapTokens.mapBase),
      child: LayoutBuilder(
        builder: (context, constraints) {
          final size = Size(constraints.maxWidth, constraints.maxHeight);
          final projector = _MapProjector(points: points, size: size);

          return Stack(
            fit: StackFit.expand,
            children: [
              CustomPaint(
                painter: _VanRidesDeterministicMapPainter(
                  routeLayers: routeLayers,
                  projector: projector,
                ),
              ),
              for (final marker in _markers(projector))
                Positioned(
                  left: marker.offset.dx - marker.size.width / 2,
                  top: marker.offset.dy - marker.size.height / 2,
                  width: marker.size.width,
                  height: marker.size.height,
                  child: marker.child,
                ),
            ],
          );
        },
      ),
    );
  }

  List<_ProjectedMarker> _markers(_MapProjector projector) {
    final markers = <_ProjectedMarker>[];
    final liveLocation = location;
    if (liveLocation != null) {
      markers.add(
        _ProjectedMarker(
          offset: projector.project(
            LatLng(liveLocation.latitude, liveLocation.longitude),
          ),
          size: const Size(72, 72),
          child: _VehicleMarker(
            freshness: _effectiveLocationFreshness(liveLocation),
          ),
        ),
      );
    }

    final geometry = routeGeometry;
    if (geometry == null) {
      return markers;
    }

    for (final waypoint in visibleTripRouteWaypoints(geometry, focusMode)) {
      if (!waypoint.hasCoordinates) {
        continue;
      }
      final offset = projector.project(
        LatLng(waypoint.latitude!, waypoint.longitude!),
      );
      if (waypoint.paradaDoPassageiro) {
        markers.add(
          _ProjectedMarker(
            offset: offset,
            size: const Size(72, 72),
            child: _PassengerMarker(label: waypoint.nome),
          ),
        );
      } else {
        final state = vanRidesStopMarkerStateFor(waypoint, trackingState);
        markers.add(
          _ProjectedMarker(
            offset: offset,
            size: const Size(42, 42),
            child: _StopMarker(
              label: waypoint.ordem?.toString() ?? '',
              state: state,
            ),
          ),
        );
      }
    }

    return markers;
  }

  TripLocationFreshness _effectiveLocationFreshness(
    TripLiveLocationRecord liveLocation,
  ) {
    if (liveLocation.freshness != TripLocationFreshness.unavailable &&
        liveLocation.freshness != TripLocationFreshness.unknown) {
      return liveLocation.freshness;
    }
    final stateFreshness = trackingState.locationFreshness;
    if (stateFreshness != TripLocationFreshness.unavailable &&
        stateFreshness != TripLocationFreshness.unknown) {
      return stateFreshness;
    }
    return liveLocation.freshness;
  }
}

class _ProjectedMarker {
  const _ProjectedMarker({
    required this.offset,
    required this.size,
    required this.child,
  });

  final Offset offset;
  final Size size;
  final Widget child;
}

class _MapProjector {
  _MapProjector({required this.points, required this.size});

  final List<LatLng> points;
  final Size size;

  Offset project(LatLng point) {
    if (points.isEmpty || size.isEmpty) {
      return Offset(size.width / 2, size.height / 2);
    }

    var minLat = points.first.latitude;
    var maxLat = points.first.latitude;
    var minLng = points.first.longitude;
    var maxLng = points.first.longitude;
    for (final item in points.skip(1)) {
      minLat = math.min(minLat, item.latitude);
      maxLat = math.max(maxLat, item.latitude);
      minLng = math.min(minLng, item.longitude);
      maxLng = math.max(maxLng, item.longitude);
    }

    final latSpan = math.max(maxLat - minLat, 0.0009);
    final lngSpan = math.max(maxLng - minLng, 0.0009);
    final insetX = math.min(size.width * 0.18, 86);
    final insetTop = math.min(size.height * 0.28, 320);
    final insetBottom = math.min(size.height * 0.34, 420);
    final drawableWidth = math.max(size.width - insetX * 2, 1);
    final drawableHeight = math.max(size.height - insetTop - insetBottom, 1);
    final x = insetX + ((point.longitude - minLng) / lngSpan) * drawableWidth;
    final y = insetTop + ((maxLat - point.latitude) / latSpan) * drawableHeight;

    return Offset(x.clamp(insetX, size.width - insetX).toDouble(), y);
  }
}

class _VanRidesDeterministicMapPainter extends CustomPainter {
  const _VanRidesDeterministicMapPainter({
    required this.routeLayers,
    required this.projector,
  });

  final List<VanRidesRouteLayerSpec> routeLayers;
  final _MapProjector projector;

  @override
  void paint(Canvas canvas, Size size) {
    _paintMapTexture(canvas, size);
    _paintRoutes(canvas);
  }

  void _paintMapTexture(Canvas canvas, Size size) {
    final roadPaint =
        Paint()
          ..color = VanRidesMapTokens.border.withValues(alpha: 0.2)
          ..strokeWidth = 2.2
          ..strokeCap = StrokeCap.round;
    final minorRoadPaint =
        Paint()
          ..color = VanRidesMapTokens.border.withValues(alpha: 0.11)
          ..strokeWidth = 1.3
          ..strokeCap = StrokeCap.round;

    for (var i = -size.height; i < size.width + size.height; i += 104) {
      canvas.drawLine(
        Offset(i, size.height * 0.12),
        Offset(i + size.height * 0.72, size.height * 0.88),
        minorRoadPaint,
      );
    }
    for (var y = size.height * 0.14; y < size.height; y += 82) {
      final path =
          Path()
            ..moveTo(-20, y)
            ..cubicTo(
              size.width * 0.24,
              y - 28,
              size.width * 0.48,
              y + 34,
              size.width + 20,
              y - 10,
            );
      canvas.drawPath(path, minorRoadPaint);
    }
    for (var x = size.width * 0.1; x < size.width; x += 148) {
      final path =
          Path()
            ..moveTo(x, -20)
            ..cubicTo(
              x + 34,
              size.height * 0.2,
              x - 28,
              size.height * 0.56,
              x + 22,
              size.height + 20,
            );
      canvas.drawPath(path, roadPaint);
    }

    final vignette =
        Paint()
          ..shader = RadialGradient(
            colors: [
              Colors.transparent,
              VanRidesMapTokens.backgroundDeep.withValues(alpha: 0.48),
            ],
            stops: const [0.58, 1],
          ).createShader(Offset.zero & size);
    canvas.drawRect(Offset.zero & size, vignette);
  }

  void _paintRoutes(Canvas canvas) {
    for (final layer in routeLayers) {
      if (layer.points.length < 2) {
        continue;
      }
      final path =
          Path()..moveTo(
            projector.project(layer.points.first).dx,
            projector.project(layer.points.first).dy,
          );
      for (final point in layer.points.skip(1)) {
        final projected = projector.project(point);
        path.lineTo(projected.dx, projected.dy);
      }

      final paint =
          Paint()
            ..color = layer.color
            ..strokeWidth = layer.width.toDouble()
            ..strokeCap = StrokeCap.round
            ..strokeJoin = StrokeJoin.round
            ..style = PaintingStyle.stroke;
      canvas.drawPath(path, paint);
    }
  }

  @override
  bool shouldRepaint(covariant _VanRidesDeterministicMapPainter oldDelegate) {
    return oldDelegate.routeLayers != routeLayers ||
        oldDelegate.projector.points != projector.points ||
        oldDelegate.projector.size != projector.size;
  }
}

class _VehicleMarker extends StatelessWidget {
  const _VehicleMarker({required this.freshness});

  final TripLocationFreshness freshness;

  @override
  Widget build(BuildContext context) {
    final isLive = freshness == TripLocationFreshness.live;
    final color =
        isLive ? VanRidesMapTokens.greenAction : VanRidesMapTokens.redDanger;
    return DecoratedBox(
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.2),
        shape: BoxShape.circle,
        boxShadow: [
          BoxShadow(color: color.withValues(alpha: 0.26), blurRadius: 20),
        ],
      ),
      child: Center(
        child: Container(
          width: 52,
          height: 52,
          decoration: BoxDecoration(
            color: VanRidesMapTokens.surface,
            shape: BoxShape.circle,
            border: Border.all(color: color, width: 4),
          ),
          child: Icon(Icons.directions_bus_filled_rounded, color: color),
        ),
      ),
    );
  }
}

class _PassengerMarker extends StatelessWidget {
  const _PassengerMarker({required this.label});

  final String label;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: VanRidesMapTokens.greenAction.withValues(alpha: 0.18),
        shape: BoxShape.circle,
      ),
      child: Center(
        child: Container(
          width: 54,
          height: 54,
          decoration: BoxDecoration(
            color: VanRidesMapTokens.surface,
            shape: BoxShape.circle,
            border: Border.all(color: VanRidesMapTokens.greenAction, width: 4),
          ),
          child: Stack(
            clipBehavior: Clip.none,
            children: [
              const Center(
                child: Icon(
                  Icons.person_pin_circle_rounded,
                  color: VanRidesMapTokens.greenAction,
                  size: 30,
                ),
              ),
              Positioned(
                right: -8,
                top: -8,
                child: Container(
                  width: 24,
                  height: 24,
                  alignment: Alignment.center,
                  decoration: const BoxDecoration(
                    color: VanRidesMapTokens.greenAction,
                    shape: BoxShape.circle,
                  ),
                  child: Text(
                    label.trim().isEmpty ? 'P' : label.trim()[0].toUpperCase(),
                    style: const TextStyle(
                      color: VanRidesMapTokens.backgroundDeep,
                      fontSize: 12,
                      fontWeight: FontWeight.w900,
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _StopMarker extends StatelessWidget {
  const _StopMarker({required this.label, required this.state});

  final String label;
  final VanRidesStopMarkerState state;

  @override
  Widget build(BuildContext context) {
    final color =
        state == VanRidesStopMarkerState.next
            ? VanRidesMapTokens.amberWarning
            : state == VanRidesStopMarkerState.completed
            ? VanRidesMapTokens.greenAction
            : VanRidesMapTokens.textMuted;
    return DecoratedBox(
      decoration: BoxDecoration(
        color: VanRidesMapTokens.surface.withValues(alpha: 0.96),
        shape: BoxShape.circle,
        border: Border.all(color: color, width: 3),
        boxShadow: const [
          BoxShadow(
            color: Color(0x66070C12),
            blurRadius: 12,
            offset: Offset(0, 8),
          ),
        ],
      ),
      child: Center(
        child: Text(
          label.isEmpty ? '•' : label,
          style: TextStyle(
            color: color,
            fontSize: 15,
            fontWeight: FontWeight.w900,
          ),
        ),
      ),
    );
  }
}
