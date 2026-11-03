import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/domain/driver_trip_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_record.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/presentation/home_motorista_screen.dart';

import 'home_motorista_test_support.dart';

void main() {
  setUpAll(() async {
    await initializeDateFormatting('pt_BR');
  });

  testWidgets('permite editar e salvar a ordem operacional no planejamento', (
    tester,
  ) async {
    await setPhoneViewport(tester);
    final gateway = FakeDriverTripGateway(
      DriverTripRecord(
        id: 11,
        rotaBaseId: 4,
        rotaNome: 'Rota Centro',
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
            confirmadoPresenca: false,
            proximaParada: false,
            passageiroId: 40,
            passageiroNome: 'Joao',
            menorDeIdade: false,
          ),
        ],
        rotaGeografica: sampleDriverGeometry(),
      ),
      reorderedRecord: DriverTripRecord(
        id: 11,
        rotaBaseId: 4,
        rotaNome: 'Rota Centro',
        dataOperacao: DateTime(2026, 3, 13),
        status: TripTrackingStatus.planned,
        rotaVersao: 3,
        etaMinutosAteProximaParada: 6,
        etaAtualizadoEm: DateTime(2026, 3, 18, 23, 44),
        motorista: const TripTrackingDriverRecord(id: 7, nome: 'Motorista QA'),
        proximaParada: const DriverTripStopRecord(
          id: 90,
          ordem: 1,
          status: TripTrackingStopStatus.pending,
          etaMinutos: 6,
          confirmadoPresenca: false,
          proximaParada: true,
          passageiroId: 40,
          passageiroNome: 'Joao',
          menorDeIdade: false,
        ),
        paradas: const [
          DriverTripStopRecord(
            id: 90,
            ordem: 1,
            status: TripTrackingStopStatus.pending,
            etaMinutos: 6,
            confirmadoPresenca: false,
            proximaParada: true,
            passageiroId: 40,
            passageiroNome: 'Joao',
            menorDeIdade: false,
          ),
          DriverTripStopRecord(
            id: 80,
            ordem: 2,
            status: TripTrackingStopStatus.pending,
            etaMinutos: 12,
            confirmadoPresenca: false,
            proximaParada: false,
            passageiroId: 30,
            passageiroNome: 'Ana',
            menorDeIdade: true,
          ),
        ],
        rotaGeografica: sampleDriverGeometry(),
      ),
    );

    await pumpHomeMotorista(tester, tripGateway: gateway);

    await tester.pumpAndSettle();
    await scrollToAndEnsureVisible(
      tester,
      find.byKey(HomeMotoristaScreen.editarRotaButtonKey),
    );
    await invokeButton(
      tester,
      find.byKey(HomeMotoristaScreen.editarRotaButtonKey),
    );

    expect(find.byKey(HomeMotoristaScreen.salvarRotaButtonKey), findsOneWidget);

    await scrollToAndEnsureVisible(tester, find.byType(ReorderableListView));
    await tester.drag(
      find.byType(ReorderableListView),
      const Offset(0, -120),
      warnIfMissed: false,
    );
    await tester.pumpAndSettle();

    await scrollToAndEnsureVisible(
      tester,
      find.byKey(HomeMotoristaScreen.salvarRotaButtonKey),
    );
    await tester.tap(find.byKey(HomeMotoristaScreen.salvarRotaButtonKey));
    await tester.pumpAndSettle();

    expect(gateway.lastTripId, 11);
    expect(gateway.lastReorderStopIds, isNotNull);
    expect(find.byKey(HomeMotoristaScreen.salvarRotaButtonKey), findsNothing);
  });
}
