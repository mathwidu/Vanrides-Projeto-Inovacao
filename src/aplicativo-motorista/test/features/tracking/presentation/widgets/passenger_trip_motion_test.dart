import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/app/theme/vanrides_motion.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/widgets/passenger_trip_adaptive_sheet.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/widgets/passenger_trip_footer_sheet_controls.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/widgets/passenger_trip_sheet_detent.dart';

void main() {
  testWidgets('adaptive sheet uses shared motion and honors reduced motion', (
    tester,
  ) async {
    await tester.pumpWidget(
      _wrap(
        disableAnimations: true,
        child: PassengerTripAdaptiveSheet(
          detent: PassengerTripSheetDetent.resting,
          onDetentChanged: (_) {},
          peek: const Text('peek'),
          resting: const Text('resting'),
          expanded: const Text('expanded'),
        ),
      ),
    );

    final switcher = tester.widget<AnimatedSwitcher>(
      find.byType(AnimatedSwitcher),
    );
    expect(switcher.duration, Duration.zero);
    expect(switcher.switchInCurve, Curves.linear);
    expect(switcher.switchOutCurve, Curves.linear);

    await tester.pumpWidget(
      _wrap(
        disableAnimations: false,
        child: PassengerTripAdaptiveSheet(
          detent: PassengerTripSheetDetent.resting,
          onDetentChanged: (_) {},
          peek: const Text('peek'),
          resting: const Text('resting'),
          expanded: const Text('expanded'),
        ),
      ),
    );

    final animatedSwitcher = tester.widget<AnimatedSwitcher>(
      find.byType(AnimatedSwitcher),
    );
    expect(animatedSwitcher.duration, VanRidesMotionTokens.sheetContentSwitch);
    expect(animatedSwitcher.switchInCurve, VanRidesMotionTokens.easeOutQuart);
  });

  testWidgets('sheet handle chevron uses shared reduced motion policy', (
    tester,
  ) async {
    await tester.pumpWidget(
      _wrap(
        disableAnimations: true,
        child: PassengerTripSheetHandle(
          detent: PassengerTripSheetDetent.resting,
          onDetentSelected: (_) {},
        ),
      ),
    );

    final rotation = tester.widget<AnimatedRotation>(
      find.byType(AnimatedRotation),
    );
    expect(rotation.duration, Duration.zero);
    expect(rotation.curve, Curves.linear);

    await tester.pumpWidget(
      _wrap(
        disableAnimations: false,
        child: PassengerTripSheetHandle(
          detent: PassengerTripSheetDetent.resting,
          onDetentSelected: (_) {},
        ),
      ),
    );

    final animatedRotation = tester.widget<AnimatedRotation>(
      find.byType(AnimatedRotation),
    );
    expect(animatedRotation.duration, VanRidesMotionTokens.navigationSelection);
    expect(animatedRotation.curve, VanRidesMotionTokens.easeOutQuart);
  });
}

Widget _wrap({required bool disableAnimations, required Widget child}) {
  return MaterialApp(
    home: MediaQuery(
      data: MediaQueryData(disableAnimations: disableAnimations),
      child: Scaffold(body: Center(child: child)),
    ),
  );
}
