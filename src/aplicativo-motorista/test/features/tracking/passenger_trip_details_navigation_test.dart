import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/home_passageiro_screen.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/trip_live_map_panel.dart';

import '../../support/fakes/fake_account_profile_gateway.dart';
import '../../support/fakes/fake_trip_route_event_gateway.dart';
import '../../support/fakes/fake_websocket_service.dart';
import '../../support/passenger_home_test_support.dart';

void main() {
  testWidgets('detalhes da viagem abre suporte retornando para conta', (
    tester,
  ) async {
    final trip = samplePassengerTripRecord(
      id: 30,
      servicoNome: 'Linha Manha',
      tipoOperacao: 'IDA',
      horarioPartida: '07:30',
      passageiroNome: 'Ana',
      status: TripTrackingStatus.inProgress,
    );

    await tester.pumpWidget(
      MaterialApp(
        home: HomePassageiroScreen(
          nome: 'Ana',
          idPassageiro: 21,
          motoristaId: 7,
          email: 'ana@vanrides.dev',
          senha: 'Secret123',
          accessToken: 'token.passageiro',
          accountProfileGateway: const FakeAccountProfileGateway.passenger(),
          trackingGateway: FakePassengerTripTrackingGateway(trip),
          tripRouteEventGateway: FakeTripRouteEventGateway(),
          liveLocationGateway: const FakeTripLiveLocationGateway(null),
          liveLocationWebSocketService: FakeWebSocketService(),
          mapRenderMode: TripLiveMapRenderMode.placeholder,
        ),
      ),
    );

    await tester.pumpAndSettle();

    await tester.drag(
      find.byKey(const Key('passenger.trip.sheet')),
      const Offset(0, -240),
    );
    await tester.pumpAndSettle();
    await tester.ensureVisible(
      find.byKey(HomePassageiroScreen.verLocalizacaoButtonKey),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(HomePassageiroScreen.verLocalizacaoButtonKey));
    await tester.pumpAndSettle();

    expect(find.text('Detalhes da viagem'), findsOneWidget);

    await tester.scrollUntilVisible(
      find.byKey(const Key('passenger.trip.details.supportAction')),
      320,
    );
    await tester.pumpAndSettle();
    await tester.tap(
      find.byKey(const Key('passenger.trip.details.supportAction')),
    );
    await tester.pumpAndSettle();

    expect(
      find.byKey(const Key('passenger.account.darkSurface')),
      findsOneWidget,
    );
  });
}
