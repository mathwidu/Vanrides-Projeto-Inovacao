import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/trip_live_map_state.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/widgets/vanrides_map_shell.dart';

Widget _buildSubject(Widget child, {Size size = const Size(390, 844)}) {
  return MaterialApp(
    home: MediaQuery(
      data: MediaQueryData(size: size),
      child: Scaffold(body: child),
    ),
  );
}

void main() {
  testWidgets('renderiza shell dark com top strip, bottom sheet e controles', (
    tester,
  ) async {
    var recentered = false;

    await tester.pumpWidget(
      _buildSubject(
        VanRidesMapShell(
          map: const ColoredBox(
            key: Key('fake-map-stage'),
            color: VanRidesMapTokens.mapBase,
          ),
          topStatus: const VanRidesMapTopStatus(
            icon: Icons.near_me_rounded,
            label: 'Sinal ao vivo',
            value: 'Van a 4 paradas',
          ),
          bottomSheet: const Text('Chega em cerca de 8 min'),
          controls: VanRidesMapControlCluster(
            cameraLockedToContent: false,
            onRecenter: () => recentered = true,
            onZoomIn: () {},
            onZoomOut: () {},
          ),
        ),
      ),
    );

    expect(find.byKey(const Key('vanrides.map.shell')), findsOneWidget);
    expect(find.byKey(const Key('fake-map-stage')), findsOneWidget);
    expect(find.text('Sinal ao vivo'), findsOneWidget);
    expect(find.text('Van a 4 paradas'), findsOneWidget);
    expect(find.text('Chega em cerca de 8 min'), findsOneWidget);
    expect(find.byKey(const Key('vanrides.map.controls')), findsOneWidget);
    expect(find.byType(Card), findsNothing);

    await tester.tap(find.byIcon(Icons.my_location));
    expect(recentered, isTrue);
    expect(tester.takeException(), isNull);
  });

  testWidgets('top status preserva layout compacto sem overflow', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(320, 640));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    await tester.pumpWidget(
      _buildSubject(
        const Center(
          child: SizedBox(
            width: 236,
            child: VanRidesMapTopStatus(
              icon: Icons.wifi_tethering_rounded,
              label: 'Ultima atualizacao ha alguns minutos',
              value: 'Motorista aproximando da parada da Ana Clara',
            ),
          ),
        ),
      ),
    );

    expect(find.byKey(const Key('vanrides.map.top-status')), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('placeholder usa superficie operacional dark sem cards', (
    tester,
  ) async {
    await tester.pumpWidget(
      _buildSubject(
        const VanRidesMapPlaceholderSurface(
          content: TripLiveMapPlaceholderContent(
            loading: false,
            title: 'Aguardando localizacao ao vivo',
            description: 'A van ainda nao publicou coordenadas para esta rota.',
            routeReady: false,
            waitingForSignal: true,
          ),
        ),
      ),
    );

    expect(
      find.byKey(const Key('vanrides.map.placeholder.surface')),
      findsOneWidget,
    );
    expect(find.text('Aguardando localizacao ao vivo'), findsOneWidget);
    expect(find.byIcon(Icons.wifi_tethering_off_rounded), findsOneWidget);
    expect(find.byType(Card), findsNothing);
    expect(tester.takeException(), isNull);
  });
}
