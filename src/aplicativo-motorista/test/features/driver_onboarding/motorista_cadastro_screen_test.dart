import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/driver_onboarding/presentation/cadastro_motorista_screen.dart';
import 'package:vanrides_frontend_dev/features/auth/presentation/email_verification_pending_screen.dart';
import 'package:vanrides_frontend_dev/features/driver_onboarding/data/motorista_cadastro_gateway.dart';

void main() {
  testWidgets(
    'cadastro de motorista envia payload e abre verificacao de email',
    (tester) async {
      final gateway = _FakeMotoristaCadastroGateway(
        result: const MotoristaCadastroOk(),
      );

      await _pumpScreen(tester, gateway);
      await _fillValidDriverForm(tester);

      await tester.tap(find.byKey(CadastroMotoristaScreen.cadastrarButtonKey));
      await tester.pumpAndSettle();

      expect(gateway.payloads, hasLength(1));
      expect(gateway.payloads.single.nome, 'Matheus Motorista');
      expect(gateway.payloads.single.email, 'motorista@vanrides.dev');
      expect(gateway.payloads.single.telefone, '51999990000');
      expect(gateway.payloads.single.dataNascimento, '1990-05-10');
      expect(gateway.payloads.single.senha, 'Driver!123');
      expect(
        find.byKey(EmailVerificationPendingScreen.screenKey),
        findsOneWidget,
      );
      expect(find.text('motorista@vanrides.dev'), findsOneWidget);
    },
  );

  testWidgets('cadastro de motorista mostra erro de validacao do backend', (
    tester,
  ) async {
    final gateway = _FakeMotoristaCadastroGateway(
      result: const MotoristaCadastroValidationError(
        message: 'E-mail ja cadastrado',
      ),
    );

    await _pumpScreen(tester, gateway);
    await _fillValidDriverForm(tester, email: 'duplicado@vanrides.dev');

    await tester.tap(find.byKey(CadastroMotoristaScreen.cadastrarButtonKey));
    await tester.pump();

    expect(find.text('E-mail ja cadastrado'), findsOneWidget);
    expect(find.byKey(EmailVerificationPendingScreen.screenKey), findsNothing);
  });

  testWidgets(
    'cadastro de motorista mostra erro de rede e mantem formulario preenchido',
    (tester) async {
      final gateway = _FakeMotoristaCadastroGateway(
        result: const MotoristaCadastroNetworkError(),
      );

      await _pumpScreen(tester, gateway);
      await _fillValidDriverForm(tester);

      await tester.tap(find.byKey(CadastroMotoristaScreen.cadastrarButtonKey));
      await tester.pump();

      expect(find.text('Erro de conexão com o servidor.'), findsOneWidget);
      expect(find.text('Matheus Motorista'), findsOneWidget);
      expect(find.text('motorista@vanrides.dev'), findsOneWidget);
      expect(
        find.byKey(EmailVerificationPendingScreen.screenKey),
        findsNothing,
      );
    },
  );
}

Future<void> _pumpScreen(
  WidgetTester tester,
  MotoristaCadastroGateway gateway,
) async {
  await tester.pumpWidget(
    MaterialApp(home: CadastroMotoristaScreen(cadastroGateway: gateway)),
  );
}

Future<void> _fillValidDriverForm(
  WidgetTester tester, {
  String email = 'motorista@vanrides.dev',
}) async {
  await _enterText(
    tester,
    CadastroMotoristaScreen.nomeFieldKey,
    'Matheus Motorista',
  );
  await _enterText(tester, CadastroMotoristaScreen.emailFieldKey, email);
  await _enterText(
    tester,
    CadastroMotoristaScreen.telefoneFieldKey,
    '51999990000',
  );
  await _enterText(
    tester,
    CadastroMotoristaScreen.dataNascimentoFieldKey,
    '10/05/1990',
  );
  await _enterText(tester, CadastroMotoristaScreen.senhaFieldKey, 'Driver!123');
  await _enterText(
    tester,
    CadastroMotoristaScreen.confirmarSenhaFieldKey,
    'Driver!123',
  );
  await tester.ensureVisible(
    find.byKey(CadastroMotoristaScreen.cadastrarButtonKey),
  );
}

Future<void> _enterText(WidgetTester tester, Key key, String value) async {
  final finder = find.byKey(key);
  await tester.ensureVisible(finder);
  await tester.enterText(finder, value);
  await tester.pump();
}

class _FakeMotoristaCadastroGateway implements MotoristaCadastroGateway {
  _FakeMotoristaCadastroGateway({required this.result});

  final MotoristaCadastroResult result;
  final List<MotoristaCadastroPayload> payloads = [];

  @override
  Future<MotoristaCadastroResult> cadastrar(
    MotoristaCadastroPayload payload,
  ) async {
    payloads.add(payload);
    return result;
  }
}
