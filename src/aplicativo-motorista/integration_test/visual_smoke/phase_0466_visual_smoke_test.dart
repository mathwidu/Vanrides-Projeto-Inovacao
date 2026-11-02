import 'package:flutter/foundation.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:vanrides_frontend_dev/app/session/app_session_store.dart';
import 'package:vanrides_frontend_dev/features/guardian/presentation/guardian_localizacao_screen.dart';
import 'package:vanrides_frontend_dev/features/service_presence/presentation/presence_today_list.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/map_first_v2_shell.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/widgets/passenger_map_footer_sheet_scaffold.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/widgets/passenger_home_presence_widgets.dart';
import 'package:vanrides_frontend_dev/l10n/app_strings.dart';

import '../_support/e2e_support.dart';
import '../_support/flows.dart';

const _visualRole = String.fromEnvironment(
  'VANRIDES_VISUAL_ROLE',
  defaultValue: 'passenger',
);
const _driver1Email = String.fromEnvironment(
  'VANRIDES_FIXTURE_DRIVER_1_EMAIL',
  defaultValue: '',
);
const _driverPassword = String.fromEnvironment(
  'VANRIDES_FIXTURE_DRIVER_PASSWORD',
  defaultValue: '',
);
const _passengerEmail = String.fromEnvironment(
  'VANRIDES_FIXTURE_PASSENGER_EMAIL',
  defaultValue: '',
);
const _passengerPassword = String.fromEnvironment(
  'VANRIDES_FIXTURE_PASSENGER_PASSWORD',
  defaultValue: '',
);
const _guardianEmail = String.fromEnvironment(
  'GUARDIAN_E2E_EMAIL',
  defaultValue: '',
);
const _guardianPassword = String.fromEnvironment(
  'GUARDIAN_E2E_PASSWORD',
  defaultValue: '',
);

void main() {
  ensureBinding();

  testWidgets(
    'phase 0466 visual smoke for $_visualRole',
    (tester) async {
      await AppSessionStore().clear();

      switch (_visualRole) {
        case 'passenger':
          await _capturePassenger(tester);
          break;
        case 'driver':
          await _captureDriver(tester);
          break;
        case 'guardian':
          await _captureGuardian(tester);
          break;
        default:
          throw TestFailure(
            'VANRIDES_VISUAL_ROLE invalido: $_visualRole. Use passenger, driver ou guardian.',
          );
      }
    },
    timeout: const Timeout(Duration(minutes: 7)),
  );
}

Future<void> _capturePassenger(WidgetTester tester) async {
  _requireFixture(_passengerEmail, 'VANRIDES_FIXTURE_PASSENGER_EMAIL');
  _requireFixture(_passengerPassword, 'VANRIDES_FIXTURE_PASSENGER_PASSWORD');

  await launchApp(tester);
  await login(
    tester: tester,
    email: _passengerEmail,
    senha: _passengerPassword,
  );
  await pumpUntil(
    tester,
    find.byKey(MapFirstBottomNavItems.itemKey(0)),
    timeout: const Duration(seconds: 60),
  );
  await _visualReady(tester, 'passenger-trip');

  await _tapBottomNav(tester, 1);
  await _pumpUntilAny(tester, [
    find.byKey(PresenceTodayList.darkSurfaceKey),
    find.byKey(PassengerHomePresenceTab.heroGlassKey),
  ], timeout: const Duration(seconds: 60));
  await _visualReady(tester, 'passenger-presence');

  await _tapBottomNav(tester, 2);
  await pumpUntil(
    tester,
    find.byKey(const Key('passenger.account.darkSurface')),
    timeout: const Duration(seconds: 60),
  );
  await _visualReady(tester, 'passenger-account');
}

Future<void> _captureDriver(WidgetTester tester) async {
  _requireFixture(_driver1Email, 'VANRIDES_FIXTURE_DRIVER_1_EMAIL');
  _requireFixture(_driverPassword, 'VANRIDES_FIXTURE_DRIVER_PASSWORD');

  await launchApp(tester);
  await login(tester: tester, email: _driver1Email, senha: _driverPassword);
  await esperarHomeMotorista(tester);
  await _visualReady(tester, 'driver-home');

  await _tapBottomNav(tester, 3);
  await pumpUntil(
    tester,
    find.byKey(const Key('driver.account.darkSurface')),
    timeout: const Duration(seconds: 60),
  );
  await _visualReady(tester, 'driver-account');
}

Future<void> _captureGuardian(WidgetTester tester) async {
  _requireFixture(_guardianEmail, 'GUARDIAN_E2E_EMAIL');
  _requireFixture(_guardianPassword, 'GUARDIAN_E2E_PASSWORD');

  await launchApp(tester);
  await login(tester: tester, email: _guardianEmail, senha: _guardianPassword);
  await pumpUntil(
    tester,
    find.byKey(const ValueKey('guardian.home')),
    timeout: const Duration(seconds: 60),
  );
  await _visualReady(tester, 'guardian-home');

  await _tapBottomNavLabel(tester, AppStrings.guardian.presencas);
  await pumpUntil(
    tester,
    find.byKey(const ValueKey('guardian.presencas')),
    timeout: const Duration(seconds: 60),
  );
  await _visualReady(tester, 'guardian-presence');

  await _tapBottomNavLabel(tester, AppStrings.guardian.localizacao);
  await _pumpUntilAny(tester, [
    find.byKey(PassengerMapFooterSheetScaffold.rootKey),
    find.byKey(GuardianLocalizacaoScreen.abrirMapaButtonKey),
    find.text(AppStrings.guardian.semViagemHoje),
  ], timeout: const Duration(seconds: 60));
  await _visualReady(tester, 'guardian-location');

  await _tapBottomNavLabel(tester, AppStrings.guardian.conta);
  await pumpUntil(
    tester,
    find.byKey(const Key('guardian.account.darkSurface')),
    timeout: const Duration(seconds: 60),
  );
  await _visualReady(tester, 'guardian-account');
}

Future<void> _tapBottomNav(WidgetTester tester, int index) async {
  final target = find.byKey(MapFirstBottomNavItems.itemKey(index));
  await pumpUntil(tester, target, timeout: const Duration(seconds: 30));
  await tester.tap(target.last);
  await tester.pumpAndSettle(const Duration(milliseconds: 250));
}

Future<void> _tapBottomNavLabel(WidgetTester tester, String label) async {
  final target = find.text(label);
  await pumpUntil(tester, target, timeout: const Duration(seconds: 30));
  await tester.tap(target.last);
  await tester.pumpAndSettle(const Duration(milliseconds: 250));
}

Future<void> _pumpUntilAny(
  WidgetTester tester,
  List<Finder> finders, {
  Duration timeout = const Duration(seconds: 30),
  Duration step = const Duration(milliseconds: 250),
}) async {
  final end = DateTime.now().add(timeout);
  while (DateTime.now().isBefore(end)) {
    await tester.pump(step);
    if (finders.any((finder) => finder.evaluate().isNotEmpty)) return;
  }
  throw TestFailure('Timeout esperando um destes elementos: $finders');
}

Future<void> _visualReady(WidgetTester tester, String state) async {
  await tester.pumpAndSettle(const Duration(milliseconds: 250));
  // The visual smoke shell listens for this line and captures the simulator.
  // Keep the screen stable for the external `simctl io screenshot` call.
  debugPrintSynchronously('VANRIDES_VISUAL_READY:$state');
  await tester.pump(const Duration(seconds: 2));
}

void _requireFixture(String value, String name) {
  if (value.trim().isEmpty) {
    throw TestFailure('$name precisa estar definido para o smoke visual.');
  }
}
