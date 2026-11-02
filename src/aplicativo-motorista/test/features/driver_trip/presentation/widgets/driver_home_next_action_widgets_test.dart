import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/map_first_v2_components.dart';

void main() {
  testWidgets('primary action label stays inside narrow driver sheet', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(190, 220));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    await tester.pumpWidget(
      MaterialApp(
        home: MediaQuery(
          data: const MediaQueryData(
            size: Size(190, 220),
            textScaler: TextScaler.linear(2),
          ),
          child: Scaffold(
            body: Align(
              alignment: Alignment.topCenter,
              child: SizedBox(
                width: 170,
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    MapFirstPrimaryButton(
                      buttonKey: const Key('driver.primary'),
                      label: 'Confirmar embarque do passageiro agora',
                      icon: Icons.check_circle_outline_rounded,
                      onPressed: () {},
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );

    expect(find.byKey(const Key('driver.primary')), findsOneWidget);
    expect(find.text('Confirmar embarque do passageiro agora'), findsOneWidget);
    expect(
      tester.getSize(find.byKey(const Key('driver.primary'))).height,
      lessThanOrEqualTo(76),
    );
    expect(tester.takeException(), isNull);
  });
}
