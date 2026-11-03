import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_route_geometry_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_state_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/passenger_trip_details_screen.dart';

void main() {
  testWidgets('renders operational details as secondary trip layer', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(home: PassengerTripDetailsScreen(trip: _trip())),
    );

    expect(find.text('Detalhes da viagem'), findsOneWidget);
    expect(find.text('Resumo operacional'), findsOneWidget);
    expect(find.text('07:25 • Ida'), findsWidgets);
    expect(find.text('Status de presença'), findsOneWidget);
    expect(find.text('Confirmada'), findsOneWidget);
    expect(find.text('Minha parada'), findsWidgets);
    expect(find.text('Parada 2'), findsOneWidget);
    expect(find.text('Antes de voce'), findsOneWidget);
    expect(find.text('Garagem operacional'), findsOneWidget);
    expect(find.text('Depois de voce'), findsOneWidget);
    expect(find.text('Feevale'), findsOneWidget);

    await tester.scrollUntilVisible(
      find.byKey(const Key('passenger.trip.details.supportAction')),
      320,
    );
    await tester.pumpAndSettle();

    expect(find.text('Suporte da viagem'), findsWidgets);
  });

  testWidgets('support action returns open support intent to caller', (
    tester,
  ) async {
    PassengerTripDetailsAction? action;

    await tester.pumpWidget(
      MaterialApp(
        home: Builder(
          builder:
              (context) => Scaffold(
                body: Center(
                  child: ElevatedButton(
                    key: const Key('open.details'),
                    onPressed: () async {
                      action = await Navigator.of(
                        context,
                      ).push<PassengerTripDetailsAction>(
                        MaterialPageRoute(
                          builder:
                              (_) => PassengerTripDetailsScreen(trip: _trip()),
                        ),
                      );
                    },
                    child: const Text('Abrir detalhes'),
                  ),
                ),
              ),
        ),
      ),
    );

    await tester.tap(find.byKey(const Key('open.details')));
    await tester.pumpAndSettle();

    await tester.scrollUntilVisible(
      find.byKey(const Key('passenger.trip.details.supportAction')),
      320,
    );
    await tester.pumpAndSettle();

    await tester.tap(
      find.byKey(const Key('passenger.trip.details.supportAction')),
    );
    await tester.pumpAndSettle();

    expect(action, PassengerTripDetailsAction.openSupport);
    expect(find.text('Abrir detalhes'), findsOneWidget);
  });
}

TripTrackingRecord _trip() {
  return TripTrackingRecord(
    id: 42,
    rotaNome: 'Rota Escolar',
    servicoId: 15,
    servicoNome: 'Feevale matutino',
    tipoOperacao: 'IDA',
    dataOperacao: DateTime(2026, 6, 19),
    status: TripTrackingStatus.inProgress,
    rotaVersao: 3,
    etaMinutosAtePassageiro: 8,
    etaAtualizadoEm: DateTime(2026, 6, 19, 7, 17),
    motorista: const TripTrackingDriverRecord(
      id: 7,
      nome: 'Matheus Motorista',
      telefone: '51999990000',
    ),
    paradaDoPassageiro: const TripTrackingPassengerStopRecord(
      id: 302,
      ordem: 2,
      status: TripTrackingStopStatus.pending,
      etaMinutos: 8,
      confirmadoPresenca: true,
      proximaParada: true,
      passageiroId: 21,
      passageiroNome: 'Ana Escola',
      menorDeIdade: true,
    ),
    rotaGeografica: const TripRouteGeometryRecord(
      provider: 'GOOGLE_DIRECTIONS_V1',
      polyline: '',
      navegacao: null,
      waypoints: [
        TripRouteWaypointRecord(
          tipo: 'ORIGIN',
          paradaId: 301,
          passageiroId: null,
          nome: 'Garagem operacional',
          latitude: -29.68,
          longitude: -51.13,
          ordem: 1,
          proximaParada: false,
          paradaDoPassageiro: false,
        ),
        TripRouteWaypointRecord(
          tipo: 'STOP',
          paradaId: 302,
          passageiroId: 21,
          nome: 'Ana Escola',
          latitude: -29.69,
          longitude: -51.14,
          ordem: 2,
          proximaParada: true,
          paradaDoPassageiro: true,
        ),
        TripRouteWaypointRecord(
          tipo: 'DESTINATION',
          paradaId: 303,
          passageiroId: null,
          nome: 'Feevale',
          latitude: -29.70,
          longitude: -51.15,
          ordem: 3,
          proximaParada: false,
          paradaDoPassageiro: false,
        ),
      ],
      legs: [],
      filaTotalParadas: 3,
      posicaoPassageiroNaFila: 2,
      paradasAntesDoPassageiro: 1,
    ),
    trackingState: const TripTrackingStateRecord(
      routeSnapshotStatus: TripRouteSnapshotStatus.materialized,
      locationFreshness: TripLocationFreshness.live,
      etaConfidence: TripEtaConfidence.high,
      stopsRemaining: 2,
      stopsCompleted: 1,
      stopsBeforePassenger: 1,
      routeRecomputeReason: TripRouteRecomputeReason.none,
    ),
    horarioPartida: '07:25',
  );
}
