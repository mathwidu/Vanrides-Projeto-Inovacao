import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/domain/driver_trip_record.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/presentation/widgets/driver_home_trip_widgets.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_state_record.dart';

void main() {
  testWidgets(
    'Driver route editing sheet renders stops and dispatches actions',
    (tester) async {
      var saveTaps = 0;
      var cancelTaps = 0;
      int? oldIndex;
      int? newIndex;

      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: SingleChildScrollView(
              child: SizedBox(
                width: 720,
                child: DriverHomeRouteEditingSheet(
                  trip: _plannedTrip,
                  editableStops: const [_stop, _secondStop],
                  savingRoute: false,
                  saveRouteKey: const Key('driver.saveRoute'),
                  cancelRouteKey: const Key('driver.cancelRoute'),
                  onReorder: (oldValue, newValue) {
                    oldIndex = oldValue;
                    newIndex = newValue;
                  },
                  onSaveRoute: () => saveTaps++,
                  onCancelRoute: () => cancelTaps++,
                ),
              ),
            ),
          ),
        ),
      );

      expect(find.byType(ReorderableListView), findsOneWidget);
      expect(find.text('Aluno A'), findsOneWidget);
      expect(find.text('Aluno B'), findsOneWidget);

      final reorderable = tester.widget<ReorderableListView>(
        find.byType(ReorderableListView),
      );
      reorderable.onReorder(0, 1);
      await tester.tap(find.byKey(const Key('driver.saveRoute')));
      await tester.tap(find.byKey(const Key('driver.cancelRoute')));
      await tester.pumpAndSettle();

      expect(oldIndex, 0);
      expect(newIndex, 1);
      expect(saveTaps, 1);
      expect(cancelTaps, 1);
    },
  );

  testWidgets('Driver route editing sheet disables actions while saving', (
    tester,
  ) async {
    var saveTaps = 0;
    var cancelTaps = 0;

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: SingleChildScrollView(
            child: SizedBox(
              width: 360,
              child: DriverHomeRouteEditingSheet(
                trip: _plannedTrip,
                editableStops: const [_stop],
                savingRoute: true,
                saveRouteKey: const Key('driver.saveRoute'),
                cancelRouteKey: const Key('driver.cancelRoute'),
                onReorder: (_, _) {},
                onSaveRoute: () => saveTaps++,
                onCancelRoute: () => cancelTaps++,
              ),
            ),
          ),
        ),
      ),
    );

    expect(find.byType(CircularProgressIndicator), findsOneWidget);
    expect(find.byKey(const Key('driver.saveRoute')), findsOneWidget);
    expect(find.byKey(const Key('driver.cancelRoute')), findsOneWidget);

    await tester.tap(find.byKey(const Key('driver.saveRoute')));
    await tester.tap(find.byKey(const Key('driver.cancelRoute')));
    await tester.pump();

    expect(saveTaps, 0);
    expect(cancelTaps, 0);
  });
}

const _stop = DriverTripStopRecord(
  id: 11,
  ordem: 1,
  status: TripTrackingStopStatus.pending,
  etaMinutos: 9,
  confirmadoPresenca: true,
  proximaParada: true,
  passageiroId: 42,
  passageiroNome: 'Aluno A',
  menorDeIdade: false,
);

const _secondStop = DriverTripStopRecord(
  id: 12,
  ordem: 2,
  status: TripTrackingStopStatus.pending,
  etaMinutos: 14,
  confirmadoPresenca: false,
  proximaParada: false,
  passageiroId: 43,
  passageiroNome: 'Aluno B',
  menorDeIdade: false,
);

final _plannedTrip = DriverTripRecord(
  id: 91,
  rotaBaseId: 4,
  rotaNome: 'Rota Centro',
  servicoNome: 'Linha Manha',
  tipoOperacao: 'IDA',
  dataOperacao: _operationDate,
  status: TripTrackingStatus.planned,
  rotaVersao: 2,
  etaMinutosAteProximaParada: 9,
  etaAtualizadoEm: null,
  motorista: null,
  proximaParada: _stop,
  paradas: const [_stop],
  rotaGeografica: null,
  trackingState: const TripTrackingStateRecord(
    routeSnapshotStatus: TripRouteSnapshotStatus.materialized,
    locationFreshness: TripLocationFreshness.live,
    etaConfidence: TripEtaConfidence.high,
    stopsRemaining: 1,
    stopsCompleted: 0,
    routeRecomputeReason: TripRouteRecomputeReason.none,
  ),
  horarioPartida: '07:30',
);

final _operationDate = DateTime(2026, 3, 13);
