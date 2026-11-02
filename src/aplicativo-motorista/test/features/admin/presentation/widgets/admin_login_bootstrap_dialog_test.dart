import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/admin/data/admin_portal_auth_service.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_company_admin_bootstrap_input.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_company_admin_bootstrap_result.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_portal_session.dart';
import 'package:vanrides_frontend_dev/features/admin/presentation/widgets/admin_login_bootstrap_dialog.dart';

void main() {
  testWidgets('envia bootstrap valido normalizado e retorna senha', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(900, 1000));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    final gateway = _FakeAdminPortalAuthGateway();
    AdminLoginBootstrapResult? dialogResult;

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: Builder(
            builder:
                (context) => TextButton(
                  key: const Key('open.bootstrap.dialog'),
                  onPressed: () async {
                    dialogResult = await showDialog<AdminLoginBootstrapResult>(
                      context: context,
                      builder:
                          (_) => AdminLoginBootstrapDialog(gateway: gateway),
                    );
                  },
                  child: const Text('Abrir bootstrap'),
                ),
          ),
        ),
      ),
    );

    await tester.tap(find.byKey(const Key('open.bootstrap.dialog')));
    await tester.pumpAndSettle();

    expect(find.text('Empresa'), findsOneWidget);
    expect(find.text('Administrador'), findsOneWidget);
    expect(find.text('E-mail de verificação'), findsOneWidget);

    await tester.enterText(
      find.byKey(AdminLoginBootstrapDialog.companyNameFieldKey),
      ' Escola VanRides ',
    );
    await tester.enterText(
      find.byKey(AdminLoginBootstrapDialog.adminNameFieldKey),
      ' Admin Portal ',
    );
    await tester.enterText(
      find.byKey(AdminLoginBootstrapDialog.emailFieldKey),
      ' ADMIN@VANRIDES.DEV ',
    );
    await tester.enterText(
      find.byKey(AdminLoginBootstrapDialog.phoneFieldKey),
      ' 51999999999 ',
    );
    await tester.enterText(
      find.byKey(AdminLoginBootstrapDialog.passwordFieldKey),
      'Portal!123',
    );

    await tester.ensureVisible(
      find.byKey(AdminLoginBootstrapDialog.createButtonKey),
    );
    await tester.tap(find.byKey(AdminLoginBootstrapDialog.createButtonKey));
    await tester.pumpAndSettle();

    expect(gateway.bootstrapCalls, 1);
    expect(gateway.lastBootstrapInput?.companyName, 'Escola VanRides');
    expect(gateway.lastBootstrapInput?.nome, 'Admin Portal');
    expect(gateway.lastBootstrapInput?.email, 'admin@vanrides.dev');
    expect(gateway.lastBootstrapInput?.telefone, '51999999999');
    expect(gateway.lastBootstrapInput?.senha, 'Portal!123');
    expect(dialogResult?.bootstrap.companyDisplayName, 'Escola VanRides');
    expect(dialogResult?.password, 'Portal!123');
  });

  testWidgets('valida campos obrigatorios antes de chamar gateway', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(900, 1000));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    final gateway = _FakeAdminPortalAuthGateway();

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: Builder(
            builder:
                (context) => TextButton(
                  key: const Key('open.bootstrap.dialog'),
                  onPressed: () {
                    showDialog<AdminLoginBootstrapResult>(
                      context: context,
                      builder:
                          (_) => AdminLoginBootstrapDialog(gateway: gateway),
                    );
                  },
                  child: const Text('Abrir bootstrap'),
                ),
          ),
        ),
      ),
    );

    await tester.tap(find.byKey(const Key('open.bootstrap.dialog')));
    await tester.pumpAndSettle();

    await tester.ensureVisible(
      find.byKey(AdminLoginBootstrapDialog.createButtonKey),
    );
    await tester.tap(find.byKey(AdminLoginBootstrapDialog.createButtonKey));
    await tester.pump();

    expect(gateway.bootstrapCalls, 0);
    expect(find.text('Informe o nome da empresa'), findsOneWidget);
    expect(find.text('Informe o nome do admin'), findsOneWidget);
    expect(find.text('Informe um e-mail válido'), findsOneWidget);
    expect(find.text('Informe o telefone'), findsOneWidget);
    expect(find.text('Mínimo de 8 caracteres'), findsWidgets);
  });
}

class _FakeAdminPortalAuthGateway implements AdminPortalAuthGateway {
  int bootstrapCalls = 0;
  AdminCompanyAdminBootstrapInput? lastBootstrapInput;

  @override
  Future<AdminCompanyAdminBootstrapResult> bootstrap(
    AdminCompanyAdminBootstrapInput input,
  ) async {
    bootstrapCalls += 1;
    lastBootstrapInput = input;
    return AdminCompanyAdminBootstrapResult(
      id: 1,
      companyId: 10,
      companySlug: 'escola-vanrides',
      companyName: input.companyName,
      nome: input.nome,
      email: input.email,
      telefone: input.telefone,
    );
  }

  @override
  Future<String> changePendingEmail({
    required String currentEmail,
    required String senha,
    required String newEmail,
  }) {
    throw UnimplementedError();
  }

  @override
  Future<AdminPortalSession> login({
    required String email,
    required String senha,
  }) {
    throw UnimplementedError();
  }

  @override
  Future<String> resendVerification({required String email}) {
    throw UnimplementedError();
  }
}
