import 'package:flutter_test/flutter_test.dart';

import 'package:vanrides_frontend_dev/features/driver_onboarding/presentation/cadastro_motorista_screen.dart';
import 'package:vanrides_frontend_dev/features/auth/presentation/email_verification_pending_screen.dart';
import 'package:vanrides_frontend_dev/features/auth/presentation/escolha_cadastro_screen.dart';

import '../_support/e2e_support.dart';
import '../_support/flows.dart';

void registerMotoristaCadastroTests() {
  testWidgets(
    'Motorista cadastro (feliz): cria motorista e mostra verificacao pendente',
    (tester) async {
      await launchApp(tester);

      final nome =
          'Motorista Cadastro Feliz ${DateTime.now().millisecondsSinceEpoch}';
      final email = uniqueEmail('motorista.cadastro.feliz');

      await criarMotoristaPeloApp(
        tester: tester,
        nome: nome,
        email: email,
        telefone: '11999999999',
        senha: 'Teste@1234',
      );

      expect(
        find.byKey(EmailVerificationPendingScreen.screenKey),
        findsOneWidget,
      );
    },
    timeout: const Timeout(Duration(minutes: 5)),
  );

  testWidgets(
    'Motorista cadastro (triste): senha fraca bloqueia',
    (tester) async {
      await launchApp(tester);
      await goToCadastroChoice(tester);

      await tester.tap(find.byKey(EscolhaCadastroScreen.souMotoristaButtonKey));
      await tester.pumpAndSettle();
      await pumpUntil(tester, find.byKey(CadastroMotoristaScreen.screenKey));

      await tester.enterText(
        find.byKey(CadastroMotoristaScreen.nomeFieldKey),
        'Motorista Fraco',
      );
      await tester.enterText(
        find.byKey(CadastroMotoristaScreen.emailFieldKey),
        uniqueEmail('motorista.fraco'),
      );
      await tester.enterText(
        find.byKey(CadastroMotoristaScreen.telefoneFieldKey),
        '11999999999',
      );
      await tester.enterText(
        find.byKey(CadastroMotoristaScreen.dataNascimentoFieldKey),
        '15/01/1990',
      );
      await tester.enterText(
        find.byKey(CadastroMotoristaScreen.senhaFieldKey),
        'abc',
      );
      await tester.enterText(
        find.byKey(CadastroMotoristaScreen.confirmarSenhaFieldKey),
        'abc',
      );
      await dismissKeyboard(tester);

      final cadastrar = find.byKey(CadastroMotoristaScreen.cadastrarButtonKey);
      await scrollIntoView(tester, cadastrar);
      await tester.tap(cadastrar.hitTestable());
      // Aguarda o SnackBar aparecer (sem consumir o tempo dele).
      await tester.pump(const Duration(milliseconds: 500));

      expect(find.text('Sua senha não atende aos requisitos.'), findsOneWidget);
      expect(find.byKey(CadastroMotoristaScreen.screenKey), findsOneWidget);
    },
    timeout: const Timeout(Duration(minutes: 5)),
  );
}

void main() {
  ensureBinding();
  registerMotoristaCadastroTests();
}
