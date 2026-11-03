import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_state_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/trip_live_map_panel.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/widgets/passenger_home_trip_widgets.dart';

void main() {
  testWidgets('paused trip shows signal state without promising ETA', (
    tester,
  ) async {
    final trip = _trip(
      freshness: TripLocationFreshness.paused,
      status: TripTrackingStatus.inProgress,
    );

    await _pumpScene(tester, trip: trip, dayTrips: [trip]);

    expect(find.textContaining(RegExp('Sinal pausado|Pausado')), findsWidgets);
    expect(find.text('Envio pausado'), findsOneWidget);
    expect(
      find.text('Acompanhe pela última posição até o envio voltar.'),
      findsOneWidget,
    );
    expect(find.text('Atualizar sinal'), findsOneWidget);
    expect(find.textContaining('Chega em'), findsNothing);
    expect(find.text('Detalhes'), findsNothing);
    expect(find.text('Minha parada'), findsOneWidget);
    expect(find.text('Mais'), findsNothing);
    expect(find.text('Rota'), findsNothing);
    expect(find.text('Presença'), findsOneWidget);

    final sheet = tester.widget<DraggableScrollableSheet>(
      find.byType(DraggableScrollableSheet),
    );
    expect(sheet.initialChildSize, closeTo(0.34, 0.001));
    expect(sheet.snapSizes, const [0.18, 0.34, 0.86]);
  });

  testWidgets('live trip keeps complete critical labels in compact sheet', (
    tester,
  ) async {
    final trip = _trip(
      freshness: TripLocationFreshness.live,
      status: TripTrackingStatus.inProgress,
    );

    await _pumpScene(tester, trip: trip, dayTrips: [trip]);

    expect(find.text('Van a caminho'), findsOneWidget);
    expect(find.text('Faltam 2 paradas'), findsOneWidget);
    expect(find.textContaining(RegExp('Sinal ao vivo|Ao vivo')), findsWidgets);
    expect(find.text('Chega e...'), findsNothing);
    expect(find.text('Van'), findsNothing);
    expect(find.text('Voce'), findsNothing);
    expect(find.text('Detalhes'), findsNothing);
    expect(find.text('Minha parada'), findsOneWidget);
    expect(find.text('Mais'), findsNothing);
  });

  testWidgets('tracking narrative uses human trip states', (tester) async {
    final planned = _trip(
      freshness: TripLocationFreshness.paused,
      status: TripTrackingStatus.planned,
    );
    await _pumpScene(tester, trip: planned, dayTrips: [planned]);
    expect(find.text('Van ainda não iniciou'), findsOneWidget);
    expect(find.text('Aguardando inicio'), findsNothing);

    final live = _trip(
      freshness: TripLocationFreshness.live,
      status: TripTrackingStatus.inProgress,
    );
    await _pumpScene(tester, trip: live, dayTrips: [live]);
    expect(find.text('Van a caminho'), findsOneWidget);
    expect(find.text('Faltam 2 paradas'), findsOneWidget);
    expect(find.text('Chega em cerca de 12 min'), findsNothing);

    final nextStop = _trip(
      freshness: TripLocationFreshness.live,
      status: TripTrackingStatus.inProgress,
      stopsBefore: 0,
    );
    await _pumpScene(tester, trip: nextStop, dayTrips: [nextStop]);
    expect(find.text('Sua parada é a próxima'), findsOneWidget);

    final unavailable = _trip(
      freshness: TripLocationFreshness.unavailable,
      status: TripTrackingStatus.inProgress,
    );
    await _pumpScene(tester, trip: unavailable, dayTrips: [unavailable]);
    expect(find.text('Aguardando posição da van'), findsOneWidget);
    expect(find.text('Atualizar sinal'), findsOneWidget);
    expect(find.text('Sem sinal da van'), findsNothing);

    final completed = _trip(
      freshness: TripLocationFreshness.live,
      status: TripTrackingStatus.completed,
    );
    await _pumpScene(tester, trip: completed, dayTrips: [completed]);
    expect(find.text('Viagem encerrada'), findsOneWidget);
  });

  testWidgets('unavailable trip uses short signal pill label', (tester) async {
    final trip = _trip(
      freshness: TripLocationFreshness.unavailable,
      status: TripTrackingStatus.inProgress,
    );

    await _pumpScene(tester, trip: trip, dayTrips: [trip]);

    expect(find.text('Sem sinal'), findsOneWidget);
    expect(find.text('Aguardando posição da van'), findsOneWidget);
    expect(find.text('Atualizar sinal'), findsOneWidget);
    expect(find.textContaining('Aguardando si...'), findsNothing);
  });

  testWidgets('stale trip explains old position without technical wording', (
    tester,
  ) async {
    final trip = _trip(
      freshness: TripLocationFreshness.stale,
      status: TripTrackingStatus.inProgress,
      driverLocationUpdatedAt: DateTime(2026, 6, 15, 7, 8),
    );

    await _pumpScene(tester, trip: trip, dayTrips: [trip]);

    expect(find.text('Posição antiga'), findsWidgets);
    expect(find.text('Último estado útil às 07:08'), findsOneWidget);
    expect(find.text('Atualizar sinal'), findsOneWidget);
    expect(find.text('Aguardando atualização'), findsNothing);
  });
}

Future<void> _pumpScene(
  WidgetTester tester, {
  required TripTrackingRecord trip,
  required List<TripTrackingRecord> dayTrips,
}) async {
  await tester.pumpWidget(
    MaterialApp(
      home: Scaffold(
        body: SizedBox(
          width: 390,
          height: 800,
          child: PassengerHomeMapScene(
            trip: trip,
            dayTrips: dayTrips,
            selectedTripId: trip.id,
            effectiveMotoristaId: 7,
            email: 'passageiro@vanrides.dev',
            senha: 'senha',
            accessToken: 'token',
            liveLocationGateway: null,
            liveLocationWebSocketService: null,
            mapRenderMode: TripLiveMapRenderMode.placeholder,
            onRefresh: () {},
            onTripSelected: (_) {},
            onOpenTripDetails: () {},
            onOpenMap: () {},
            onOpenPresence: () {},
          ),
        ),
      ),
    ),
  );

  await tester.pumpAndSettle();
}

TripTrackingRecord _trip({
  required TripLocationFreshness freshness,
  required TripTrackingStatus status,
  int stopsBefore = 2,
  DateTime? driverLocationUpdatedAt,
}) {
  return TripTrackingRecord(
    id: 30,
    rotaNome: 'Rota Escolar',
    servicoId: 130,
    servicoNome: 'feevale',
    tipoOperacao: 'IDA',
    dataOperacao: DateTime(2026, 6, 15),
    status: status,
    rotaVersao: 1,
    etaMinutosAtePassageiro: 12,
    etaAtualizadoEm: DateTime(2026, 6, 15, 7, 10),
    motorista: const TripTrackingDriverRecord(id: 7, nome: 'Motorista Portal'),
    rotaGeografica: null,
    paradaDoPassageiro: const TripTrackingPassengerStopRecord(
      id: 330,
      ordem: 1,
      status: TripTrackingStopStatus.pending,
      etaMinutos: 12,
      confirmadoPresenca: true,
      proximaParada: true,
      passageiroId: 21,
      passageiroNome: 'Matheus Duarte',
      menorDeIdade: false,
    ),
    horarioPartida: '07:25',
    trackingState: TripTrackingStateRecord(
      routeSnapshotStatus: TripRouteSnapshotStatus.materialized,
      locationFreshness: freshness,
      etaConfidence: TripEtaConfidence.high,
      stopsRemaining: stopsBefore + 1,
      stopsCompleted: 1,
      stopsBeforePassenger: stopsBefore,
      routeRecomputeReason: TripRouteRecomputeReason.none,
      driverLocationUpdatedAt: driverLocationUpdatedAt,
    ),
  );
}
