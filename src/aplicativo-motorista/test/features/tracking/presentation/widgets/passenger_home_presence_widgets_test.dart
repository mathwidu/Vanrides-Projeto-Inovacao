import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/app/widgets/vanrides_liquid_surface.dart';
import 'package:vanrides_frontend_dev/features/service_presence/domain/passenger_available_service_record.dart';
import 'package:vanrides_frontend_dev/features/service_presence/presentation/presence_today_list.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/widgets/passenger_home_presence_widgets.dart';

TripTrackingRecord _trip() {
  return TripTrackingRecord(
    id: 30,
    rotaNome: 'Rota Escolar',
    servicoId: 130,
    servicoNome: 'Linha Manha',
    tipoOperacao: 'IDA',
    dataOperacao: DateTime(2026, 3, 20),
    status: TripTrackingStatus.inProgress,
    rotaVersao: 1,
    etaMinutosAtePassageiro: 12,
    etaAtualizadoEm: DateTime(2026, 3, 20, 7, 30),
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
      passageiroNome: 'Ana Escola',
      menorDeIdade: true,
    ),
    horarioPartida: '07:30',
  );
}

PassengerAvailableServiceRecord _service({
  required int id,
  required String nome,
  String status = 'UNKNOWN',
  int? presencaId,
}) {
  return PassengerAvailableServiceRecord(
    servicoId: id,
    nome: nome,
    tipoOperacao: 'IDA',
    dataOperacao: DateTime(2026, 3, 20),
    horarioPartida: '07:30',
    destinoFinalNome: 'Escola Central',
    politicaPresenca: 'EXIGE_CONFIRMACAO',
    presencaId: presencaId,
    presencaStatus: status,
  );
}

void main() {
  testWidgets('fallback de presenca aciona confirmacao externa', (
    tester,
  ) async {
    var openPresenceCount = 0;

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: PassengerHomePresenceTab(
            trip: null,
            dayTrips: const [],
            presenceServices: const [],
            loadingPresenceServices: false,
            processingPresenceService: false,
            presenceErrorMessage: 'Nenhum servico liberado hoje.',
            onTripSelected: (_) {},
            onConfirmService: (_) {},
            onAbsentService: (_) {},
            onClearService: (_) {},
            onOpenPresence: () => openPresenceCount++,
            onOpenTripDetails: () {},
          ),
        ),
      ),
    );

    expect(find.text('Presença de hoje'), findsOneWidget);
    expect(find.byKey(PassengerHomePresenceTab.backdropKey), findsOneWidget);
    expect(find.byKey(PassengerHomePresenceTab.heroGlassKey), findsOneWidget);
    expect(
      tester
          .widget<VanRidesLiquidSurface>(
            find.byKey(PassengerHomePresenceTab.heroGlassKey),
          )
          .variant,
      VanRidesLiquidSurfaceVariant.sheet,
    );
    expect(find.text('Nenhum servico liberado hoje.'), findsOneWidget);
    expect(
      find.text('Abra presença para revisar os horários do dia.'),
      findsOneWidget,
    );
    expect(find.text('Abrir presença'), findsOneWidget);
    expect(find.text('Vou embarcar'), findsNothing);
    expect(find.text('Não vou embarcar'), findsNothing);

    await tester.tap(find.text('Abrir presença'));
    await tester.pump();

    expect(openPresenceCount, 1);
  });

  testWidgets('lista servicos liberados preservando callbacks recebidos', (
    tester,
  ) async {
    final service = _service(id: 15, nome: 'Linha Manha');
    PassengerAvailableServiceRecord? confirmedService;

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: PassengerHomePresenceTab(
            trip: _trip(),
            dayTrips: const [],
            presenceServices: [service],
            loadingPresenceServices: false,
            processingPresenceService: false,
            presenceErrorMessage: null,
            onTripSelected: (_) {},
            onConfirmService: (value) => confirmedService = value,
            onAbsentService: (_) {},
            onClearService: (_) {},
            onOpenPresence: () {},
            onOpenTripDetails: () {},
          ),
        ),
      ),
    );

    expect(find.text('Presença de hoje'), findsOneWidget);
    expect(find.byKey(PassengerHomePresenceTab.heroGlassKey), findsNothing);
    expect(find.byKey(PresenceTodayList.darkSurfaceKey), findsOneWidget);
    expect(
      tester
          .widget<VanRidesLiquidSurface>(
            find.byKey(PresenceTodayList.darkSurfaceKey),
          )
          .variant,
      VanRidesLiquidSurfaceVariant.sheet,
    );
    expect(find.text('Linha Manha'), findsOneWidget);
    expect(find.text('Pendente'), findsOneWidget);

    await tester.tap(find.byKey(const ValueKey('service-presence.confirm.15')));
    await tester.pump();

    expect(confirmedService, same(service));
  });
}
