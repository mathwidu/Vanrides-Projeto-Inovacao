import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_route_geometry_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/map_first_trip_tracking_components.dart';
import 'package:vanrides_frontend_dev/l10n/app_strings.dart';

Widget _buildSubject(Widget child, {Size size = const Size(720, 640)}) {
  return MaterialApp(
    home: Scaffold(
      body: MediaQuery(
        data: MediaQueryData(size: size),
        child: Center(child: child),
      ),
    ),
  );
}

Future<void> _pumpSubject(
  WidgetTester tester,
  Widget child, {
  Size size = const Size(720, 640),
}) async {
  await tester.binding.setSurfaceSize(size);
  addTearDown(() => tester.binding.setSurfaceSize(null));

  await tester.pumpWidget(_buildSubject(child, size: size));
  await tester.pumpAndSettle();
}

const _passengerStop = TripRouteWaypointRecord(
  tipo: 'STOP',
  paradaId: 301,
  passageiroId: 21,
  nome: 'Colegio Central',
  latitude: -29.68,
  longitude: -51.13,
  ordem: 2,
  proximaParada: false,
  paradaDoPassageiro: true,
);

const _nextStop = TripRouteWaypointRecord(
  tipo: 'STOP',
  paradaId: 302,
  passageiroId: 22,
  nome: 'Rua das Flores',
  latitude: -29.681,
  longitude: -51.131,
  ordem: 3,
  proximaParada: true,
  paradaDoPassageiro: false,
);

const _routeLeg = TripRouteLegRecord(
  indice: 1,
  origemLabel: 'Colegio Central',
  destinoLabel: 'Rua das Flores',
  distanciaMetros: 1450,
  duracaoSegundos: 610,
);

void main() {
  testWidgets('grid de metricas mostra label, valor e icone', (tester) async {
    await _pumpSubject(
      tester,
      const MapFirstTripMetricGrid(
        items: [
          MapFirstTripMetricItem(
            label: 'ETA atual',
            value: '12 min',
            icon: Icons.schedule_outlined,
          ),
        ],
      ),
    );

    expect(find.text('ETA atual'), findsOneWidget);
    expect(find.text('12 min'), findsOneWidget);
    expect(find.byIcon(Icons.schedule_outlined), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('tile da fila diferencia parada do passageiro', (tester) async {
    await _pumpSubject(
      tester,
      const MapFirstTripQueueStopTile(
        stop: _passengerStop,
        highlighted: true,
        isNext: false,
      ),
    );

    expect(find.text('Colegio Central'), findsOneWidget);
    expect(find.text('2'), findsOneWidget);
    expect(find.text(AppStrings.passageiro.suaParada), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('tile da fila diferencia proxima parada', (tester) async {
    await _pumpSubject(
      tester,
      const MapFirstTripQueueStopTile(
        stop: _nextStop,
        highlighted: false,
        isNext: true,
      ),
    );

    expect(find.text('Rua das Flores'), findsOneWidget);
    expect(
      find.text(AppStrings.passageiro.proximaParadaDaViagem),
      findsOneWidget,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('tile de trecho preserva distancia e duracao', (tester) async {
    await _pumpSubject(tester, const MapFirstTripRouteLegTile(leg: _routeLeg));

    expect(find.text('Colegio Central → Rua das Flores'), findsOneWidget);
    expect(find.text('2'), findsOneWidget);
    expect(
      find.text(
        AppStrings.passageiro.distanciaDuracaoTrecho('1450 m', '11 min'),
      ),
      findsOneWidget,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('barra de acoes dispara callbacks primario e secundario', (
    tester,
  ) async {
    var primaryPressed = false;
    var secondaryPressed = false;

    await _pumpSubject(
      tester,
      MapFirstTripActionBar(
        primaryLabel: 'Abrir navegacao',
        onPrimaryAction: () => primaryPressed = true,
        secondaryLabel: 'Confirmar presenca',
        onSecondaryAction: () => secondaryPressed = true,
      ),
    );

    await tester.tap(find.text('Abrir navegacao'));
    await tester.tap(find.text('Confirmar presenca'));
    await tester.pumpAndSettle();

    expect(primaryPressed, isTrue);
    expect(secondaryPressed, isTrue);
    expect(tester.takeException(), isNull);
  });
}
