import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/app/theme/vanrides_motion.dart';
import 'package:vanrides_frontend_dev/features/guardian/presentation/widgets/guardian_map_footer_nav.dart';

void main() {
  testWidgets('guardian footer navigation follows shared reduced motion', (
    tester,
  ) async {
    await tester.pumpWidget(
      _wrap(
        disableAnimations: true,
        child: GuardianBottomNavContent(
          selectedIndex: 0,
          onDestinationSelected: (_) {},
        ),
      ),
    );

    final item = tester.widget<AnimatedContainer>(
      find.descendant(
        of: find.byKey(const Key('guardian.map.footerNav.0')),
        matching: find.byType(AnimatedContainer),
      ),
    );
    expect(item.duration, Duration.zero);

    await tester.pumpWidget(
      _wrap(
        disableAnimations: false,
        child: GuardianBottomNavContent(
          selectedIndex: 0,
          onDestinationSelected: (_) {},
        ),
      ),
    );

    final animatedItem = tester.widget<AnimatedContainer>(
      find.descendant(
        of: find.byKey(const Key('guardian.map.footerNav.0')),
        matching: find.byType(AnimatedContainer),
      ),
    );
    expect(animatedItem.duration, VanRidesMotionTokens.navigationSelection);
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
