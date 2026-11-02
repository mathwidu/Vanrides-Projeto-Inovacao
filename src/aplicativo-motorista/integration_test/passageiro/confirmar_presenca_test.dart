import 'package:flutter_test/flutter_test.dart';

import 'package:vanrides_frontend_dev/features/service_presence/presentation/confirmar_presenca_screen.dart';

import '../_support/api_support.dart';
import '../_support/e2e_support.dart';
import '../_support/flows.dart';

Future<void> _abrirFluxoDePresenca(WidgetTester tester) async {
  await tester.tap(find.text('Presenca').last);
  await tester.pumpAndSettle();
  await pumpUntil(tester, find.text('Abrir confirmacao de presenca'));
  await tester.tap(find.text('Abrir confirmacao de presenca'));
  await tester.pumpAndSettle();
}

void registerPassageiroConfirmarPresencaTests() {
  testWidgets(
    'Passageiro presenças (feliz): seleciona hoje e confirma',
    (tester) async {
      await launchApp(tester);

      final ts = DateTime.now().millisecondsSinceEpoch;
      final motoristaEmail = uniqueEmail('motorista.presencas.passageiro');
      const senha = 'Teste@1234';

      final motoristaId = await createMotoristaViaApi(
        nome: 'Motorista Seed Presencas $ts',
        email: motoristaEmail,
        telefone: '11999999999',
        senha: senha,
      );

      final passageiroEmail = uniqueEmail('passageiro.presencas');
      await createPassageiroViaApi(
        nome: 'Passageiro Seed Presencas $ts',
        email: passageiroEmail,
        telefone: '11999999998',
        senha: senha,
        motoristaId: motoristaId,
      );

      await login(tester: tester, email: passageiroEmail, senha: senha);
      await esperarHomePassageiro(tester);

      await _abrirFluxoDePresenca(tester);

      await pumpUntil(tester, find.text('Presenças'));
      await pumpUntil(
        tester,
        find.byKey(ConfirmarPresencaScreen.calendarKey),
        timeout: const Duration(seconds: 60),
      );

      final hoje = DateTime.now();
      final dayText = '${hoje.day}';
      final dayCell = find.descendant(
        of: find.byKey(ConfirmarPresencaScreen.calendarKey),
        matching: find.text(dayText),
      );
      expect(dayCell, findsWidgets);
      await tester.tap(dayCell.first);
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(ConfirmarPresencaScreen.confirmarButtonKey));
      await tester.pump();

      await pumpUntil(
        tester,
        find.textContaining('confirmadas'),
        timeout: const Duration(seconds: 90),
      );
    },
    timeout: const Timeout(Duration(minutes: 8)),
  );

  testWidgets(
    'Passageiro presenças (triste): confirmar sem seleção não crasha',
    (tester) async {
      await launchApp(tester);

      final ts = DateTime.now().millisecondsSinceEpoch;
      final motoristaEmail = uniqueEmail(
        'motorista.presencas.passageiro.triste',
      );
      const senha = 'Teste@1234';

      final motoristaId = await createMotoristaViaApi(
        nome: 'Motorista Seed Presencas Triste $ts',
        email: motoristaEmail,
        telefone: '11999999999',
        senha: senha,
      );

      final passageiroEmail = uniqueEmail('passageiro.presencas.triste');
      await createPassageiroViaApi(
        nome: 'Passageiro Seed Presencas Triste $ts',
        email: passageiroEmail,
        telefone: '11999999998',
        senha: senha,
        motoristaId: motoristaId,
      );

      await login(tester: tester, email: passageiroEmail, senha: senha);
      await esperarHomePassageiro(tester);

      await _abrirFluxoDePresenca(tester);

      await pumpUntil(tester, find.text('Presenças'));
      await pumpUntil(
        tester,
        find.byKey(ConfirmarPresencaScreen.calendarKey),
        timeout: const Duration(seconds: 60),
      );

      await tester.tap(find.byKey(ConfirmarPresencaScreen.confirmarButtonKey));
      await tester.pumpAndSettle();

      expect(
        find.byKey(ConfirmarPresencaScreen.confirmarButtonKey),
        findsOneWidget,
      );
      expect(
        find.byKey(ConfirmarPresencaScreen.removerButtonKey),
        findsOneWidget,
      );
    },
    timeout: const Timeout(Duration(minutes: 7)),
  );
}

void main() {
  ensureBinding();
  registerPassageiroConfirmarPresencaTests();
}
