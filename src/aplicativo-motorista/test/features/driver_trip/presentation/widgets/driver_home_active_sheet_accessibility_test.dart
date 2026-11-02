import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/domain/driver_trip_record.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/presentation/widgets/driver_home_trip_widgets.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_state_record.dart';

void main() {
  testWidgets('Driver home active sheet tolerates high text scale', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: MediaQuery(
          data: const MediaQueryData(
            size: Size(390, 844),
            textScaler: TextScaler.linear(1.6),
          ),
          child: Scaffold(
            body: SingleChildScrollView(
              child: SizedBox(
                width: 390,
                child: DriverHomeActiveSheet(
                  trip: _activeTrip,
                  nextStop: _activeTrip.proximaParada,
                  dayTrips: const [],
                  selectedTripId: _activeTrip.id,
                  agendaKey: const Key('driver.agenda'),
                  tripKeyBuilder: (id) => Key('driver.trip.$id'),
                  completeStopKey: const Key('driver.complete'),
                  navigationKey: const Key('driver.navigation'),
                  skipStopKey: const Key('driver.skip'),
                  noShowKey: const Key('driver.noShow'),
                  endTripKey: const Key('driver.end'),
                  sharingLocation: true,
                  openingNavigation: false,
                  runningLifecycleAction: false,
                  onSelectTrip: (_) {},
                  onCompleteStop: () {},
                  onOpenNavigation: () {},
                  onSkipStop: () {},
                  onNoShowStop: () {},
                  onEndTrip: () {},
                ),
              ),
            ),
          ),
        ),
      ),
    );

    expect(find.text('Transmitindo ao vivo'), findsOneWidget);
    expect(find.text('Proximo passageiro'), findsOneWidget);
    expect(find.text('Confirmar embarque'), findsWidgets);
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'Driver home active sheet keeps first decision visible on small iPhone',
    (tester) async {
      await tester.binding.setSurfaceSize(const Size(360, 640));
      addTearDown(() => tester.binding.setSurfaceSize(null));

      await tester.pumpWidget(
        MaterialApp(
          home: MediaQuery(
            data: const MediaQueryData(
              size: Size(360, 640),
              textScaler: TextScaler.linear(1.35),
            ),
            child: Scaffold(
              body: SingleChildScrollView(
                child: SizedBox(
                  width: 360,
                  child: DriverHomeActiveSheet(
                    trip: _activeTrip,
                    nextStop: _activeTrip.proximaParada,
                    dayTrips: const [],
                    selectedTripId: _activeTrip.id,
                    agendaKey: const Key('driver.agenda'),
                    tripKeyBuilder: (id) => Key('driver.trip.$id'),
                    completeStopKey: const Key('driver.complete'),
                    navigationKey: const Key('driver.navigation'),
                    skipStopKey: const Key('driver.skip'),
                    noShowKey: const Key('driver.noShow'),
                    endTripKey: const Key('driver.end'),
                    sharingLocation: true,
                    openingNavigation: false,
                    runningLifecycleAction: false,
                    onSelectTrip: (_) {},
                    onCompleteStop: () {},
                    onOpenNavigation: () {},
                    onSkipStop: () {},
                    onNoShowStop: () {},
                    onEndTrip: () {},
                  ),
                ),
              ),
            ),
          ),
        ),
      );

      expect(find.text('Transmitindo ao vivo'), findsOneWidget);
      expect(find.text('Ida'), findsOneWidget);
      expect(find.text('Aluno A'), findsOneWidget);
      expect(find.text('9 min'), findsOneWidget);
      expect(find.text('Proxima acao'), findsOneWidget);
      expect(find.text('Confirmar embarque'), findsWidgets);
      expect(find.text('Fallback recomendado'), findsOneWidget);
      expect(find.text('Acoes de revisao'), findsOneWidget);
      expect(
        find.text('Use apenas quando a parada mudar durante a corrida.'),
        findsOneWidget,
      );
      expect(
        tester.getTopLeft(find.text('Transmitindo ao vivo')).dy,
        lessThan(tester.getTopLeft(find.text('Proxima acao')).dy),
      );
      expect(
        tester.getTopLeft(find.text('Proxima acao')).dy,
        lessThan(tester.getTopLeft(find.text('Fallback recomendado')).dy),
      );
      expect(
        tester.getTopLeft(find.text('Aluno A').first).dy,
        lessThan(tester.getTopLeft(find.text('Acoes de revisao')).dy),
      );
      expect(tester.takeException(), isNull);
    },
  );
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

final _activeTrip = DriverTripRecord(
  id: 93,
  rotaBaseId: 4,
  rotaNome: 'Rota Centro',
  servicoNome: 'Linha Manha',
  tipoOperacao: 'IDA',
  dataOperacao: DateTime(2026, 3, 13),
  status: TripTrackingStatus.inProgress,
  rotaVersao: 2,
  etaMinutosAteProximaParada: 9,
  etaAtualizadoEm: null,
  motorista: null,
  proximaParada: _stop,
  paradas: [_stop],
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
