import 'dart:async';
import 'dart:math' show Point;

import 'package:flutter/material.dart';
import 'package:maplibre_gl/maplibre_gl.dart' as maplibre;

import '../vanrides_map_renderer_spike.dart';

const vanRidesMapLibreProofStyleJson = '''
{
  "version": 8,
  "name": "VanRides MapLibre proof self-hosted style",
  "metadata": {
    "vanrides": "self-hosted-proof-no-public-osm-tiles",
    "vanrides:region": "BR-RS",
    "vanrides:styleVersion": "style-smoke-v1",
    "vanrides:tileSetVersion": "br-rs-smoke-v1"
  },
  "sources": {
    "vanrides-vector-tiles": {
      "type": "vector",
      "tiles": [
        "http://127.0.0.1:8099/vanrides/maps/BR-RS/vector/{z}/{x}/{y}.pbf"
      ],
      "minzoom": 0,
      "maxzoom": 14,
      "attribution": "VanRides self-hosted tiles smoke"
    }
  },
  "layers": [
    {
      "id": "background",
      "type": "background",
      "paint": {
        "background-color": "#07111f"
      }
    }
  ]
}
''';

class VanRidesMapLibreProofSurface extends StatefulWidget {
  const VanRidesMapLibreProofSurface({super.key, required this.scene});

  final VanRidesMapRendererScene scene;

  @override
  State<VanRidesMapLibreProofSurface> createState() =>
      _VanRidesMapLibreProofSurfaceState();
}

class _VanRidesMapLibreProofSurfaceState
    extends State<VanRidesMapLibreProofSurface> {
  maplibre.MapLibreMapController? _controller;

  @override
  void didUpdateWidget(covariant VanRidesMapLibreProofSurface oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.scene != widget.scene) {
      unawaited(_syncScene());
    }
  }

  @override
  Widget build(BuildContext context) {
    return maplibre.MapLibreMap(
      key: const Key('trip.live.map.maplibre'),
      initialCameraPosition: maplibre.CameraPosition(
        target: _initialTarget(),
        zoom: 15.2,
      ),
      styleString: vanRidesMapLibreProofStyleJson,
      zoomGesturesEnabled: true,
      scrollGesturesEnabled: true,
      rotateGesturesEnabled: false,
      tiltGesturesEnabled: false,
      compassEnabled: false,
      myLocationEnabled: false,
      attributionButtonMargins: const Point(20, 20),
      onMapCreated: (controller) {
        _controller = controller;
      },
      onStyleLoadedCallback: () {
        unawaited(_syncScene());
      },
    );
  }

  maplibre.LatLng _initialTarget() {
    for (final marker in widget.scene.markers) {
      return maplibre.LatLng(
        marker.coordinate.latitude,
        marker.coordinate.longitude,
      );
    }
    for (final layer in widget.scene.routeLayers) {
      if (layer.points.isNotEmpty) {
        final point = layer.points.first;
        return maplibre.LatLng(point.latitude, point.longitude);
      }
    }
    return const maplibre.LatLng(0, 0);
  }

  Future<void> _syncScene() async {
    final controller = _controller;
    if (controller == null) {
      return;
    }
    try {
      await controller.clearLines();
      await controller.clearCircles();
      await controller.addLines(_routeLineOptions());
      await controller.addCircles(_markerCircleOptions());
    } catch (error, stackTrace) {
      FlutterError.reportError(
        FlutterErrorDetails(
          exception: error,
          stack: stackTrace,
          library: 'vanrides maplibre proof',
          context: ErrorDescription('while syncing route scene to MapLibre'),
        ),
      );
    }
  }

  List<maplibre.LineOptions> _routeLineOptions() {
    return widget.scene.routeLayers
        .where((layer) => layer.points.length >= 2)
        .map(
          (layer) => maplibre.LineOptions(
            geometry: layer.points
                .map(
                  (point) => maplibre.LatLng(point.latitude, point.longitude),
                )
                .toList(growable: false),
            lineColor: _lineColorFor(layer.role),
            lineWidth: layer.role == 'remaining' ? 5 : 3,
            lineOpacity: layer.role == 'completed' ? 0.48 : 0.9,
          ),
        )
        .toList(growable: false);
  }

  List<maplibre.CircleOptions> _markerCircleOptions() {
    return widget.scene.markers
        .map(
          (marker) => maplibre.CircleOptions(
            geometry: maplibre.LatLng(
              marker.coordinate.latitude,
              marker.coordinate.longitude,
            ),
            circleColor: _markerColorFor(marker.kind),
            circleRadius:
                marker.kind == VanRidesMapSceneMarkerKind.vehicle ? 9 : 7,
            circleStrokeColor: '#ffffff',
            circleStrokeWidth: 2,
          ),
        )
        .toList(growable: false);
  }

  String _lineColorFor(String role) {
    return switch (role) {
      'completed' => '#6b7280',
      'remaining' => '#38bdf8',
      'focus' => '#fbbf24',
      _ => '#93c5fd',
    };
  }

  String _markerColorFor(VanRidesMapSceneMarkerKind kind) {
    return switch (kind) {
      VanRidesMapSceneMarkerKind.vehicle => '#22c55e',
      VanRidesMapSceneMarkerKind.passenger => '#f97316',
      VanRidesMapSceneMarkerKind.stop => '#38bdf8',
    };
  }
}
