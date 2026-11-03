import '../domain/trip_live_location_record.dart';
import '../domain/trip_route_geometry_record.dart';
import '../domain/trip_tracking_state_record.dart';
import 'trip_live_map_state.dart';
import 'widgets/vanrides_map_marker_specs.dart';
import 'widgets/vanrides_route_layers.dart';

enum VanRidesMapSceneMarkerKind { vehicle, passenger, stop }

enum VanRidesMapRendererCandidate {
  googleLegacy,
  mapLibreVector,
  flutterMapRaster,
  deterministicTest,
}

enum VanRidesMapTileModel {
  proprietaryOnline,
  selfHostedVector,
  selfHostedRaster,
  deterministic,
}

enum VanRidesMapRuntimeReadiness {
  activeFallback,
  blockedByDependency,
  testOnly,
}

class VanRidesMapSceneCoordinate {
  const VanRidesMapSceneCoordinate({
    required this.latitude,
    required this.longitude,
  });

  final double latitude;
  final double longitude;
}

class VanRidesMapSceneRouteLayer {
  const VanRidesMapSceneRouteLayer({required this.role, required this.points});

  final String role;
  final List<VanRidesMapSceneCoordinate> points;
}

class VanRidesMapSceneMarker {
  const VanRidesMapSceneMarker({
    required this.id,
    required this.kind,
    required this.semanticLabel,
    required this.stateLabel,
    required this.coordinate,
  });

  final String id;
  final VanRidesMapSceneMarkerKind kind;
  final String semanticLabel;
  final String stateLabel;
  final VanRidesMapSceneCoordinate coordinate;
}

class VanRidesMapRendererScene {
  const VanRidesMapRendererScene({
    required this.routeLayers,
    required this.markers,
  });

  final List<VanRidesMapSceneRouteLayer> routeLayers;
  final List<VanRidesMapSceneMarker> markers;

  int get routePointCount {
    final unique = <String>{};
    for (final layer in routeLayers) {
      for (final point in layer.points) {
        unique.add('${point.latitude}:${point.longitude}');
      }
    }
    return unique.length;
  }
}

class VanRidesMapRendererAssessment {
  const VanRidesMapRendererAssessment({
    required this.candidate,
    required this.label,
    required this.tileModel,
    required this.runtimeReadiness,
    required this.routePointCount,
    required this.markerCount,
    required this.supportsSelfHostedTiles,
    required this.supportsOffline,
    required this.renderingFit,
    required this.performanceFit,
    required this.offlineSizeFit,
    required this.maintenanceRisk,
    required this.blockers,
  });

  final VanRidesMapRendererCandidate candidate;
  final String label;
  final VanRidesMapTileModel tileModel;
  final VanRidesMapRuntimeReadiness runtimeReadiness;
  final int routePointCount;
  final int markerCount;
  final bool supportsSelfHostedTiles;
  final bool supportsOffline;
  final String renderingFit;
  final String performanceFit;
  final String offlineSizeFit;
  final String maintenanceRisk;
  final List<String> blockers;
}

class VanRidesMapRendererSpikeMatrix {
  const VanRidesMapRendererSpikeMatrix({
    required this.recommendedCandidate,
    required this.tilePolicy,
    required this.assessments,
  });

  final VanRidesMapRendererCandidate recommendedCandidate;
  final String tilePolicy;
  final List<VanRidesMapRendererAssessment> assessments;

  VanRidesMapRendererAssessment assessmentFor(
    VanRidesMapRendererCandidate candidate,
  ) {
    return assessments.singleWhere((item) => item.candidate == candidate);
  }
}

VanRidesMapRendererScene buildVanRidesMapRendererScene({
  required TripRouteGeometryRecord? routeGeometry,
  required TripLiveLocationRecord? location,
  required TripTrackingStateRecord trackingState,
  required TripLiveMapFocusMode focusMode,
}) {
  final routeLayers = buildVanRidesRouteLayers(
    routeGeometry: routeGeometry,
    trackingState: trackingState,
  ).map(_sceneRouteLayerFrom).toList(growable: false);

  return VanRidesMapRendererScene(
    routeLayers: routeLayers,
    markers: _sceneMarkersFor(
      routeGeometry: routeGeometry,
      location: location,
      trackingState: trackingState,
      focusMode: focusMode,
    ),
  );
}

VanRidesMapRendererSpikeMatrix buildVanRidesMapRendererSpikeMatrix(
  VanRidesMapRendererScene scene,
) {
  return VanRidesMapRendererSpikeMatrix(
    recommendedCandidate: VanRidesMapRendererCandidate.mapLibreVector,
    tilePolicy:
        'Tiles publicos OSM nao sao backend do produto; usar apenas tiles '
        'self-hosted ou provider com permissao explicita para cache/offline.',
    assessments: [
      _assessment(
        candidate: VanRidesMapRendererCandidate.googleLegacy,
        label: 'Google Maps legado',
        tileModel: VanRidesMapTileModel.proprietaryOnline,
        runtimeReadiness: VanRidesMapRuntimeReadiness.activeFallback,
        scene: scene,
        supportsSelfHostedTiles: false,
        supportsOffline: false,
        renderingFit: 'Bom fallback atual, mas nao e self-hosted.',
        performanceFit: 'Ja validado no app atual.',
        offlineSizeFit: 'Nao atende pacote offline VanRides.',
        maintenanceRisk: 'Baixo no curto prazo, alto para estrategia propria.',
        blockers: const [],
      ),
      _assessment(
        candidate: VanRidesMapRendererCandidate.mapLibreVector,
        label: 'MapLibre vector tiles',
        tileModel: VanRidesMapTileModel.selfHostedVector,
        runtimeReadiness: VanRidesMapRuntimeReadiness.blockedByDependency,
        scene: scene,
        supportsSelfHostedTiles: true,
        supportsOffline: true,
        renderingFit: 'Melhor alvo para estilo VanRides e vector tiles.',
        performanceFit: 'Precisa smoke em device com SDK MapLibre.',
        offlineSizeFit: 'Melhor tendencia para pacotes por regiao/corredor.',
        maintenanceRisk: 'Medio ate estabilizar dependencia e style JSON.',
        blockers: const [
          'Adicionar dependencia maplibre ou maplibre_gl em card controlado.',
          'Publicar style JSON self-hosted antes de runtime real.',
        ],
      ),
      _assessment(
        candidate: VanRidesMapRendererCandidate.flutterMapRaster,
        label: 'flutter_map raster tiles',
        tileModel: VanRidesMapTileModel.selfHostedRaster,
        runtimeReadiness: VanRidesMapRuntimeReadiness.blockedByDependency,
        scene: scene,
        supportsSelfHostedTiles: true,
        supportsOffline: true,
        renderingFit: 'Bom fallback simples para raster/cache.',
        performanceFit: 'Precisa medir camadas e muitos markers em device.',
        offlineSizeFit: 'Pode crescer rapido com zooms e area regional.',
        maintenanceRisk: 'Baixo/medio, mas menos expressivo para produto.',
        blockers: const [
          'Adicionar dependencia flutter_map em card controlado.',
          'Validar cache/offline com tiles self-hosted, sem prefetch OSM publico.',
        ],
      ),
      _assessment(
        candidate: VanRidesMapRendererCandidate.deterministicTest,
        label: 'Superficie deterministica de testes',
        tileModel: VanRidesMapTileModel.deterministic,
        runtimeReadiness: VanRidesMapRuntimeReadiness.testOnly,
        scene: scene,
        supportsSelfHostedTiles: false,
        supportsOffline: false,
        renderingFit: 'Excelente para screenshot/teste, nao para runtime.',
        performanceFit: 'Deterministico e leve.',
        offlineSizeFit: 'Nao representa pacote real.',
        maintenanceRisk: 'Baixo como fixture visual.',
        blockers: const ['Usar apenas como prova visual/teste automatizado.'],
      ),
    ],
  );
}

VanRidesMapSceneRouteLayer _sceneRouteLayerFrom(VanRidesRouteLayerSpec layer) {
  return VanRidesMapSceneRouteLayer(
    role: layer.type.name,
    points: layer.points
        .map(
          (point) => VanRidesMapSceneCoordinate(
            latitude: point.latitude,
            longitude: point.longitude,
          ),
        )
        .toList(growable: false),
  );
}

List<VanRidesMapSceneMarker> _sceneMarkersFor({
  required TripRouteGeometryRecord? routeGeometry,
  required TripLiveLocationRecord? location,
  required TripTrackingStateRecord trackingState,
  required TripLiveMapFocusMode focusMode,
}) {
  final markers = <VanRidesMapSceneMarker>[];
  if (location != null) {
    final freshness = _effectiveLocationFreshness(location, trackingState);
    final spec = vanRidesVehicleMarkerSpec(freshness);
    markers.add(
      VanRidesMapSceneMarker(
        id: 'vehicle-${freshness.name}',
        kind: VanRidesMapSceneMarkerKind.vehicle,
        semanticLabel: spec.semanticLabel,
        stateLabel: spec.stateLabel,
        coordinate: VanRidesMapSceneCoordinate(
          latitude: location.latitude,
          longitude: location.longitude,
        ),
      ),
    );
  }

  if (routeGeometry == null) {
    return List<VanRidesMapSceneMarker>.unmodifiable(markers);
  }

  for (final waypoint in visibleTripRouteWaypoints(routeGeometry, focusMode)) {
    if (!waypoint.hasCoordinates) {
      continue;
    }
    markers.add(
      waypoint.paradaDoPassageiro
          ? _passengerMarker(waypoint)
          : _stopMarker(waypoint, trackingState),
    );
  }

  return List<VanRidesMapSceneMarker>.unmodifiable(markers);
}

VanRidesMapSceneMarker _passengerMarker(TripRouteWaypointRecord waypoint) {
  final spec = vanRidesPassengerMarkerSpec(label: waypoint.nome);
  return VanRidesMapSceneMarker(
    id: _waypointMarkerId('passenger', waypoint),
    kind: VanRidesMapSceneMarkerKind.passenger,
    semanticLabel: spec.semanticLabel,
    stateLabel: spec.stateLabel,
    coordinate: VanRidesMapSceneCoordinate(
      latitude: waypoint.latitude!,
      longitude: waypoint.longitude!,
    ),
  );
}

VanRidesMapSceneMarker _stopMarker(
  TripRouteWaypointRecord waypoint,
  TripTrackingStateRecord trackingState,
) {
  final state = vanRidesStopMarkerStateFor(waypoint, trackingState);
  final spec = vanRidesStopMarkerSpec(
    state: state,
    order: waypoint.ordem,
    label: waypoint.nome,
  );
  return VanRidesMapSceneMarker(
    id: _waypointMarkerId('stop', waypoint),
    kind: VanRidesMapSceneMarkerKind.stop,
    semanticLabel: spec.semanticLabel,
    stateLabel: spec.stateLabel,
    coordinate: VanRidesMapSceneCoordinate(
      latitude: waypoint.latitude!,
      longitude: waypoint.longitude!,
    ),
  );
}

TripLocationFreshness _effectiveLocationFreshness(
  TripLiveLocationRecord location,
  TripTrackingStateRecord trackingState,
) {
  if (location.freshness != TripLocationFreshness.unavailable &&
      location.freshness != TripLocationFreshness.unknown) {
    return location.freshness;
  }
  final stateFreshness = trackingState.locationFreshness;
  if (stateFreshness != TripLocationFreshness.unavailable &&
      stateFreshness != TripLocationFreshness.unknown) {
    return stateFreshness;
  }
  return location.freshness;
}

String _waypointMarkerId(String prefix, TripRouteWaypointRecord waypoint) {
  final id = waypoint.paradaId ?? waypoint.passageiroId ?? waypoint.ordem;
  return '$prefix-${id ?? waypoint.nome}-${waypoint.ordem ?? 0}';
}

VanRidesMapRendererAssessment _assessment({
  required VanRidesMapRendererCandidate candidate,
  required String label,
  required VanRidesMapTileModel tileModel,
  required VanRidesMapRuntimeReadiness runtimeReadiness,
  required VanRidesMapRendererScene scene,
  required bool supportsSelfHostedTiles,
  required bool supportsOffline,
  required String renderingFit,
  required String performanceFit,
  required String offlineSizeFit,
  required String maintenanceRisk,
  required List<String> blockers,
}) {
  return VanRidesMapRendererAssessment(
    candidate: candidate,
    label: label,
    tileModel: tileModel,
    runtimeReadiness: runtimeReadiness,
    routePointCount: scene.routePointCount,
    markerCount: scene.markers.length,
    supportsSelfHostedTiles: supportsSelfHostedTiles,
    supportsOffline: supportsOffline,
    renderingFit: renderingFit,
    performanceFit: performanceFit,
    offlineSizeFit: offlineSizeFit,
    maintenanceRisk: maintenanceRisk,
    blockers: blockers,
  );
}
