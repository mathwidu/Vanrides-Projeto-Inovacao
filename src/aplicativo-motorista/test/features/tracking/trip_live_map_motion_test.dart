import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/trip_live_map_motion.dart';

void main() {
  test('motion policy disables camera animation for reduced motion users', () {
    final defaultPolicy = resolveTripLiveMapMotionPolicy(
      disableAnimations: false,
      accessibleNavigation: false,
    );
    final reducedPolicy = resolveTripLiveMapMotionPolicy(
      disableAnimations: true,
      accessibleNavigation: false,
    );
    final accessiblePolicy = resolveTripLiveMapMotionPolicy(
      disableAnimations: false,
      accessibleNavigation: true,
    );

    expect(defaultPolicy.animatesCamera, isTrue);
    expect(defaultPolicy.settleDelay, const Duration(milliseconds: 180));
    expect(reducedPolicy.animatesCamera, isFalse);
    expect(reducedPolicy.settleDelay, Duration.zero);
    expect(accessiblePolicy.animatesCamera, isFalse);
  });
}
