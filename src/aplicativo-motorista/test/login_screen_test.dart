import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:vanrides_frontend_dev/app/theme/vanrides_design_tokens.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/presentation/driver_company_pending_screen.dart';
import 'package:vanrides_frontend_dev/features/auth/presentation/login_screen.dart';

void main() {
  setUp(() {
    SharedPreferences.setMockInitialValues({});
  });

  testWidgets('login usa superficie dark premium', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: LoginScreen(
          client: MockClient((_) async => http.Response('{}', 500)),
        ),
      ),
    );
    await tester.pumpAndSettle();

    final root = tester.widget<Container>(
      find.byKey(const Key('login.darkSurface')),
    );
    expect(root.color, VanRidesDesignTokens.backgroundDeep);

    final form = tester.widget<Container>(
      find.byKey(const Key('login.form.darkSurface')),
    );
    final decoration = form.decoration! as BoxDecoration;
    expect(decoration.color, VanRidesDesignTokens.surface);
    expect(decoration.color, isNot(Colors.white));
  });

  testWidgets(
    'login verificado de passageiro sem empresa segue para onboarding',
    (tester) async {
      final client = MockClient((request) async {
        expect(request.url.path, '/login');
        expect(request.method, 'POST');
        return http.Response(
          jsonEncode({
            'id': 22,
            'tipoUsuario': 'Passageiro',
            'mensagem': 'Bem-vindo, Passageiro Bruna!',
            'companyLinkStatus': 'NO_COMPANY',
            'accessToken': _jwtWithoutCompanyId(),
            'refreshToken': 'refresh-token',
          }),
          200,
          headers: {'content-type': 'application/json; charset=utf-8'},
        );
      });

      await tester.pumpWidget(MaterialApp(home: LoginScreen(client: client)));

      await tester.enterText(
        find.byKey(LoginScreen.emailFieldKey),
        'bruna@vanrides.dev',
      );
      await tester.enterText(
        find.byKey(LoginScreen.senhaFieldKey),
        'Secret123',
      );
      await tester.ensureVisible(find.byKey(LoginScreen.entrarButtonKey));
      await tester.tap(find.byKey(LoginScreen.entrarButtonKey));
      await tester.pump();
      await tester.pumpAndSettle();

      expect(find.text('Entrar em uma empresa'), findsOneWidget);
      expect(find.textContaining('Sua conta já está pronta'), findsOneWidget);
      final prefs = await SharedPreferences.getInstance();
      expect(prefs.getString('session.accessToken'), isNotNull);
      expect(prefs.getString('session.password'), isNull);
      expect(prefs.getString('senha'), isNull);
    },
  );

  testWidgets(
    'login de motorista sem empresa mostra estado de vinculo pendente',
    (tester) async {
      final client = MockClient((request) async {
        expect(request.url.path, '/login');
        expect(request.method, 'POST');
        return http.Response(
          jsonEncode({
            'id': 31,
            'tipoUsuario': 'Motorista',
            'mensagem': 'Bem-vindo, Motorista Joao!',
            'companyLinkStatus': 'NO_COMPANY',
            'accessToken': _jwtWithoutCompanyId(),
            'refreshToken': 'refresh-token-driver',
          }),
          200,
          headers: {'content-type': 'application/json; charset=utf-8'},
        );
      });

      await tester.pumpWidget(MaterialApp(home: LoginScreen(client: client)));

      await tester.enterText(
        find.byKey(LoginScreen.emailFieldKey),
        'joao.motorista@vanrides.dev',
      );
      await tester.enterText(
        find.byKey(LoginScreen.senhaFieldKey),
        'Driver!123',
      );
      await tester.ensureVisible(find.byKey(LoginScreen.entrarButtonKey));
      await tester.tap(find.byKey(LoginScreen.entrarButtonKey));
      await tester.pump();
      await tester.pumpAndSettle();

      expect(find.byKey(DriverCompanyPendingScreen.screenKey), findsOneWidget);
      expect(find.text('Conecte sua conta a uma empresa'), findsOneWidget);
      expect(find.text('joao.motorista@vanrides.dev'), findsOneWidget);
    },
  );

  testWidgets('payload inesperado nao aparece como erro de conexão', (
    tester,
  ) async {
    final client = MockClient((request) async {
      return http.Response(
        jsonEncode({'message': 'ok'}),
        200,
        headers: {'content-type': 'application/json; charset=utf-8'},
      );
    });

    await tester.pumpWidget(MaterialApp(home: LoginScreen(client: client)));

    await tester.enterText(
      find.byKey(LoginScreen.emailFieldKey),
      'bruna@vanrides.dev',
    );
    await tester.enterText(find.byKey(LoginScreen.senhaFieldKey), 'Secret123');
    await tester.ensureVisible(find.byKey(LoginScreen.entrarButtonKey));
    await tester.tap(find.byKey(LoginScreen.entrarButtonKey));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 200));

    expect(find.text('Resposta inválida do servidor.'), findsOneWidget);
    expect(find.text('Erro de conexão.'), findsNothing);
  });

  testWidgets('carrega credenciais lembradas a partir do AppSessionStore', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({
      'lembrar': true,
      'email': 'lembrar@vanrides.dev',
      'senha': 'Senha123',
    });

    await tester.pumpWidget(
      MaterialApp(
        home: LoginScreen(
          client: MockClient((_) async => http.Response('{}', 500)),
        ),
      ),
    );
    await tester.pumpAndSettle();

    final emailField = tester.widget<TextFormField>(
      find.byKey(LoginScreen.emailFieldKey),
    );
    final senhaField = tester.widget<TextFormField>(
      find.byKey(LoginScreen.senhaFieldKey),
    );

    expect(emailField.controller!.text, 'lembrar@vanrides.dev');
    expect(senhaField.controller!.text, 'Senha123');
    expect(
      tester.widget<Checkbox>(find.byKey(LoginScreen.lembrarCheckboxKey)).value,
      isTrue,
    );

    final prefs = await SharedPreferences.getInstance();
    expect(prefs.getString('senha'), isNull);
  });
}

String _jwtWithoutCompanyId() {
  final header = base64Url
      .encode(utf8.encode(jsonEncode({'alg': 'HS256', 'typ': 'JWT'})))
      .replaceAll('=', '');
  final payload = base64Url
      .encode(utf8.encode(jsonEncode({'sub': 'bruna@vanrides.dev'})))
      .replaceAll('=', '');
  return '$header.$payload.signature';
}
