import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/app/widgets/vanrides_liquid_tab_scaffold.dart';

void main() {
  testWidgets('keeps tab pages alive while switching selection', (
    tester,
  ) async {
    final firstDisposeCount = ValueNotifier<int>(0);
    final secondDisposeCount = ValueNotifier<int>(0);

    await tester.pumpWidget(
      MaterialApp(
        home: VanRidesLiquidTabScaffold(
          selectedIndex: 0,
          pages: [
            _TrackedTabPage(label: 'Viagem', disposeCount: firstDisposeCount),
            _TrackedTabPage(
              label: 'Presenca',
              disposeCount: secondDisposeCount,
            ),
          ],
        ),
      ),
    );

    expect(find.text('Viagem'), findsOneWidget);
    expect(find.text('Presenca', skipOffstage: false), findsOneWidget);

    await tester.pumpWidget(
      MaterialApp(
        home: VanRidesLiquidTabScaffold(
          selectedIndex: 1,
          pages: [
            _TrackedTabPage(label: 'Viagem', disposeCount: firstDisposeCount),
            _TrackedTabPage(
              label: 'Presenca',
              disposeCount: secondDisposeCount,
            ),
          ],
        ),
      ),
    );
    await tester.pump();

    expect(firstDisposeCount.value, 0);
    expect(secondDisposeCount.value, 0);
    expect(find.byKey(VanRidesLiquidTabScaffold.bodyKey), findsOneWidget);
  });

  testWidgets('honors reduced motion for transition overlay', (tester) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: MediaQuery(
          data: MediaQueryData(disableAnimations: true),
          child: VanRidesLiquidTabScaffold(
            selectedIndex: 1,
            pages: [SizedBox(), SizedBox()],
          ),
        ),
      ),
    );

    await tester.pump();

    final overlay = tester.widget<ColoredBox>(
      find.byKey(VanRidesLiquidTabScaffold.transitionKey),
    );
    expect(overlay.color.a, 0);
  });
}

class _TrackedTabPage extends StatefulWidget {
  const _TrackedTabPage({required this.label, required this.disposeCount});

  final String label;
  final ValueNotifier<int> disposeCount;

  @override
  State<_TrackedTabPage> createState() => _TrackedTabPageState();
}

class _TrackedTabPageState extends State<_TrackedTabPage> {
  @override
  void dispose() {
    widget.disposeCount.value += 1;
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Center(child: Text(widget.label));
  }
}
