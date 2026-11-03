import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/app/widgets/vanrides_liquid_surface.dart';
import 'package:vanrides_frontend_dev/app/widgets/vanrides_status_pill.dart';

void main() {
  test('uses solid fallback for accessibility settings', () {
    expect(
      VanRidesLiquidSurface.shouldUseSolidFallback(
        const MediaQueryData(accessibleNavigation: true),
      ),
      isTrue,
    );
    expect(
      VanRidesLiquidSurface.shouldUseSolidFallback(
        const MediaQueryData(disableAnimations: true),
      ),
      isTrue,
    );
    expect(
      VanRidesLiquidSurface.shouldUseSolidFallback(
        const MediaQueryData(highContrast: true),
      ),
      isTrue,
    );
    expect(
      VanRidesLiquidSurface.shouldUseSolidFallback(const MediaQueryData()),
      isFalse,
    );
  });

  testWidgets('renders a liquid nav surface with semantics', (tester) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: MediaQuery(
            data: MediaQueryData(),
            child: VanRidesLiquidSurface.nav(
              semanticLabel: 'Navegacao principal',
              child: Text('Viagem'),
            ),
          ),
        ),
      ),
    );

    expect(_semanticsLabel('Navegacao principal'), findsOneWidget);
    expect(find.text('Viagem'), findsOneWidget);
    expect(find.byKey(VanRidesLiquidSurface.liquidKey), findsOneWidget);
    expect(find.byKey(VanRidesLiquidSurface.solidFallbackKey), findsNothing);
  });

  testWidgets('renders solid fallback when high contrast is enabled', (
    tester,
  ) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: MediaQuery(
            data: MediaQueryData(highContrast: true),
            child: VanRidesLiquidSurface.card(
              semanticLabel: 'Perfil',
              child: Text('Matheus'),
            ),
          ),
        ),
      ),
    );

    expect(_semanticsLabel('Perfil'), findsOneWidget);
    expect(find.byKey(VanRidesLiquidSurface.solidFallbackKey), findsOneWidget);
    expect(find.byKey(VanRidesLiquidSurface.liquidKey), findsNothing);
  });

  testWidgets('status pill keeps visible and semantic labels', (tester) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: VanRidesStatusPill(
            icon: Icons.near_me_rounded,
            label: 'Sinal ao vivo',
            tone: VanRidesStatusTone.tracking,
            semanticLabel: 'Status da localizacao: sinal ao vivo',
          ),
        ),
      ),
    );

    expect(find.text('Sinal ao vivo'), findsOneWidget);
    expect(
      _semanticsLabel('Status da localizacao: sinal ao vivo'),
      findsOneWidget,
    );
  });
}

Finder _semanticsLabel(String label) {
  return find.byWidgetPredicate(
    (widget) => widget is Semantics && widget.properties.label == label,
  );
}
