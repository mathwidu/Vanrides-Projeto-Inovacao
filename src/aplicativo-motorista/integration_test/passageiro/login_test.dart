import 'package:flutter_test/flutter_test.dart';

import 'package:vanrides_frontend_dev/l10n/app_strings.dart';
import 'package:vanrides_frontend_dev/main.dart' as app;

import '../_support/api_support.dart';
import '../_support/e2e_support.dart';
import '../_support/flows.dart';

void registerPassageiroLoginTests() {
  testWidgets(
    'Passageiro login (pendente): bloqueia acesso e abre tela de verificacao',
    (tester) async {
      await launchApp(tester);

      final ts = DateTime.now().millisecondsSinceEpoch;
      final motoristaEmail = uniqueEmail('motorista.login.passageiro');
      const senha = 'Teste@1234';

      final motoristaId = await createMotoristaViaApi(
        nome: 'Motorista Seed Login Pass $ts',
        email: motoristaEmail,
        telefone: '11999999999',
        senha: senha,
      );

      final passageiroEmail = uniqueEmail('passageiro.login.feliz');
      await createPassageiroViaApi(
        nome: 'Passageiro Seed Login $ts',
        email: passageiroEmail,
        telefone: '11999999998',
        senha: senha,
        motoristaId: motoristaId,
      );

      await login(tester: tester, email: passageiroEmail, senha: senha);
      await esperarTelaEmailPendente(tester);
    },
    timeout: const Timeout(Duration(minutes: 7)),
  );

  testWidgets(
    'Passageiro login (triste): validacoes bloqueiam email e senha invalidos',
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
  registerPassageiroLoginTests();
}
