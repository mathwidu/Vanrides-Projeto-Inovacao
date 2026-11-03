import 'package:flutter_test/flutter_test.dart';

import 'package:vanrides_frontend_dev/features/passenger_onboarding/presentation/cadastro_passageiro_screen.dart';
import 'package:vanrides_frontend_dev/features/auth/presentation/email_verification_pending_screen.dart';
import 'package:vanrides_frontend_dev/features/auth/presentation/escolha_cadastro_screen.dart';

import '../_support/e2e_support.dart';
import '../_support/flows.dart';

void registerPassageiroCadastroTests() {
  testWidgets(
    'Passageiro cadastro (feliz): cria passageiro e mostra verificacao pendente',
    (tester) async {
      if (!kVanRidesE2eMode) {
        throw TestFailure(
          'Rode com --dart-define=VANRIDES_E2E_TEST_MODE=true para automatizar endereço.',
        );
      }

      await launchApp(tester);

      final ts = DateTime.now().millisecondsSinceEpoch;
      const senha = 'Teste@1234';

      await goToCadastroChoice(tester);
      final passageiroEmail = uniqueEmail('passageiro.cadastro.feliz');
      await criarPassageiroPeloApp(
        tester: tester,
        nome: 'Passageiro Feliz $ts',
        email: passageiroEmail,
        telefone: '11999999998',
        senha: senha,
        motoristaNomeParaSelecionar: null,
        preencherEnderecoE2e: true,
      );

      await pumpUntil(
        tester,
        find.byKey(EmailVerificationPendingScreen.screenKey),
        timeout: const Duration(seconds: 60),
      );
      expect(
        find.byKey(EmailVerificationPendingScreen.screenKey),
        findsOneWidget,
      );
    },
    timeout: const Timeout(Duration(minutes: 6)),
  );

  testWidgets(
    'Passageiro cadastro (triste): sem endereço mostra snackbar',
    (tester) async {
      if (!kVanRidesE2eMode) {
        throw TestFailure(
          'Rode com --dart-define=VANRIDES_E2E_TEST_MODE=true.',
        );
      }

      await launchApp(tester);

      final ts = DateTime.now().millisecondsSinceEpoch;
      const senha = 'Teste@1234';

      await goToCadastroChoice(tester);
      await criarPassageiroPeloApp(
        tester: tester,
        nome: 'Passageiro Sem Endereço $ts',
        email: uniqueEmail('passageiro.sem.endereco'),
        telefone: '11999999998',
        senha: senha,
        motoristaNomeParaSelecionar: null,
        preencherEnderecoE2e: false,
      );
      // Aguarda só o suficiente para o SnackBar aparecer (sem “consumir” o tempo dele).
      await tester.pump(const Duration(milliseconds: 500));

      expect(
        find.text('Preencha todos os campos obrigatórios do cadastro.'),
        findsOneWidget,
      );
      expect(find.byKey(CadastroPassageiroScreen.screenKey), findsOneWidget);
    },
    timeout: const Timeout(Duration(minutes: 6)),
  );

  testWidgets(
    'Passageiro cadastro (fluxo híbrido): não exige motorista no formulário',
    (tester) async {
      if (!kVanRidesE2eMode) {
        throw TestFailure(
          'Rode com --dart-define=VANRIDES_E2E_TEST_MODE=true.',
        );
      }

      await launchApp(tester);
      await goToCadastroChoice(tester);

      await tester.tap(
        find.byKey(EscolhaCadastroScreen.souPassageiroButtonKey),
      );
      await tester.pumpAndSettle();
      await pumpUntil(tester, find.byKey(CadastroPassageiroScreen.screenKey));

      await tester.enterText(
        find.byKey(CadastroPassageiroScreen.nomeFieldKey),
        'Passageiro Fluxo Hibrido',
      );
      await tester.enterText(
        find.byKey(CadastroPassageiroScreen.emailFieldKey),
        uniqueEmail('passageiro.hibrido'),
      );
      await tester.enterText(
        find.byKey(CadastroPassageiroScreen.telefoneFieldKey),
        '11999999998',
      );
      await tester.enterText(
        find.byKey(CadastroPassageiroScreen.dataNascimentoFieldKey),
        '20/02/1995',
      );
      await tester.enterText(
        find.byKey(CadastroPassageiroScreen.senhaFieldKey),
        'Teste@1234',
      );
      await tester.enterText(
        find.byKey(CadastroPassageiroScreen.confirmarSenhaFieldKey),
        'Teste@1234',
      );
      await dismissKeyboard(tester);

      // Preenche endereço via E2E, mas não escolhe motorista.
      final preencherEndereco = find.byKey(
        CadastroPassageiroScreen.e2ePreencherEnderecoButtonKey,
      );
      await scrollIntoView(tester, preencherEndereco);
      await tester.tap(preencherEndereco.hitTestable());
      await tester.pumpAndSettle();

      final cadastrar = find.byKey(CadastroPassageiroScreen.cadastrarButtonKey);
      await scrollIntoView(tester, cadastrar);
      await tester.tap(cadastrar.hitTestable());
      await pumpUntil(
        tester,
        find.byKey(EmailVerificationPendingScreen.screenKey),
        timeout: const Duration(seconds: 60),
      );

      expect(
        find.byKey(EmailVerificationPendingScreen.screenKey),
        findsOneWidget,
      );
      expect(find.byKey(EscolhaCadastroScreen.screenKey), findsNothing);
    },
    timeout: const Timeout(Duration(minutes: 6)),
  );
}

void main() {
  ensureBinding();
  registerPassageiroCadastroTests();
}
