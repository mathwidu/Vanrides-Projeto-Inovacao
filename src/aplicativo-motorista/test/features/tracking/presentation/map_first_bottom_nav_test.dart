import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/app/theme/vanrides_motion.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/map_first_v2_shell.dart';

void main() {
  testWidgets(
    'bottom navigation exposes each destination as an accessible tab',
    (tester) async {
      var selectedIndex = 0;

      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: Align(
              alignment: Alignment.bottomCenter,
              child: StatefulBuilder(
                builder: (context, setState) {
                  return MapFirstBottomNavBar(
                    selectedIndex: selectedIndex,
                    onDestinationSelected: (index) {
                      setState(() => selectedIndex = index);
                    },
                    destinations: const [
                      MapFirstNavDestination(
                        icon: Icons.explore_outlined,
                        selectedIcon: Icons.explore,
                        label: 'Viagem',
                      ),
                      MapFirstNavDestination(
                        icon: Icons.fact_check_outlined,
                        selectedIcon: Icons.fact_check,
                        label: 'Presenca',
                      ),
                      MapFirstNavDestination(
                        icon: Icons.person_outline,
                        selectedIcon: Icons.person,
                        label: 'Conta',
                      ),
                    ],
                  );
                },
              ),
            ),
          ),
        ),
      );

      expect(_navSemantics(tester, 'Viagem').properties.button, isTrue);
      expect(_navSemantics(tester, 'Viagem').properties.selected, isTrue);
      expect(_navSemantics(tester, 'Viagem').properties.onTap, isNotNull);
      expect(_navSemantics(tester, 'Presenca').properties.button, isTrue);
      expect(_navSemantics(tester, 'Presenca').properties.selected, isFalse);
      expect(_navSemantics(tester, 'Presenca').properties.onTap, isNotNull);

      await tester.tap(find.byKey(MapFirstBottomNavItems.itemKey(1)));
      await tester.pumpAndSettle();

      expect(selectedIndex, 1);
      expect(_navSemantics(tester, 'Presenca').properties.selected, isTrue);
    },
  );

  testWidgets('bottom navigation selection uses shared reduced motion', (
    tester,
  ) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: MediaQuery(
          data: MediaQueryData(disableAnimations: true),
          child: Scaffold(
            body: Align(
              alignment: Alignment.bottomCenter,
              child: MapFirstBottomNavBar(
                selectedIndex: 0,
                onDestinationSelected: _noopDestinationSelected,
                destinations: [
                  MapFirstNavDestination(
                    icon: Icons.explore_outlined,
                    selectedIcon: Icons.explore,
                    label: 'Viagem',
                  ),
                  MapFirstNavDestination(
                    icon: Icons.fact_check_outlined,
                    selectedIcon: Icons.fact_check,
                    label: 'Presenca',
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );

    final item = tester.widget<AnimatedContainer>(
      find.descendant(
        of: find.byKey(MapFirstBottomNavItems.itemKey(0)),
        matching: find.byType(AnimatedContainer),
      ),
    );
    expect(item.duration, Duration.zero);

    await tester.pumpWidget(
      const MaterialApp(
        home: MediaQuery(
          data: MediaQueryData(),
          child: Scaffold(
            body: Align(
              alignment: Alignment.bottomCenter,
              child: MapFirstBottomNavBar(
                selectedIndex: 0,
                onDestinationSelected: _noopDestinationSelected,
                destinations: [
                  MapFirstNavDestination(
                    icon: Icons.explore_outlined,
                    selectedIcon: Icons.explore,
                    label: 'Viagem',
                  ),
                  MapFirstNavDestination(
                    icon: Icons.fact_check_outlined,
                    selectedIcon: Icons.fact_check,
                    label: 'Presenca',
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );

    final animatedItem = tester.widget<AnimatedContainer>(
      find.descendant(
        of: find.byKey(MapFirstBottomNavItems.itemKey(0)),
        matching: find.byType(AnimatedContainer),
      ),
    );
    expect(animatedItem.duration, VanRidesMotionTokens.navigationSelection);
  });
}

Semantics _navSemantics(WidgetTester tester, String label) {
  return tester
      .widgetList<Semantics>(find.byType(Semantics))
      .firstWhere((widget) => widget.properties.label == label);
}

void _noopDestinationSelected(int _) {}
