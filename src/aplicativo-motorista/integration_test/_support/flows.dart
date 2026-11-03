import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

import 'package:vanrides_frontend_dev/features/driver_onboarding/presentation/cadastro_motorista_screen.dart';
import 'package:vanrides_frontend_dev/features/passenger_onboarding/presentation/cadastro_passageiro_screen.dart';
import 'package:vanrides_frontend_dev/features/auth/presentation/email_verification_pending_screen.dart';
import 'package:vanrides_frontend_dev/features/auth/presentation/escolha_cadastro_screen.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/presentation/home_motorista_screen.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/home_passageiro_screen.dart';
import 'package:vanrides_frontend_dev/main.dart' as app;

import 'e2e_support.dart';

void ensureBinding() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();
}

Future<void> launchApp(WidgetTester tester) async {
  app.main();
  await tester.pump();
  await pumpUntil(tester, find.byKey(app.LoginScreen.entrarButtonKey));
}

Future<void> goToCadastroChoice(WidgetTester tester) async {
  final criarConta = find.byKey(app.LoginScreen.criarContaButtonKey);
  await scrollIntoView(tester, criarConta);
  await tester.tap(criarConta.hitTestable());
  await tester.pumpAndSettle();
  await pumpUntil(tester, find.byKey(EscolhaCadastroScreen.screenKey));
}

Future<void> criarMotoristaPeloApp({
  required WidgetTester tester,
  required String nome,
  required String email,
  required String telefone,
  required String senha,
  String dataNascimento = '15/01/1990',
}) async {
  await goToCadastroChoice(tester);
  await tester.tap(find.byKey(EscolhaCadastroScreen.souMotoristaButtonKey));
  await tester.pumpAndSettle();
  await pumpUntil(tester, find.byKey(CadastroMotoristaScreen.screenKey));

  await tester.enterText(
    find.byKey(CadastroMotoristaScreen.nomeFieldKey),
    nome,
  );
  await tester.enterText(
    find.byKey(CadastroMotoristaScreen.emailFieldKey),
    email,
  );
  await tester.enterText(
    find.byKey(CadastroMotoristaScreen.telefoneFieldKey),
    telefone,
  );
  await tester.enterText(
    find.byKey(CadastroMotoristaScreen.dataNascimentoFieldKey),
    dataNascimento,
  );
  await tester.enterText(
    find.byKey(CadastroMotoristaScreen.senhaFieldKey),
    senha,
  );
  await tester.enterText(
    find.byKey(CadastroMotoristaScreen.confirmarSenhaFieldKey),
    senha,
  );
  await dismissKeyboard(tester);

  final cadastrar = find.byKey(CadastroMotoristaScreen.cadastrarButtonKey);
  await scrollIntoView(tester, cadastrar);
  await tester.tap(cadastrar.hitTestable());

  await pumpUntil(
    tester,
    find.byKey(EmailVerificationPendingScreen.screenKey),
    timeout: const Duration(seconds: 60),
  );
  await tester.pumpAndSettle();
}

Future<void> criarPassageiroPeloApp({
  required WidgetTester tester,
  required String nome,
  required String email,
  required String telefone,
  required String senha,
  String dataNascimento = '20/02/1995',
  String? motoristaNomeParaSelecionar,
  required bool preencherEnderecoE2e,
}) async {
  if (!kVanRidesE2eMode) {
    throw TestFailure(
      'Cadastro de passageiro automatizado requer VANRIDES_E2E_TEST_MODE=true',
    );
  }

  await pumpUntil(tester, find.byKey(EscolhaCadastroScreen.screenKey));
  await tester.tap(find.byKey(EscolhaCadastroScreen.souPassageiroButtonKey));
  await tester.pumpAndSettle();
  await pumpUntil(tester, find.byKey(CadastroPassageiroScreen.screenKey));

  await tester.enterText(
    find.byKey(CadastroPassageiroScreen.nomeFieldKey),
    nome,
  );
  await tester.enterText(
    find.byKey(CadastroPassageiroScreen.emailFieldKey),
    email,
  );
  await tester.enterText(
    find.byKey(CadastroPassageiroScreen.telefoneFieldKey),
    telefone,
  );
  await tester.enterText(
    find.byKey(CadastroPassageiroScreen.dataNascimentoFieldKey),
    dataNascimento,
  );
  await tester.enterText(
    find.byKey(CadastroPassageiroScreen.senhaFieldKey),
    senha,
  );
  await tester.enterText(
    find.byKey(CadastroPassageiroScreen.confirmarSenhaFieldKey),
    senha,
  );
  await dismissKeyboard(tester);

  // O fluxo híbrido remove a seleção de motorista do cadastro público.
  // O motorista passa a ser opcional e pode ser atribuído depois pela empresa.
  if (motoristaNomeParaSelecionar != null) {
    debugPrint(
      '[E2E] motoristaNomeParaSelecionar ignorado: o cadastro do passageiro não seleciona mais motorista.',
    );
  }

  final preencherEnderecoButton = find.byKey(
    CadastroPassageiroScreen.e2ePreencherEnderecoButtonKey,
  );
  if (preencherEnderecoE2e) {
    await scrollIntoView(tester, preencherEnderecoButton);
    await tester.tap(preencherEnderecoButton.hitTestable());
    await tester.pumpAndSettle();
  }

  final cadastrar = find.byKey(CadastroPassageiroScreen.cadastrarButtonKey);
  await scrollIntoView(tester, cadastrar);
  await tester.tap(cadastrar.hitTestable());
}

Future<void> voltarParaLoginDaEscolhaCadastro(WidgetTester tester) async {
  await back(tester);
  await pumpUntil(tester, find.byKey(app.LoginScreen.entrarButtonKey));
}

Future<void> login({
  required WidgetTester tester,
  required String email,
  required String senha,
}) async {
  await tester.enterText(find.byKey(app.LoginScreen.emailFieldKey), email);
  await tester.enterText(find.byKey(app.LoginScreen.senhaFieldKey), senha);
  await dismissKeyboard(tester);
  final entrar = find.byKey(app.LoginScreen.entrarButtonKey);
  await scrollIntoView(tester, entrar);
  await tester.tap(entrar.hitTestable());
  await tester.pump();
}

Future<void> esperarHomeMotorista(WidgetTester tester) async {
  await pumpUntil(
    tester,
    find.byKey(HomeMotoristaScreen.atualizarButtonKey),
    timeout: const Duration(seconds: 60),
  );
  expect(find.byKey(HomeMotoristaScreen.atualizarButtonKey), findsOneWidget);
}

Future<void> esperarHomePassageiro(WidgetTester tester) async {
  await _pumpUntilAny(tester, [
    find.byKey(HomePassageiroScreen.verLocalizacaoButtonKey),
    find.byKey(const Key('mapFirst.bottomNav.item.0')),
    find.text('Sem viagem hoje'),
  ], timeout: const Duration(seconds: 60));

  if (find
      .byKey(const Key('mapFirst.bottomNav.item.0'))
      .evaluate()
      .isNotEmpty) {
    return;
  }

  final confirmPresence = find.byKey(
    HomePassageiroScreen.confirmarPresencaButtonKey,
  );
  if (confirmPresence.evaluate().isNotEmpty) {
    expect(confirmPresence, findsOneWidget);
    return;
  }

  expect(find.text('Sem viagem hoje'), findsOneWidget);
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

Future<void> esperarTelaEmailPendente(WidgetTester tester) async {
  await pumpUntil(
    tester,
    find.byKey(EmailVerificationPendingScreen.screenKey),
    timeout: const Duration(seconds: 60),
  );
  expect(find.byKey(EmailVerificationPendingScreen.screenKey), findsOneWidget);
}
