import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/app/experience/experience_home_router.dart';
import 'package:vanrides_frontend_dev/app/session/app_company_link_status.dart';
import 'package:vanrides_frontend_dev/app/session/app_session_user.dart';
import 'package:vanrides_frontend_dev/app/session/app_user_role.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/home_passageiro_screen.dart';
import 'package:vanrides_frontend_dev/l10n/app_strings.dart';

void main() {
  testWidgets('roteia guardian para shell correto', (tester) async {
    const sessionUser = AppSessionUser(
      companyId: 1,
      accessToken: 'token.guardian',
      userId: 10,
      displayName: 'Mae da Ana',
      email: 'guardian@vanrides.dev',
      password: 'senha123',
      role: AppUserRole.guardian,
      backendRoleLabel: 'Responsavel',
    );

    await tester.pumpWidget(
      const MaterialApp(home: ExperienceHomeRouter(sessionUser: sessionUser)),
    );

    expect(find.textContaining('Mae da Ana'), findsOneWidget);
    expect(find.text(AppStrings.guardian.inicio), findsOneWidget);
  });

  testWidgets('roteia admin para shell web', (tester) async {
    const sessionUser = AppSessionUser(
      companyId: 1,
      accessToken: 'token.admin',
      userId: 1,
      displayName: 'Empresa Admin',
      email: 'admin@vanrides.dev',
      password: 'senha123',
      role: AppUserRole.companyAdmin,
      backendRoleLabel: 'Company_Admin',
    );

    await tester.pumpWidget(
      const MaterialApp(home: ExperienceHomeRouter(sessionUser: sessionUser)),
    );

    expect(find.text('Dashboard Operacional'), findsOneWidget);
  });

  testWidgets('admin sem empresa recebe estado seguro sem copy tecnica', (
    tester,
  ) async {
    const sessionUser = AppSessionUser(
      accessToken: 'token.admin',
      userId: 1,
      displayName: 'Empresa Admin',
      email: 'admin@vanrides.dev',
      password: 'senha123',
      role: AppUserRole.companyAdmin,
      backendRoleLabel: 'Company_Admin',
    );

    await tester.pumpWidget(
      const MaterialApp(home: ExperienceHomeRouter(sessionUser: sessionUser)),
    );

    expect(find.text('Conta em análise'), findsOneWidget);
    expect(
      find.textContaining('Administrador sem empresa vinculada'),
      findsOneWidget,
    );
    expect(find.textContaining('contexto'), findsNothing);
    expect(find.textContaining('token'), findsNothing);
  });

  testWidgets(
    'responsavel sem vinculo recebe estado seguro sem copy tecnica',
    (tester) async {
      const sessionUser = AppSessionUser(
        accessToken: 'token.guardian',
        userId: 10,
        displayName: 'Mae da Ana',
        email: 'guardian@vanrides.dev',
        password: 'senha123',
        role: AppUserRole.guardian,
        backendRoleLabel: 'Responsavel',
      );

      await tester.pumpWidget(
        const MaterialApp(home: ExperienceHomeRouter(sessionUser: sessionUser)),
      );

      expect(find.text('Conta em análise'), findsOneWidget);
      expect(
        find.textContaining('Responsável familiar sem vínculo ativo'),
        findsOneWidget,
      );
      expect(find.textContaining('sessao'), findsNothing);
      expect(find.textContaining('token'), findsNothing);
    },
  );

  testWidgets('roteia passageiro sem empresa para onboarding', (tester) async {
    const sessionUser = AppSessionUser(
      userId: 22,
      displayName: 'Bruna',
      email: 'bruna@vanrides.dev',
      password: 'senha123',
      role: AppUserRole.passenger,
      backendRoleLabel: 'Passageiro',
      accessToken: 'token.passenger',
      companyLinkStatus: AppCompanyLinkStatus.noCompany,
    );

    await tester.pumpWidget(
      const MaterialApp(home: ExperienceHomeRouter(sessionUser: sessionUser)),
    );

    expect(
      find.text(AppStrings.passageiro.entrarEmUmaEmpresaTitulo),
      findsOneWidget,
    );
    expect(find.textContaining('Sua conta já está pronta'), findsOneWidget);
  });

  testWidgets(
    'roteia passageiro vinculado sem motorista para home operacional',
    (tester) async {
      const sessionUser = AppSessionUser(
        companyId: 1,
        accessToken: 'token.passenger',
        userId: 22,
        displayName: 'Bruna',
        email: 'bruna@vanrides.dev',
        password: 'senha123',
        role: AppUserRole.passenger,
        backendRoleLabel: 'Passageiro',
      );

      await tester.pumpWidget(
        const MaterialApp(home: ExperienceHomeRouter(sessionUser: sessionUser)),
      );
      await tester.pump();

      expect(find.byType(HomePassageiroScreen), findsOneWidget);
      final screen = tester.widget<HomePassageiroScreen>(
        find.byType(HomePassageiroScreen),
      );
      expect(screen.motoristaId, isNull);
      expect(screen.accountProfileGateway, isNotNull);
      expect(
        find.text(AppStrings.passageiro.empresaVinculadaTitulo),
        findsNothing,
      );
    },
  );
}
