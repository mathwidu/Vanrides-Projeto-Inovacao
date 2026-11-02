import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/app/theme/vanrides_motion.dart';

void main() {
  test('motion policy disables durations and wobble for reduced motion', () {
    final policy = VanRidesMotionPolicy.fromMediaQuery(
      const MediaQueryData(disableAnimations: true),
    );

    expect(policy.shouldAnimate, isFalse);
    expect(policy.duration(VanRidesMotionTokens.sheetSnap), Duration.zero);
    expect(policy.curve(VanRidesMotionTokens.easeOutQuart), Curves.linear);
    expect(policy.clampedWobbleScale(2200), 0);
    expect(policy.clampedWobbleOffset(2200), 0);
  });

  test('motion policy clamps liquid wobble values', () {
    const policy = VanRidesMotionPolicy(reduceMotion: false);

    expect(
      policy.clampedWobbleScale(8000),
      VanRidesMotionTokens.maxWobbleScale,
    );
    expect(
      policy.clampedWobbleScale(-8000),
      -VanRidesMotionTokens.maxWobbleScale,
    );
    expect(
      policy.clampedWobbleOffset(8000),
      VanRidesMotionTokens.maxWobbleOffset,
    );
    expect(
      policy.clampedWobbleOffset(-8000),
      -VanRidesMotionTokens.maxWobbleOffset,
    );
  });

  test('motion tokens name operational interactions', () {
    expect(VanRidesMotionTokens.buttonPress, VanRidesMotionTokens.fast);
    expect(VanRidesMotionTokens.statusUpdate, VanRidesMotionTokens.standard);
    expect(VanRidesMotionTokens.routeRefresh, VanRidesMotionTokens.calmReveal);
    expect(VanRidesMotionTokens.detentSettle, VanRidesMotionTokens.sheetSnap);
  });

  test('motion tokens cover shared operational transitions', () {
    expect(
      VanRidesMotionTokens.navigationSelection,
      const Duration(milliseconds: 180),
    );
    expect(
      VanRidesMotionTokens.sheetContentSwitch,
      const Duration(milliseconds: 180),
    );
    expect(
      VanRidesMotionTokens.sheetProgrammatic,
      const Duration(milliseconds: 280),
    );
    expect(
      VanRidesMotionTokens.mapCameraSettle,
      const Duration(milliseconds: 180),
    );
    expect(
      VanRidesMotionTokens.driverTabSwitch,
      VanRidesMotionTokens.statusUpdate,
    );
  });
}
