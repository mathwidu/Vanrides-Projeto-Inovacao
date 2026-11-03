import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:vanrides_frontend_dev/features/guardian/presentation/guardian_localizacao_screen.dart';
import 'package:vanrides_frontend_dev/features/guardian/presentation/guardian_shell_screen.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/widgets/passenger_map_footer_sheet_scaffold.dart';
import 'package:vanrides_frontend_dev/l10n/app_strings.dart';
import 'package:vanrides_frontend_dev/features/auth/presentation/login_screen.dart'
    as login_screen;

import '../_support/e2e_support.dart';
import '../_support/flows.dart';

const _guardianEmail = String.fromEnvironment(
  'GUARDIAN_E2E_EMAIL',
  defaultValue: '',
);
const _guardianPassword = String.fromEnvironment(
  'GUARDIAN_E2E_PASSWORD',
  defaultValue: '',
);
const _guardianExpectedPassageiro = String.fromEnvironment(
  'GUARDIAN_E2E_PASSAGEIRO',
  defaultValue: '',
);

bool _shouldSkipGuardianE2E() {
  return _guardianEmail.isEmpty || _guardianPassword.isEmpty;
}

void main() {
  ensureBinding();

  testWidgets(
    'guardian faz login e navega pelas areas principais',
    (tester) async {
      await launchApp(tester);

      await login(
        tester: tester,
        email: _guardianEmail,
        senha: _guardianPassword,
      );

      await pumpUntil(
        tester,
        find.byKey(const ValueKey('guardian.home')),
        timeout: const Duration(seconds: 60),
      );
      await tester.pump(const Duration(milliseconds: 500));

      expect(find.textContaining('Bem-vindo,'), findsOneWidget);
      expect(
        find.text(AppStrings.guardian.passageirosDaFamilia),
        findsOneWidget,
      );
      expect(find.text('Internal Server Error'), findsNothing);

      if (_guardianExpectedPassageiro.isNotEmpty) {
        expect(find.textContaining(_guardianExpectedPassageiro), findsWidgets);
      }

      await tester.tap(find.text(AppStrings.guardian.presencas));
      final presencasTitle = find.text(
        AppStrings.guardian.presencasDoResponsavel,
      );
      await pumpUntil(
        tester,
        presencasTitle,
        timeout: const Duration(seconds: 60),
      );
      expect(presencasTitle, findsOneWidget);

      await tester.tap(find.text(AppStrings.guardian.localizacao));
      final localizacaoEsperada = [
        find.byKey(PassengerMapFooterSheetScaffold.rootKey),
        find.byKey(GuardianLocalizacaoScreen.abrirMapaButtonKey),
        find.text(AppStrings.guardian.semViagemHoje),
      ];
      await _pumpUntilAny(
        tester,
        localizacaoEsperada,
        timeout: const Duration(seconds: 60),
      );
      expect(_hasAny(localizacaoEsperada), isTrue);

      await tester.tap(find.text(AppStrings.guardian.conta));
      await pumpUntil(
        tester,
        find.byKey(const Key('guardian.account.darkSurface')),
        timeout: const Duration(seconds: 60),
      );
      expect(
        find.byKey(const Key('guardian.account.darkSurface')),
        findsOneWidget,
      );

      await scrollIntoView(
        tester,
        find.byKey(GuardianShellScreen.logoutButtonKey),
      );
      await tester.tap(find.byKey(GuardianShellScreen.logoutButtonKey));
      await pumpUntil(
        tester,
        find.byKey(login_screen.LoginScreen.entrarButtonKey),
        timeout: const Duration(seconds: 60),
      );
      expect(
        find.byKey(login_screen.LoginScreen.entrarButtonKey),
        findsOneWidget,
      );
    },
    skip: _shouldSkipGuardianE2E(),
  );
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
    if (_hasAny(finders)) return;
  }
  throw TestFailure('Timeout esperando um destes elementos: $finders');
}

bool _hasAny(List<Finder> finders) =>
    finders.any((finder) => finder.evaluate().isNotEmpty);
