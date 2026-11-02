import 'package:flutter_test/flutter_test.dart';

import 'package:vanrides_frontend_dev/l10n/app_strings.dart';
import 'package:vanrides_frontend_dev/main.dart' as app;

import '../_support/api_support.dart';
import '../_support/e2e_support.dart';
import '../_support/flows.dart';

void registerMotoristaLoginTests() {
  testWidgets(
    'Motorista login (pendente): bloqueia acesso e abre tela de verificacao',
    (tester) async {
      await launchApp(tester);

      final nome =
          'Motorista Login Feliz ${DateTime.now().millisecondsSinceEpoch}';
      final email = uniqueEmail('motorista.login.feliz');
      const senha = 'Teste@1234';

      await createMotoristaViaApi(
        nome: nome,
        email: email,
        telefone: '11999999999',
        senha: senha,
      );

      await login(tester: tester, email: email, senha: senha);
      await esperarTelaEmailPendente(tester);
    },
    timeout: const Timeout(Duration(minutes: 6)),
  );

  testWidgets(
    'Motorista login (triste): validacoes bloqueiam email e senha invalidos',
    (tester) async {
      await launchApp(tester);

      await tester.enterText(find.byKey(app.LoginScreen.emailFieldKey), 'x');
      await tester.enterText(find.byKey(app.LoginScreen.senhaFieldKey), '1');
      await tester.tap(find.byKey(app.LoginScreen.entrarButtonKey));
      await tester.pumpAndSettle();

      expect(find.text(AppStrings.passageiro.emailInvalido), findsOneWidget);
      expect(
        find.text(AppStrings.passageiro.minimo6Caracteres),
        findsOneWidget,
      );
      expect(find.byKey(app.LoginScreen.entrarButtonKey), findsOneWidget);
    },
    timeout: const Timeout(Duration(minutes: 5)),
  );
}

void main() {
  ensureBinding();
  registerMotoristaLoginTests();
}
