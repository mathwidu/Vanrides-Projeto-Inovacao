import 'package:flutter_test/flutter_test.dart';

import 'package:vanrides_frontend_dev/features/driver_trip/presentation/home_motorista_screen.dart';

import '../_support/api_support.dart';
import '../_support/e2e_support.dart';
import '../_support/flows.dart';

void registerMotoristaCompartilharLocalizacaoTests() {
  testWidgets(
    'Motorista home (feliz): login mostra home sem CTA de localizacao avulsa',
    (tester) async {
      await launchApp(tester);

      final nome =
          'Motorista Localizacao ${DateTime.now().millisecondsSinceEpoch}';
      final email = uniqueEmail('motorista.localizacao');
      const senha = 'Teste@1234';

      await createMotoristaViaApi(
        nome: nome,
        email: email,
        telefone: '11999999999',
        senha: senha,
      );
      await login(tester: tester, email: email, senha: senha);
      await esperarHomeMotorista(tester);

      expect(
        find.byKey(HomeMotoristaScreen.atualizarButtonKey),
        findsOneWidget,
      );
      expect(
        find.byKey(HomeMotoristaScreen.compartilharLocalizacaoButtonKey),
        findsNothing,
      );
    },
    timeout: const Timeout(Duration(minutes: 6)),
  );

  testWidgets(
    'Motorista home (triste): atualizar nao crasha sem viagem publicada',
    (tester) async {
      await launchApp(tester);

      final nome =
          'Motorista Localizacao Triste ${DateTime.now().millisecondsSinceEpoch}';
      final email = uniqueEmail('motorista.localizacao.triste');
      const senha = 'Teste@1234';

      await createMotoristaViaApi(
        nome: nome,
        email: email,
        telefone: '11999999999',
        senha: senha,
      );
      await login(tester: tester, email: email, senha: senha);
      await esperarHomeMotorista(tester);

      await tester.tap(find.byKey(HomeMotoristaScreen.atualizarButtonKey));
      await tester.pumpAndSettle();

      expect(
        find.byKey(HomeMotoristaScreen.atualizarButtonKey),
        findsOneWidget,
      );
    },
    timeout: const Timeout(Duration(minutes: 6)),
  );
}

void main() {
  ensureBinding();
  registerMotoristaCompartilharLocalizacaoTests();
}
