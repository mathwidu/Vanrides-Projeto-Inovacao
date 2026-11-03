import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/app/session/app_company_link_status.dart';
import 'package:vanrides_frontend_dev/app/session/app_session_store.dart';
import 'package:vanrides_frontend_dev/app/session/app_session_user.dart';
import 'package:vanrides_frontend_dev/app/session/app_user_role.dart';
import 'package:vanrides_frontend_dev/features/driver_onboarding/data/driver_onboarding_service.dart';
import 'package:vanrides_frontend_dev/features/driver_onboarding/domain/driver_onboarding_models.dart';
import 'package:vanrides_frontend_dev/features/driver_onboarding/presentation/driver_invite_screen.dart';

const _driverSession = AppSessionUser(
  userId: 31,
  displayName: 'Joao Motorista',
  email: 'joao@vanrides.dev',
  password: 'Driver!123',
  role: AppUserRole.driver,
  backendRoleLabel: 'Motorista',
  accessToken: 'token.driver',
  companyLinkStatus: AppCompanyLinkStatus.noCompany,
);

void main() {
  testWidgets('convite por link mostra passos diretos sem aprovacao manual', (
    tester,
  ) async {
    final store = _MemorySessionStore();

    await tester.pumpWidget(
      MaterialApp(
        home: DriverInviteScreen(
          token: 'DRV-2026-OK',
          gateway: _FakeDriverOnboardingGateway(),
          sessionStore: store,
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Feevale Vans'), findsOneWidget);
    expect(find.text('Link recebido'), findsOneWidget);
    expect(find.text('Conta de motorista'), findsOneWidget);
    expect(find.text('E-mail confirmado'), findsOneWidget);
    expect(find.text('Empresa vinculada'), findsOneWidget);
    expect(find.text('Entrar com este convite'), findsOneWidget);
    expect(find.text('Criar conta de motorista'), findsOneWidget);
    expect(find.textContaining('Aguardando aprovação'), findsNothing);
    expect(store.pendingDriverInviteToken, 'DRV-2026-OK');
  });

  testWidgets('motorista logado resgata convite sem mensagem de pendencia', (
    tester,
  ) async {
    final store = _MemorySessionStore();
    final gateway = _FakeDriverOnboardingGateway();

    await tester.pumpWidget(
      MaterialApp(
        home: DriverInviteScreen(
          token: 'DRV-2026-OK',
          sessionUser: _driverSession,
          gateway: gateway,
          sessionStore: store,
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.ensureVisible(find.byKey(DriverInviteScreen.redeemButtonKey));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(DriverInviteScreen.redeemButtonKey));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));

    expect(gateway.redeemedTokens, ['DRV-2026-OK']);
    expect(store.current?.companyLinkStatus, AppCompanyLinkStatus.companyLinked);
    expect(store.pendingDriverInviteToken, isNull);
    expect(find.textContaining('Aguardando aprovação'), findsNothing);
  });
}

class _FakeDriverOnboardingGateway implements DriverOnboardingGateway {
  final List<String> redeemedTokens = [];

  @override
  Future<DriverInvitePreviewRecord> previewInvite(String token) async {
    return const DriverInvitePreviewRecord(
      companyId: 7,
      companyName: 'Feevale Vans',
      role: 'DRIVER',
      status: 'ACTIVE',
    );
  }

  @override
  Future<DriverOnboardingStatusRecord> redeemInvite(
    AppSessionUser session, {
    required String token,
  }) async {
    redeemedTokens.add(token);
    return const DriverOnboardingStatusRecord(
      companyLinkStatus: AppCompanyLinkStatus.companyLinked,
      companyId: 7,
      companyName: 'Feevale Vans',
      activeMembershipId: 99,
    );
  }

  @override
  Future<DriverOnboardingStatusRecord> fetchStatus(
    AppSessionUser session,
  ) async {
    return const DriverOnboardingStatusRecord(
      companyLinkStatus: AppCompanyLinkStatus.noCompany,
    );
  }

  @override
  Future<List<DriverOnboardingCompanyRecord>> searchCompanies(
    AppSessionUser session, {
    String? query,
  }) async {
    return const [];
  }

  @override
  Future<DriverOnboardingStatusRecord> createRequest(
    AppSessionUser session, {
    required int companyId,
  }) async {
    return const DriverOnboardingStatusRecord(
      companyLinkStatus: AppCompanyLinkStatus.pendingApproval,
    );
  }

  @override
  Future<AppSessionUser> upgradeSession(
    AppSessionUser session, {
    String? companyName,
  }) async {
    return session.copyWith(
      companyId: 7,
      accessToken: 'token.driver.linked',
      companyLinkStatus: AppCompanyLinkStatus.companyLinked,
    );
  }
}

class _MemorySessionStore extends AppSessionStore {
  AppSessionUser? current;
  String? pendingDriverInviteToken;

  @override
  Future<void> writePendingDriverInviteToken(String token) async {
    pendingDriverInviteToken = token.trim().isEmpty ? null : token.trim();
  }

  @override
  Future<void> clearPendingDriverInviteToken() async {
    pendingDriverInviteToken = null;
  }

  @override
  Future<void> writeCurrent(AppSessionUser user) async {
    current = user;
  }
}
