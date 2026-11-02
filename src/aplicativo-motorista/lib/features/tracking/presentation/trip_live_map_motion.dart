import '../../../app/theme/vanrides_motion.dart';

class TripLiveMapMotionPolicy {
  const TripLiveMapMotionPolicy({
    required this.animatesCamera,
    required this.settleDelay,
  });

  static const animated = TripLiveMapMotionPolicy(
    animatesCamera: true,
    settleDelay: VanRidesMotionTokens.mapCameraSettle,
  );

  static const reduced = TripLiveMapMotionPolicy(
    animatesCamera: false,
    settleDelay: Duration.zero,
  );

  final bool animatesCamera;
  final Duration settleDelay;
}

TripLiveMapMotionPolicy resolveTripLiveMapMotionPolicy({
  required bool disableAnimations,
  required bool accessibleNavigation,
}) {
  if (disableAnimations || accessibleNavigation) {
    return TripLiveMapMotionPolicy.reduced;
  }
  return TripLiveMapMotionPolicy.animated;
}
