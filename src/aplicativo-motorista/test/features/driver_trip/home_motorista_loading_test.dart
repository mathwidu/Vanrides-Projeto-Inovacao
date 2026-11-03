import 'package:flutter_test/flutter_test.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:vanrides_frontend_dev/features/auth/presentation/login_screen.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/presentation/home_motorista_screen.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/presentation/widgets/driver_home_account_widgets.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/map_first_v2_shell.dart';

import '../../support/fakes/fake_account_profile_gateway.dart';
import 'home_motorista_test_support.dart';

void main() {
  setUpAll(() async {
    await initializeDateFormatting('pt_BR');
  });

  testWidgets('mostra estado vazio quando motorista nao possui viagem', (
    tester,
  ) async {
    await setPhoneViewport(tester);
    await pumpHomeMotorista(tester, tripGateway: FakeDriverTripGateway(null));

    await tester.pumpAndSettle();

    expect(find.text('Sem viagem publicada hoje'), findsOneWidget);
    expect(
      find.byKey(HomeMotoristaScreen.seguirNavegacaoButtonKey),
      findsNothing,
    );
  });

  testWidgets('logout do motorista limpa sessao persistida', (tester) async {
    await setPhoneViewport(tester);
    SharedPreferences.setMockInitialValues({
      'session.userId': 7,
      'session.displayName': 'Motorista QA',
      'session.email': 'motorista@vanrides.dev',
      'session.tipoUsuario': 'Motorista',
      'session.accessToken': 'token.motorista',
      'session.refreshToken': 'refresh.motorista',
    });

    await pumpHomeMotorista(
      tester,
      tripGateway: FakeDriverTripGateway(null),
      accessToken: 'token.motorista',
      accountProfileGateway: const FakeAccountProfileGateway.driver(),
    );

    await tester.pumpAndSettle();
    tester
        .widget<MapFirstBottomNavBar>(find.byType(MapFirstBottomNavBar))
        .onDestinationSelected(3);
    await tester.pumpAndSettle();
    tester
        .widget<DriverAccountTab>(
          find.byType(DriverAccountTab, skipOffstage: false),
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
