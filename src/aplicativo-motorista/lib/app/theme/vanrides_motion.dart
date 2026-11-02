import 'package:flutter/material.dart';

class VanRidesMotionTokens {
  const VanRidesMotionTokens._();

  static const Duration instant = Duration.zero;
  static const Duration fast = Duration(milliseconds: 160);
  static const Duration standard = Duration(milliseconds: 220);
  static const Duration sheetSnap = Duration(milliseconds: 320);
  static const Duration calmReveal = Duration(milliseconds: 380);
  static const Duration navigationSelection = Duration(milliseconds: 180);
  static const Duration sheetContentSwitch = Duration(milliseconds: 180);
  static const Duration sheetProgrammatic = Duration(milliseconds: 280);
  static const Duration mapCameraSettle = Duration(milliseconds: 180);
  static const Duration buttonPress = fast;
  static const Duration statusUpdate = standard;
  static const Duration detentSettle = sheetSnap;
  static const Duration routeRefresh = calmReveal;
  static const Duration driverTabSwitch = statusUpdate;

  static const Curve easeOutQuart = Cubic(0.25, 1, 0.5, 1);
  static const Curve easeOutQuint = Cubic(0.22, 1, 0.36, 1);
  static const Curve easeInOutSoft = Cubic(0.65, 0, 0.35, 1);

  static const SpringDescription liquidSheetSpring = SpringDescription(
    mass: 1,
    stiffness: 360,
    damping: 34,
  );

  static const double maxWobbleScale = 0.018;
  static const double maxWobbleOffset = 8;
}

class VanRidesMotionPolicy {
  const VanRidesMotionPolicy({required this.reduceMotion});

  factory VanRidesMotionPolicy.fromMediaQuery(MediaQueryData mediaQuery) {
    return VanRidesMotionPolicy(
      reduceMotion:
          mediaQuery.disableAnimations || mediaQuery.accessibleNavigation,
    );
  }

  static VanRidesMotionPolicy of(BuildContext context) {
    return VanRidesMotionPolicy.fromMediaQuery(MediaQuery.of(context));
  }

  final bool reduceMotion;

  bool get shouldAnimate => !reduceMotion;

  Duration duration(Duration duration) {
    return reduceMotion ? VanRidesMotionTokens.instant : duration;
  }

  Curve curve(Curve curve) {
    return reduceMotion ? Curves.linear : curve;
  }

  double clampedWobbleScale(double dragVelocity) {
    if (reduceMotion) {
      return 0;
    }
    final normalized = (dragVelocity / 1800).clamp(-1.0, 1.0);
    return normalized * VanRidesMotionTokens.maxWobbleScale;
  }

  double clampedWobbleOffset(double dragVelocity) {
    if (reduceMotion) {
      return 0;
    }
    final normalized = (dragVelocity / 1600).clamp(-1.0, 1.0);
    return normalized * VanRidesMotionTokens.maxWobbleOffset;
  }
}
