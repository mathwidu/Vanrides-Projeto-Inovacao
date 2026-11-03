import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:vanrides_frontend_dev/features/auth/presentation/login_screen.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/home_passageiro_screen.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/trip_live_map_panel.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/widgets/passenger_home_account_widgets.dart';

import '../support/fakes/fake_account_profile_gateway.dart';
import '../support/fakes/fake_trip_route_event_gateway.dart';
import '../support/fakes/fake_websocket_service.dart';
import '../support/passenger_home_test_support.dart';

void main() {
  setUpAll(() async {
    await initializeDateFormatting('pt_BR');
  });

  testWidgets('logout do passageiro limpa sessao persistida', (tester) async {
    SharedPreferences.setMockInitialValues({
      'session.userId': 21,
      'session.displayName': 'Ana',
      'session.email': 'ana@vanrides.dev',
      'session.tipoUsuario': 'Passageiro',
      'session.accessToken': 'token.passageiro',
      'session.refreshToken': 'refresh.passageiro',
    });

    await tester.pumpWidget(
      MaterialApp(
        home: HomePassageiroScreen(
          nome: 'Ana',
          idPassageiro: 21,
          motoristaId: 7,
          email: 'ana@vanrides.dev',
          senha: 'Secret123',
          accessToken: 'token.passageiro',
          trackingGateway: const FakePassengerTripTrackingGateway(null),
          tripRouteEventGateway: FakeTripRouteEventGateway(),
          liveLocationGateway: const FakeTripLiveLocationGateway(null),
          liveLocationWebSocketService: FakeWebSocketService(),
          accountProfileGateway: const FakeAccountProfileGateway.passenger(),
          mapRenderMode: TripLiveMapRenderMode.placeholder,
        ),
      ),
    );

    await tester.pumpAndSettle();
    tester
        .widget<PassengerHomeAccountTab>(
          find.byType(PassengerHomeAccountTab, skipOffstage: false),
        )
        .onLogout();
    await tester.pumpAndSettle();

    final prefs = await SharedPreferences.getInstance();
    expect(prefs.getString('session.accessToken'), isNull);
    expect(prefs.getString('session.refreshToken'), isNull);
    expect(prefs.getInt('session.userId'), isNull);
    expect(find.byType(LoginScreen), findsOneWidget);
  });
}
