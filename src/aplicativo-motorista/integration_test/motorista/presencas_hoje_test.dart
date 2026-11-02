import 'package:flutter_test/flutter_test.dart';

import 'package:vanrides_frontend_dev/features/driver_trip/presentation/home_motorista_screen.dart';

import '../_support/api_support.dart';
import '../_support/e2e_support.dart';
import '../_support/flows.dart';

void registerMotoristaPresencasHojeTests() {
  testWidgets(
    'Motorista home (feliz): sem viagem nao exibe atalho de presencas',
    (tester) async {
      await launchApp(tester);

      final nome =
          'Motorista Presencas ${DateTime.now().millisecondsSinceEpoch}';
      final email = uniqueEmail('motorista.presencas');
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
        find.byKey(HomeMotoristaScreen.verPresencasHojeButtonKey),
        findsNothing,
      );
      expect(find.text('Sem viagem publicada hoje'), findsOneWidget);
    },
    timeout: const Timeout(Duration(minutes: 6)),
  );

  testWidgets(
    'Motorista home (triste): atualizar mantem o estado vazio estavel',
    (tester) async {
      await launchApp(tester);

      final nome =
          'Motorista Presencas Triste ${DateTime.now().millisecondsSinceEpoch}';
      final email = uniqueEmail('motorista.presencas.triste');
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
      expect(find.text('Sem viagem publicada hoje'), findsOneWidget);
    },
    timeout: const Timeout(Duration(minutes: 6)),
  );
}

void main() {
  ensureBinding();
  registerMotoristaPresencasHojeTests();
}
