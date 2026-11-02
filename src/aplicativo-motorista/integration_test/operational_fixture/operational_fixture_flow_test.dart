import 'package:flutter_test/flutter_test.dart';
import 'package:flutter/widgets.dart';

import 'package:vanrides_frontend_dev/features/driver_trip/presentation/home_motorista_screen.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/home_passageiro_screen.dart';

import '../_support/e2e_support.dart';
import '../_support/flows.dart';

const _driver1Email = String.fromEnvironment(
  'VANRIDES_FIXTURE_DRIVER_1_EMAIL',
  defaultValue: '',
);
const _driver2Email = String.fromEnvironment(
  'VANRIDES_FIXTURE_DRIVER_2_EMAIL',
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
const _passengerName = String.fromEnvironment(
  'VANRIDES_FIXTURE_PASSENGER_NAME',
  defaultValue: 'P1',
);

const _morningServiceName = String.fromEnvironment(
  'VANRIDES_FIXTURE_SERVICE_MORNING_NAME',
  defaultValue: 'Linha Manhã - Escola',
);
const _afternoonServiceName = String.fromEnvironment(
  'VANRIDES_FIXTURE_SERVICE_AFTERNOON_NAME',
  defaultValue: 'Linha Tarde - Retorno',
);
const _nightServiceName = String.fromEnvironment(
  'VANRIDES_FIXTURE_SERVICE_NIGHT_NAME',
  defaultValue: 'Linha Noite - Atividade',
);

bool get _hasDriverFixture =>
    _driver1Email.isNotEmpty &&
    _driver2Email.isNotEmpty &&
    _driverPassword.isNotEmpty;

bool get _hasPassengerFixture =>
    _passengerEmail.isNotEmpty && _passengerPassword.isNotEmpty;

void _printVisibleTextsOnFailure(WidgetTester tester) {
  final texts = tester.allWidgets
      .whereType<Text>()
      .map((widget) => widget.data ?? widget.textSpan?.toPlainText() ?? '')
      .where((text) => text.trim().isNotEmpty)
      .toList(growable: false);
  printOnFailure('Textos na tela:\n${texts.join('\n')}');
}

void _expectTextWidgetContaining(WidgetTester tester, String expected) {
  final texts = tester.allWidgets
      .whereType<Text>()
      .map((widget) => widget.data ?? widget.textSpan?.toPlainText() ?? '')
      .where((text) => text.contains(expected))
      .toList(growable: false);
  expect(texts, isNotEmpty);
}

void main() {
  ensureBinding();

  testWidgets(
    'motorista principal ve a viagem operacional iniciada da manha',
    (tester) async {
      await launchApp(tester);

      await login(tester: tester, email: _driver1Email, senha: _driverPassword);
      await esperarHomeMotorista(tester);

      await pumpUntil(
        tester,
        find.byKey(HomeMotoristaScreen.concluirParadaButtonKey),
        timeout: const Duration(seconds: 60),
      );

      _printVisibleTextsOnFailure(tester);
      expect(find.textContaining(_morningServiceName), findsWidgets);
      expect(find.textContaining(_passengerName), findsWidgets);
      expect(
        find.byKey(HomeMotoristaScreen.concluirParadaButtonKey),
        findsOneWidget,
      );
      expect(
        find.byKey(HomeMotoristaScreen.encerrarCorridaButtonKey),
        findsOneWidget,
      );
    },
    skip: !_hasDriverFixture,
    timeout: const Timeout(Duration(minutes: 6)),
  );

  testWidgets(
    'motorista reserva ve as viagens escaladas para tarde e noite',
    (tester) async {
      await launchApp(tester);

      await login(tester: tester, email: _driver2Email, senha: _driverPassword);
      await esperarHomeMotorista(tester);

      await pumpUntil(
        tester,
        find.byKey(HomeMotoristaScreen.agendaViagensKey),
        timeout: const Duration(seconds: 60),
      );

      expect(find.byKey(HomeMotoristaScreen.agendaViagensKey), findsOneWidget);
      expect(find.textContaining(_afternoonServiceName), findsWidgets);
      expect(find.textContaining(_nightServiceName), findsWidgets);
      expect(
        find.byKey(HomeMotoristaScreen.iniciarCorridaButtonKey),
        findsOneWidget,
      );
    },
    skip: !_hasDriverFixture,
    timeout: const Timeout(Duration(minutes: 6)),
  );

  testWidgets(
    'passageiro principal escolhe entre as viagens elegiveis do dia',
    (tester) async {
      await launchApp(tester);

      await login(
        tester: tester,
        email: _passengerEmail,
        senha: _passengerPassword,
      );
      await esperarHomePassageiro(tester);

      await pumpUntil(
        tester,
        find.byKey(HomePassageiroScreen.agendaViagensKey),
        timeout: const Duration(seconds: 60),
      );

      _printVisibleTextsOnFailure(tester);
      expect(find.byKey(HomePassageiroScreen.agendaViagensKey), findsOneWidget);
      _expectTextWidgetContaining(tester, _morningServiceName);
      _expectTextWidgetContaining(tester, _afternoonServiceName);
      _expectTextWidgetContaining(tester, _nightServiceName);
      final confirmarPresenca = find.byKey(
        HomePassageiroScreen.confirmarPresencaButtonKey,
      );
      if (confirmarPresenca.evaluate().isNotEmpty) {
        expect(confirmarPresenca, findsOneWidget);
      } else {
        expect(
          find.text(
            'Nenhum horário precisa de decisão agora.',
            skipOffstage: false,
          ),
          findsOneWidget,
        );
      }
    },
    skip: !_hasPassengerFixture,
    timeout: const Timeout(Duration(minutes: 6)),
  );
}
