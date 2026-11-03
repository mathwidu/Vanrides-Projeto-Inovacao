import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/domain/driver_trip_record.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/presentation/widgets/driver_home_widgets.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_record.dart';

void main() {
  testWidgets('DriverHomeUnavailableTripState renders loading and refresh', (
    tester,
  ) async {
    var refreshTaps = 0;

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: DriverHomeUnavailableTripState(
            isLoading: true,
            errorMessage: null,
            refreshKey: const Key('driver.refresh'),
            onRefresh: () async {
              refreshTaps++;
            },
          ),
        ),
      ),
    );

    expect(find.text('VanRides'), findsOneWidget);
    expect(find.byType(CircularProgressIndicator), findsOneWidget);

    await tester.tap(find.byKey(const Key('driver.refresh')));
    await tester.pump();

    expect(refreshTaps, 1);
  });

  testWidgets('DriverHomeUnavailableTripState renders error and retry', (
    tester,
  ) async {
    var refreshTaps = 0;

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: DriverHomeUnavailableTripState(
            isLoading: false,
            errorMessage: 'Falha ao carregar a operacao',
            refreshKey: const Key('driver.refresh'),
            onRefresh: () async {
              refreshTaps++;
            },
          ),
        ),
      ),
    );

    expect(find.text('Erro ao carregar operacao'), findsOneWidget);
    expect(find.text('Falha ao carregar a operacao'), findsOneWidget);

    await tester.tap(find.text('Tentar novamente'));
    await tester.pumpAndSettle();

    expect(refreshTaps, 1);
  });

  testWidgets('DriverHomeUnavailableTripState renders empty trip state', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: DriverHomeUnavailableTripState(
            isLoading: false,
            errorMessage: null,
            refreshKey: const Key('driver.refresh'),
            onRefresh: () async {},
          ),
        ),
      ),
    );

    expect(find.text('Sem viagem publicada hoje'), findsOneWidget);
    expect(
      find.text(
        'Quando a viagem automatica do dia for gerada, a rota operacional aparece aqui com navegacao pronta e fila de paradas.',
      ),
      findsOneWidget,
    );
  });

  testWidgets('DriverOperationTypeBadge renders the operation label', (
    tester,
  ) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(body: DriverOperationTypeBadge(label: 'Volta')),
      ),
    );

    expect(find.text('Volta'), findsOneWidget);
  });

  testWidgets('DriverUtilityRow renders actions and dispatches callbacks', (
    tester,
  ) async {
    var taps = 0;

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: SizedBox(
            width: 360,
            child: DriverUtilityRow(
              actions: [
                DriverUtilityAction(
                  key: const Key('driver.utility.details'),
                  icon: Icons.info_outline,
                  label: 'Detalhes',
                  onPressed: () => taps++,
                ),
                DriverUtilityAction(
                  key: const Key('driver.utility.map'),
                  icon: Icons.map_outlined,
                  label: 'Mapa',
                  onPressed: () => taps++,
                ),
              ],
            ),
          ),
        ),
      ),
    );

    expect(find.text('Detalhes'), findsOneWidget);
    expect(find.text('Mapa'), findsOneWidget);

    await tester.tap(find.byKey(const Key('driver.utility.details')));
    await tester.pumpAndSettle();

    expect(taps, 1);
  });

  testWidgets('DriverStopCard renders stop status, ETA and presence chips', (
    tester,
  ) async {
    const stop = DriverTripStopRecord(
      id: 10,
      ordem: 2,
      status: TripTrackingStopStatus.pending,
      etaMinutos: 9,
      confirmadoPresenca: true,
      proximaParada: true,
      passageiroId: 99,
      passageiroNome: 'Joao',
      menorDeIdade: false,
    );

    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: DriverStopCard(stop: stop, routeName: 'Linha Central'),
        ),
      ),
    );

    expect(find.text('Joao'), findsOneWidget);
    expect(find.text('9 min'), findsOneWidget);
    expect(find.text('Proxima'), findsOneWidget);
    expect(find.text('Presenca confirmada'), findsOneWidget);
  });

  testWidgets('DriverDetailActionSection uses injected keys and callbacks', (
    tester,
  ) async {
    var skipTaps = 0;
    var noShowTaps = 0;
    var endTaps = 0;

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: DriverDetailActionSection(
            actionKeys: const DriverTripDetailActionKeys(
              maps: Key('driver.actions.maps'),
              presence: Key('driver.actions.presence'),
              editRoute: Key('driver.actions.editRoute'),
              skipStop: Key('driver.actions.skipStop'),
              noShowStop: Key('driver.actions.noShowStop'),
              endTrip: Key('driver.actions.endTrip'),
            ),
            planningMode: false,
            canOpenMaps: true,
            openingNavigation: false,
            runningLifecycleAction: false,
            hasNextStop: true,
            onOpenMaps: () {},
            onOpenPresence: () {},
            onEditRoute: () {},
            onSkipStop: () => skipTaps++,
            onNoShowStop: () => noShowTaps++,
            onEndTrip: () => endTaps++,
          ),
        ),
      ),
    );

    await tester.tap(find.byKey(const Key('driver.actions.skipStop')));
    await tester.tap(find.byKey(const Key('driver.actions.noShowStop')));
    await tester.tap(find.byKey(const Key('driver.actions.endTrip')));
    await tester.pumpAndSettle();

    expect(skipTaps, 1);
    expect(noShowTaps, 1);
    expect(endTaps, 1);
  });
}
