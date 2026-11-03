import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/widgets/vanrides_glass_surface.dart';

void main() {
  test('uses solid fallback for accessible media settings', () {
    expect(
      VanRidesGlassSurface.shouldUseSolidFallback(
        const MediaQueryData(accessibleNavigation: true),
      ),
      isTrue,
    );
    expect(
      VanRidesGlassSurface.shouldUseSolidFallback(
        const MediaQueryData(disableAnimations: true),
      ),
      isTrue,
    );
    expect(
      VanRidesGlassSurface.shouldUseSolidFallback(
        const MediaQueryData(highContrast: true),
      ),
      isTrue,
    );
    expect(
      VanRidesGlassSurface.shouldUseSolidFallback(const MediaQueryData()),
      isFalse,
    );
  });

  testWidgets('renders an accessible sheet surface', (tester) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: MediaQuery(
            data: MediaQueryData(),
            child: VanRidesGlassSurface.sheet(
              semanticLabel: 'Resumo da viagem',
              child: Text('Van a 2 paradas'),
            ),
          ),
        ),
      ),
    );

    expect(_glassSemanticsLabel('Resumo da viagem'), findsOneWidget);
    expect(find.text('Van a 2 paradas'), findsOneWidget);
    expect(find.byKey(VanRidesGlassSurface.glassKey), findsOneWidget);
    expect(find.byKey(VanRidesGlassSurface.solidFallbackKey), findsNothing);
  });

  testWidgets('renders the solid fallback when accessibility asks for it', (
    tester,
  ) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: MediaQuery(
            data: MediaQueryData(accessibleNavigation: true),
            child: VanRidesGlassSurface.sheet(
              semanticLabel: 'Resumo da viagem',
              child: Text('Sinal pausado'),
            ),
          ),
        ),
      ),
    );

    expect(_glassSemanticsLabel('Resumo da viagem'), findsOneWidget);
    expect(find.byKey(VanRidesGlassSurface.solidFallbackKey), findsOneWidget);
    expect(find.byKey(VanRidesGlassSurface.glassKey), findsNothing);
  });
}

Finder _glassSemanticsLabel(String label) {
  return find.byWidgetPredicate(
    (widget) => widget is Semantics && widget.properties.label == label,
  );
}
