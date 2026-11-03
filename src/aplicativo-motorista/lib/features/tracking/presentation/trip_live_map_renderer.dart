import 'trip_live_map_state.dart';

const vanRidesMapLibreProofEnabled = bool.fromEnvironment(
  'VANRIDES_MAPLIBRE_PROOF',
  defaultValue: false,
);

enum TripLiveMapRenderer {
  googleLegacy,
  mapLibreProof,
  deterministic,
  placeholder,
}

TripLiveMapRenderer resolveTripLiveMapRenderer({
  required TripLiveMapRenderMode renderMode,
  required TripLiveMapRenderer requestedRenderer,
  required bool mapLibreProofEnabled,
  required bool e2eMode,
  required bool isWeb,
  required String googleApiKey,
}) {
  if (renderMode == TripLiveMapRenderMode.placeholder) {
    return TripLiveMapRenderer.placeholder;
  }
  if (e2eMode) {
    return TripLiveMapRenderer.deterministic;
  }
  if (requestedRenderer == TripLiveMapRenderer.mapLibreProof &&
      mapLibreProofEnabled) {
    return TripLiveMapRenderer.mapLibreProof;
  }
  if (!isWeb || googleApiKey.trim().isNotEmpty) {
    return TripLiveMapRenderer.googleLegacy;
  }
  return TripLiveMapRenderer.deterministic;
}

bool shouldUseTripLivePlatformMap({
  required TripLiveMapRenderMode renderMode,
  required bool e2eMode,
  required bool isWeb,
  required String googleApiKey,
}) {
  return resolveTripLiveMapRenderer(
        renderMode: renderMode,
        requestedRenderer: TripLiveMapRenderer.googleLegacy,
        mapLibreProofEnabled: false,
        e2eMode: e2eMode,
        isWeb: isWeb,
        googleApiKey: googleApiKey,
      ) ==
      TripLiveMapRenderer.googleLegacy;
}
