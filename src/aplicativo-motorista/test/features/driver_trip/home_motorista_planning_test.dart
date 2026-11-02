import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/domain/driver_trip_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_record.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/presentation/home_motorista_screen.dart';

import '../../support/fakes/fake_account_profile_gateway.dart';
import 'home_motorista_test_support.dart';

void main() {
  setUpAll(() async {
    await initializeDateFormatting('pt_BR');
  });

  testWidgets('abre viagem do deep link quando motorista tem agenda no dia', (
    tester,
  ) async {
    await setPhoneViewport(tester);
    final manha = sampleDriverTripRecord(
      id: 11,
      passageiroNome: 'Ana Manha',
      horarioPartida: '07:00',
      status: TripTrackingStatus.inProgress,
    );
    final tarde = sampleDriverTripRecord(
      id: 42,
      passageiroNome: 'Bruno Tarde',
      horarioPartida: '13:00',
      status: TripTrackingStatus.planned,
    );

    await pumpHomeMotorista(
      tester,
      accessToken: jwtWithCompanyId(66),
      initialTripId: 42,
      tripGateway: FakeDriverTripGateway(manha, dayTrips: [manha, tarde]),
    );

    await tester.pumpAndSettle();

    expect(find.byKey(HomeMotoristaScreen.agendaViagensKey), findsOneWidget);
    final linkedTripChip = tester.widget<ChoiceChip>(
      find.byKey(HomeMotoristaScreen.selecionarViagemDiaButtonKey(42)),
    );
    expect(linkedTripChip.selected, isTrue);
  });

  testWidgets(
    'avisa quando viagem do deep link nao esta na agenda do motorista',
    (tester) async {
      await setPhoneViewport(tester);
      final manha = sampleDriverTripRecord(
        id: 11,
        passageiroNome: 'Ana Manha',
        horarioPartida: '07:00',
        status: TripTrackingStatus.inProgress,
      );
      final tarde = sampleDriverTripRecord(
        id: 42,
        passageiroNome: 'Bruno Tarde',
        horarioPartida: '13:00',
        status: TripTrackingStatus.planned,
      );

      await pumpHomeMotorista(
        tester,
        accessToken: jwtWithCompanyId(66),
        initialTripId: 99,
        tripGateway: FakeDriverTripGateway(manha, dayTrips: [manha, tarde]),
      );

      await tester.pumpAndSettle();

      expect(find.text('Viagem não encontrada.'), findsOneWidget);
      final fallbackTripChip = tester.widget<ChoiceChip>(
        find.byKey(HomeMotoristaScreen.selecionarViagemDiaButtonKey(11)),
      );
      expect(fallbackTripChip.selected, isTrue);
    },
  );

  testWidgets('mostra tipo da operacao na viagem unica do motorista', (
    tester,
  ) async {
    await setPhoneViewport(tester);
    final trip = sampleDriverTripRecord(
      id: 42,
      passageiroNome: 'Bruno Tarde',
      horarioPartida: '13:00',
      tipoOperacao: 'VOLTA',
    );

    await pumpHomeMotorista(
      tester,
      accessToken: jwtWithCompanyId(66),
      tripGateway: FakeDriverTripGateway(trip),
    );

    await tester.pumpAndSettle();

    expect(find.text('Linha 13:00'), findsOneWidget);
    expect(find.text('Volta'), findsOneWidget);
  });

  testWidgets('conta do motorista usa resumo real da operacao', (tester) async {
    await setPhoneViewport(tester);
    final trip = DriverTripRecord(
      id: 11,
      rotaBaseId: 4,
      rotaNome: 'Rota Centro',
      servicoNome: 'Linha Manha',
      tipoOperacao: 'IDA',
      horarioPartida: '07:00',
      dataOperacao: DateTime(2026, 3, 13),
      status: TripTrackingStatus.planned,
      rotaVersao: 2,
      etaMinutosAteProximaParada: 9,
      etaAtualizadoEm: DateTime(2026, 3, 18, 23, 38),
      motorista: const TripTrackingDriverRecord(id: 7, nome: 'Motorista QA'),
      proximaParada: const DriverTripStopRecord(
        id: 80,
        ordem: 1,
        status: TripTrackingStopStatus.pending,
        etaMinutos: 9,
        confirmadoPresenca: false,
        proximaParada: true,
        passageiroId: 30,
        passageiroNome: 'Ana',
        menorDeIdade: true,
      ),
      paradas: const [
        DriverTripStopRecord(
          id: 80,
          ordem: 1,
          status: TripTrackingStopStatus.pending,
          etaMinutos: 9,
          confirmadoPresenca: false,
          proximaParada: true,
          passageiroId: 30,
          passageiroNome: 'Ana',
          menorDeIdade: true,
        ),
        DriverTripStopRecord(
          id: 90,
          ordem: 2,
          status: TripTrackingStopStatus.pending,
          etaMinutos: 15,
          confirmadoPresenca: true,
          proximaParada: false,
          passageiroId: 40,
          passageiroNome: 'Joao',
          menorDeIdade: false,
        ),
      ],
      rotaGeografica: sampleDriverGeometry(),
    );

    await pumpHomeMotorista(
      tester,
      tripGateway: FakeDriverTripGateway(trip, dayTrips: [trip]),
      accountProfileGateway: const FakeAccountProfileGateway.driver(),
    );

    await tester.pumpAndSettle();
    await tester.tap(find.text('Conta'));
    await tester.pumpAndSettle();

    expect(find.text('Maria Perfil Real'), findsOneWidget);
    expect(find.text('maria.real@vanrides.dev'), findsWidgets);
    expect(find.text('E-mail verificado'), findsOneWidget);
    expect(find.text('15/01/1990'), findsOneWidget);
    expect(find.text('51999990007'), findsOneWidget);
    expect(find.text('1 viagem hoje'), findsNothing);
    expect(find.text('2 paradas hoje'), findsNothing);
    expect(find.text('98.4%'), findsNothing);
    expect(find.text('4.96'), findsNothing);
    expect(find.text('On-time rate'), findsNothing);
    expect(find.text('Driver rating'), findsNothing);
    expect(find.text('Shift History'), findsNothing);
    expect(find.text('Vehicle Info'), findsNothing);
    expect(find.text('Support'), findsNothing);
  });
}
