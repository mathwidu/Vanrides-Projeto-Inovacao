import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:vanrides_frontend_dev/app/theme/vanrides_design_tokens.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_portal_session.dart';
import 'package:vanrides_frontend_dev/features/admin/presentation/admin_login_screen.dart';
import 'package:vanrides_frontend_dev/features/admin/presentation/admin_shell_screen.dart';
import 'package:vanrides_frontend_dev/features/admin/presentation/widgets/admin_portal_layout.dart';

import '../../support/fakes/fake_account_profile_gateway.dart';

void main() {
  testWidgets('dashboard inicial mostra proxima acao e atalhos', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1440, 1400));

    const session = AdminPortalSession(
      adminId: 1,
      adminName: 'Operador VanRides',
      companyId: 1,
      companyName: 'Portal Empresa',
      companySlug: 'vanrides-default',
      email: 'admin@vanrides.dev',
      accessToken: 'token.admin',
    );

    await tester.pumpWidget(
      const MaterialApp(home: AdminShellScreen(session: session)),
    );
    await tester.pumpAndSettle();

    expect(tester.takeException(), isNull);
    expect(find.text('Dashboard Operacional'), findsOneWidget);
    expect(find.text('MOTORISTAS ATIVOS'), findsOneWidget);
    expect(find.text('Próxima ação'), findsOneWidget);
    expect(find.text('Atalhos'), findsOneWidget);
    expect(find.text('Resumo do dia'), findsOneWidget);

    final scaffold = tester.widget<Scaffold>(find.byType(Scaffold).first);
    expect(scaffold.backgroundColor, AdminPortalColors.canvas);
    expect(AdminPortalColors.surface, isNot(Colors.white));
  });

  testWidgets('prioriza serviços e viagens no menu operacional', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1440, 1400));

    const session = AdminPortalSession(
      adminId: 1,
      adminName: 'Operador VanRides',
      companyId: 1,
      companyName: 'Portal Empresa',
      companySlug: 'vanrides-default',
      email: 'admin@vanrides.dev',
      accessToken: 'token.admin',
    );

    await tester.pumpWidget(
      const MaterialApp(home: AdminShellScreen(session: session)),
    );
    await tester.pumpAndSettle();

    expect(tester.takeException(), isNull);
    expect(find.text('Serviços'), findsWidgets);
    expect(find.text('Operação'), findsOneWidget);
    expect(find.text('Pessoas'), findsOneWidget);
    expect(find.text('Viagens'), findsNothing);
    final viagensDoDiaButton = find.text('Viagens do dia').first;
    expect(find.text('Viagens do dia'), findsWidgets);
    expect(find.text('Rotas'), findsNothing);
    expect(find.text('Vínculos'), findsNothing);
    expect(find.text('Avançado'), findsOneWidget);
    expect(find.text('Vínculos avançados'), findsNothing);
    expect(find.text('Onboarding e vínculos'), findsOneWidget);
    expect(find.text('Rotas-base'), findsOneWidget);
    expect(find.text('Configurações'), findsNothing);
    expect(
      find.text('Central do dia, pendências e próximas ações'),
      findsOneWidget,
    );
    expect(find.byIcon(Icons.notifications_none_outlined), findsNothing);
    expect(find.byIcon(Icons.help_outline), findsNothing);

    await tester.tap(viagensDoDiaButton);
    await tester.pump();

    expect(
      find.text('Montagem, manutenção e exceções da data'),
      findsOneWidget,
    );

    await tester.tap(find.text('Rotas-base'));
    await tester.pump();

    expect(find.text('Rotas-base legadas e compatibilidade'), findsOneWidget);
  });

  testWidgets(
    'setup operacional navega por fichas sem abrir vinculos tecnicos',
    (tester) async {
      await tester.binding.setSurfaceSize(const Size(1440, 1400));

      const session = AdminPortalSession(
        adminId: 1,
        adminName: 'Operador VanRides',
        companyId: 1,
        companyName: 'Portal Empresa',
        companySlug: 'vanrides-default',
        email: 'admin@vanrides.dev',
        accessToken: 'token.admin',
      );

      await tester.pumpWidget(
        const MaterialApp(home: AdminShellScreen(session: session)),
      );
      await tester.pumpAndSettle();

      expect(find.text('Onboarding e vínculos'), findsOneWidget);
      expect(find.text('Onboarding de acessos e vínculos'), findsNothing);

      await tester.tap(find.text('Passageiros').first);
      await tester.pump();
      expect(
        find.text('Cadastro, parada, responsáveis e serviços'),
        findsOneWidget,
      );
      expect(find.text('Onboarding de acessos e vínculos'), findsNothing);

      await tester.tap(find.text('Motoristas').first);
      await tester.pump();
      expect(
        find.text('Convites, solicitações e motoristas ativos'),
        findsOneWidget,
      );
      expect(find.text('Onboarding de acessos e vínculos'), findsNothing);

      await tester.tap(find.text('Responsáveis').first);
      await tester.pump();
      expect(find.text('Responsáveis, menores e permissões'), findsOneWidget);
      expect(find.text('Onboarding de acessos e vínculos'), findsNothing);

      await tester.tap(find.text('Conta').first);
      await tester.pump();
      expect(find.text('Perfil, empresa e segurança'), findsOneWidget);
      expect(find.text('Onboarding de acessos e vínculos'), findsNothing);
    },
  );

  testWidgets('tema administrativo padroniza feedback e controles', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1440, 1400));

    const session = AdminPortalSession(
      adminId: 1,
      adminName: 'Operador VanRides',
      companyId: 1,
      companyName: 'Portal Empresa',
      companySlug: 'vanrides-default',
      email: 'admin@vanrides.dev',
      accessToken: 'token.admin',
    );

    await tester.pumpWidget(
      const MaterialApp(home: AdminShellScreen(session: session)),
    );
    await tester.pumpAndSettle();

    final theme = Theme.of(tester.element(find.byType(Scaffold).first));

    expect(theme.brightness, Brightness.dark);
    expect(theme.colorScheme.surface, AdminPortalColors.surfaceSoft);
    expect(theme.snackBarTheme.behavior, SnackBarBehavior.floating);
    expect(
      theme.snackBarTheme.backgroundColor,
      AdminPortalColors.elevatedSurface,
    );
    expect(theme.popupMenuTheme.color, AdminPortalColors.elevatedSurface);
    expect(
      theme.dropdownMenuTheme.inputDecorationTheme?.fillColor,
      AdminPortalColors.input,
    );
    expect(
      theme.datePickerTheme.backgroundColor,
      AdminPortalColors.elevatedSurface,
    );
    expect(
      theme.filledButtonTheme.style?.backgroundColor?.resolve({
        WidgetState.disabled,
      }),
      AdminPortalColors.surfaceSoft,
    );
  });

  test('portal colors share the operational app color semantics', () {
    expect(AdminPortalColors.canvas, VanRidesDesignTokens.backgroundDeep);
    expect(AdminPortalColors.surface, VanRidesDesignTokens.surfaceGlass);
    expect(AdminPortalColors.surfaceSoft, VanRidesDesignTokens.surface);
    expect(
      AdminPortalColors.elevatedSurface,
      VanRidesDesignTokens.surfaceRaised,
    );
    expect(AdminPortalColors.blue, VanRidesDesignTokens.blueTracking);
    expect(AdminPortalColors.green, VanRidesDesignTokens.greenAction);
    expect(AdminPortalColors.orange, VanRidesDesignTokens.amberWarning);
    expect(AdminPortalColors.red, VanRidesDesignTokens.redDanger);
  });

  testWidgets('sincroniza empresa e admin pelo perfil real', (tester) async {
    await tester.binding.setSurfaceSize(const Size(1440, 1400));

    const session = AdminPortalSession(
      adminId: 1,
      adminName: 'Operador VanRides',
      companyId: 1,
      companyName: '',
      email: 'admin@vanrides.dev',
      accessToken: 'token.admin',
    );

    await tester.pumpWidget(
      const MaterialApp(
        home: AdminShellScreen(
          session: session,
          accountProfileGateway: FakeAccountProfileGateway.admin(),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(tester.takeException(), isNull);
    expect(find.text('Feevale'), findsOneWidget);
    expect(find.text('Portal Empresa'), findsNothing);
  });

  testWidgets('abre conta administrativa com perfil real', (tester) async {
    await tester.binding.setSurfaceSize(const Size(1440, 1400));

    const session = AdminPortalSession(
      adminId: 1,
      adminName: 'Operador VanRides',
      companyId: 1,
      companyName: 'Portal Empresa',
      companySlug: 'vanrides-default',
      email: 'admin@vanrides.dev',
      accessToken: 'token.admin',
    );

    await tester.pumpWidget(
      const MaterialApp(
        home: AdminShellScreen(
          session: session,
          accountProfileGateway: FakeAccountProfileGateway.admin(),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Conta').first);
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('admin.account.darkSurface')), findsOneWidget);
    expect(find.text('Admin Perfil Real'), findsOneWidget);
    expect(find.text('admin.real@vanrides.dev'), findsWidgets);
    expect(find.text('Acesso administrativo'), findsOneWidget);
    expect(find.text('Administrador ativo'), findsOneWidget);
    expect(find.text('E-mail verificado'), findsWidgets);
    expect(
      find.byKey(const Key('admin.account.editProfileButton')),
      findsOneWidget,
    );
  });

  testWidgets('logout administrativo limpa sessao persistida', (tester) async {
    await tester.binding.setSurfaceSize(const Size(1440, 1400));
    SharedPreferences.setMockInitialValues({
      'session.userId': 1,
      'session.displayName': 'Operador VanRides',
      'session.email': 'admin@vanrides.dev',
      'session.tipoUsuario': 'Admin',
      'session.accessToken': 'token.admin',
      'session.refreshToken': 'refresh.admin',
    });

    const session = AdminPortalSession(
      adminId: 1,
      adminName: 'Operador VanRides',
      companyId: 1,
      companyName: 'Portal Empresa',
      companySlug: 'vanrides-default',
      email: 'admin@vanrides.dev',
      accessToken: 'token.admin',
    );

    await tester.pumpWidget(
      const MaterialApp(
        home: AdminShellScreen(
          session: session,
          accountProfileGateway: FakeAccountProfileGateway.admin(),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Conta').first);
    await tester.pumpAndSettle();
    final logoutButton = find.byKey(
      const Key('admin.account.logout.darkButton'),
    );
    await tester.scrollUntilVisible(
      logoutButton,
      240,
      scrollable: find.byType(Scrollable).last,
    );
    await tester.tap(logoutButton);
    await tester.pumpAndSettle();

    final prefs = await SharedPreferences.getInstance();
    expect(prefs.getString('session.accessToken'), isNull);
    expect(prefs.getString('session.refreshToken'), isNull);
    expect(prefs.getInt('session.userId'), isNull);
    expect(find.byType(AdminLoginScreen), findsOneWidget);
  });
}
