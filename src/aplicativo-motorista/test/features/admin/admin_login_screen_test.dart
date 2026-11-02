import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/admin/data/admin_portal_auth_service.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_company_admin_bootstrap_input.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_company_admin_bootstrap_result.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_portal_exception.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_portal_session.dart';
import 'package:vanrides_frontend_dev/features/admin/presentation/admin_login_screen.dart';

import '../../support/fakes/fake_account_profile_gateway.dart';

void main() {
  testWidgets('admin login autentica e entra no portal', (tester) async {
    await tester.binding.setSurfaceSize(const Size(1400, 1200));

    await tester.pumpWidget(
      MaterialApp(
        home: AdminLoginScreen(
          gateway: _FakeAdminPortalAuthGateway(),
          accountProfileGatewayBuilder:
              (session, senha) => const FakeAccountProfileGateway.admin(),
        ),
      ),
    );

    expect(find.text('Entrar no portal'), findsWidgets);

    await tester.enterText(
      find.byKey(AdminLoginScreen.emailFieldKey),
      'admin@vanrides.dev',
    );
    await tester.enterText(
      find.byKey(AdminLoginScreen.passwordFieldKey),
      'Portal!123',
    );

    await tester.ensureVisible(find.byKey(AdminLoginScreen.enterButtonKey));
    await tester.tap(find.byKey(AdminLoginScreen.enterButtonKey));
    await tester.pumpAndSettle();

    expect(find.text('Dashboard Operacional'), findsOneWidget);
    expect(find.text('Feevale'), findsOneWidget);
    expect(find.text('Portal Empresa'), findsNothing);
  });

  testWidgets(
    'admin com email pendente fica bloqueado e reenvio tem cooldown',
    (tester) async {
      await tester.binding.setSurfaceSize(const Size(1400, 1200));
      final gateway = _FakeAdminPortalAuthGateway(
        loginError: const AdminPortalException(
          'O e-mail informado ainda não foi verificado.',
          code: 'EMAIL_NOT_VERIFIED',
          email: 'pendente@vanrides.dev',
        ),
      );

      await tester.pumpWidget(
        MaterialApp(home: AdminLoginScreen(gateway: gateway)),
      );

      await tester.enterText(
        find.byKey(AdminLoginScreen.emailFieldKey),
        'pendente@vanrides.dev',
      );
      await tester.enterText(
        find.byKey(AdminLoginScreen.passwordFieldKey),
        'Portal!123',
      );
      await tester.tap(find.byKey(AdminLoginScreen.enterButtonKey));
      await tester.pumpAndSettle();

      expect(find.text('Acesso bloqueado'), findsOneWidget);
      expect(find.text('pendente@vanrides.dev'), findsOneWidget);
      expect(find.text('Dashboard Operacional'), findsNothing);

      await tester.tap(find.text('Reenviar verificação'));
      await tester.pump();
      await tester.pumpAndSettle();

      expect(gateway.resendEmails, ['pendente@vanrides.dev']);
      expect(find.text('Reenviar em 15:00'), findsOneWidget);
      expect(
        find.text('Você poderá solicitar um novo envio em 15:00.'),
        findsOneWidget,
      );
    },
  );
}

class _FakeAdminPortalAuthGateway implements AdminPortalAuthGateway {
  _FakeAdminPortalAuthGateway({this.loginError});

  final AdminPortalException? loginError;
  final List<String> resendEmails = [];

  @override
  Future<AdminCompanyAdminBootstrapResult> bootstrap(
    AdminCompanyAdminBootstrapInput input,
  ) async {
    return const AdminCompanyAdminBootstrapResult(
      id: 1,
      companyId: 1,
      companySlug: 'vanrides-default',
      nome: 'Admin Portal',
      email: 'admin@vanrides.dev',
      telefone: '51999999999',
    );
  }

  @override
  Future<AdminPortalSession> login({
    required String email,
    required String senha,
  }) async {
    final error = loginError;
    if (error != null) {
      throw error;
    }

    return const AdminPortalSession(
      adminId: 1,
      adminName: 'Admin Portal',
      companyId: 1,
      companyName: '',
      companySlug: 'vanrides-default',
      email: 'admin@vanrides.dev',
      accessToken: 'token.admin',
    );
  }

  @override
  Future<String> resendVerification({required String email}) async {
    resendEmails.add(email);
    return 'Verificação reenviada para $email';
  }

  @override
  Future<String> changePendingEmail({
    required String currentEmail,
    required String senha,
    required String newEmail,
  }) async {
    return newEmail;
  }
}
