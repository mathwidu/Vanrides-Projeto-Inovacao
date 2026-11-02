import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:vanrides_frontend_dev/app/session/app_company_link_status.dart';
import 'package:vanrides_frontend_dev/app/session/app_session_user.dart';
import 'package:vanrides_frontend_dev/app/session/app_user_role.dart';
import 'package:vanrides_frontend_dev/app/theme/vanrides_design_tokens.dart';
import 'package:vanrides_frontend_dev/app/theme/vanrides_theme.dart';
import 'package:vanrides_frontend_dev/features/auth/presentation/email_verification_pending_screen.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_company_link_flow_models.dart';
import 'package:vanrides_frontend_dev/features/admin/presentation/widgets/admin_vinculos_onboarding_widgets.dart';
import 'package:vanrides_frontend_dev/features/passenger_onboarding/data/passenger_onboarding_service.dart';
import 'package:vanrides_frontend_dev/features/passenger_onboarding/domain/passenger_onboarding_models.dart';
import 'package:vanrides_frontend_dev/features/passenger_onboarding/presentation/passenger_company_link_flow.dart';

const _session = AppSessionUser(
  userId: 42,
  displayName: 'Ana Passageira',
  email: 'ana.passageira@vanrides.dev',
  password: 'Senha123',
  role: AppUserRole.passenger,
  backendRoleLabel: 'PASSENGER',
  accessToken: 'token.test',
);

void main() {
  testWidgets(
    'visual smoke do onboarding hibrido do passageiro e portal',
    (tester) async {
      await _captureGate(tester);
      await _captureSearch(tester);
      await _capturePending(tester);
      await _captureInvite(tester);
      await _captureLinked(tester);
      await _capturePendingEmail(tester);
      await _captureAdminPortal(tester);
    },
    timeout: const Timeout(Duration(minutes: 5)),
  );
}

Future<void> _captureGate(WidgetTester tester) async {
  await _pumpMobile(
    tester,
    const PassengerCompanyGateScreen(
      sessionUser: _session,
      gateway: _VisualPassengerOnboardingGateway(),
    ),
  );
  await _visualReady(tester, 'passenger-onboarding-gate');
}

Future<void> _captureSearch(WidgetTester tester) async {
  await _pumpMobile(
    tester,
    const PassengerCompanySearchScreen(
      sessionUser: _session,
      gateway: _VisualPassengerOnboardingGateway(),
    ),
  );
  await tester.enterText(find.byType(TextField), 'VanRides');
  await tester.tap(find.text('Buscar'));
  await tester.pumpAndSettle();
  await _visualReady(tester, 'passenger-onboarding-search');
}

Future<void> _capturePending(WidgetTester tester) async {
  const status = PassengerOnboardingStatusRecord(
    companyLinkStatus: AppCompanyLinkStatus.pendingApproval,
    companyName: 'VanRides Escolar',
    pendingRequest: PendingCompanyLinkRequestRecord(
      id: 91,
      companyId: 12,
      companyName: 'VanRides Escolar',
      status: 'PENDING',
    ),
  );
  await _pumpMobile(
    tester,
    const PassengerCompanyPendingScreen(
      sessionUser: _session,
      status: status,
      gateway: _VisualPassengerOnboardingGateway(),
    ),
  );
  await _visualReady(tester, 'passenger-onboarding-pending');
}

Future<void> _captureInvite(WidgetTester tester) async {
  await _pumpMobile(
    tester,
    const PassengerInviteCodeScreen(
      sessionUser: _session,
      gateway: _VisualPassengerOnboardingGateway(),
    ),
  );
  await tester.enterText(find.byType(TextField), 'VAN-2026');
  await tester.pumpAndSettle();
  await _visualReady(tester, 'passenger-onboarding-invite');
}

Future<void> _captureLinked(WidgetTester tester) async {
  await _pumpMobile(
    tester,
    PassengerLinkedPendingDriverScreen(
      sessionUser: _session.copyWith(
        companyId: 12,
        companyLinkStatus: AppCompanyLinkStatus.companyLinked,
      ),
      gateway: const _VisualPassengerOnboardingGateway(),
    ),
  );
  await _visualReady(tester, 'passenger-onboarding-linked');
}

Future<void> _capturePendingEmail(WidgetTester tester) async {
  await _pumpMobile(
    tester,
    const EmailVerificationPendingScreen(
      email: 'ana.passageira@vanrides.dev',
      variant: EmailVerificationPendingVariant.loginBlocked,
    ),
  );
  await _visualReady(tester, 'passenger-onboarding-email-pending');
}

Future<void> _captureAdminPortal(WidgetTester tester) async {
  await _pumpMobile(
    tester,
    Scaffold(
      backgroundColor: VanRidesDesignTokens.backgroundDeep,
      body: SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(16, 64, 16, 24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            AdminVinculosAccessRequestsPanel(
              requests: const [
                AdminAccessRequestRecord(
                  id: 41,
                  passageiroId: 11,
                  passageiroNome: 'Ana Passageira',
                  passageiroEmail: 'ana.passageira@vanrides.dev',
                  companyName: 'VanRides Escolar',
                  source: 'SEARCH_REQUEST',
                  emailVerified: true,
                ),
              ],
              onRefresh: () async {},
              onOpenRequest: (_) async {},
            ),
            const SizedBox(height: 20),
            AdminVinculosInviteCodesPanel(
              inviteCodes: [
                AdminInviteCodeRecord(
                  id: 51,
                  code: 'VAN-2026',
                  status: 'ACTIVE',
                  useCount: 0,
                  maxUses: 3,
                  expiresAt: DateTime(2026, 6, 30, 18),
                ),
                const AdminInviteCodeRecord(
                  id: 52,
                  code: 'VAN-USED',
                  status: 'USED_UP',
                  useCount: 1,
                  maxUses: 1,
                ),
              ],
              onCreateInvite: () async {},
              onOpenInvite: (_) async {},
            ),
          ],
        ),
      ),
    ),
  );
  await _visualReady(tester, 'admin-onboarding-portal');
}

Future<void> _pumpMobile(WidgetTester tester, Widget child) async {
  await tester.binding.setSurfaceSize(const Size(390, 844));
  addTearDown(() => tester.binding.setSurfaceSize(null));
  await _pump(tester, child);
}

Future<void> _pump(WidgetTester tester, Widget child) async {
  await tester.pumpWidget(
    MaterialApp(
      debugShowCheckedModeBanner: false,
      theme: VanRidesTheme.dark(),
      home: child,
    ),
  );
  await tester.pumpAndSettle();
}

Future<void> _visualReady(WidgetTester tester, String state) async {
  await tester.pumpAndSettle(const Duration(milliseconds: 250));
  debugPrintSynchronously('VANRIDES_PASSENGER_ONBOARDING_VISUAL_READY:$state');
  await tester.pump(const Duration(seconds: 2));
}

class _VisualPassengerOnboardingGateway implements PassengerOnboardingGateway {
  const _VisualPassengerOnboardingGateway();

  @override
  Future<PassengerOnboardingStatusRecord> createRequest(
    AppSessionUser session, {
    required int companyId,
  }) async {
    return const PassengerOnboardingStatusRecord(
      companyLinkStatus: AppCompanyLinkStatus.pendingApproval,
      companyName: 'VanRides Escolar',
      pendingRequest: PendingCompanyLinkRequestRecord(
        id: 91,
        companyId: 12,
        companyName: 'VanRides Escolar',
        status: 'PENDING',
      ),
    );
  }

  @override
  Future<PassengerOnboardingStatusRecord> fetchStatus(
    AppSessionUser session,
  ) async {
    return const PassengerOnboardingStatusRecord(
      companyLinkStatus: AppCompanyLinkStatus.noCompany,
    );
  }

  @override
  Future<PassengerOnboardingStatusRecord> redeemInviteCode(
    AppSessionUser session, {
    required String code,
  }) async {
    return const PassengerOnboardingStatusRecord(
      companyLinkStatus: AppCompanyLinkStatus.companyLinked,
      companyId: 12,
      companyName: 'VanRides Escolar',
    );
  }

  @override
  Future<List<PassengerOnboardingCompanyRecord>> searchCompanies(
    AppSessionUser session, {
    String? query,
  }) async {
    if ((query ?? '').trim().isEmpty) {
      return const [];
    }
    return const [
      PassengerOnboardingCompanyRecord(
        id: 12,
        nome: 'VanRides Escolar',
        slug: 'vanrides-escolar',
      ),
      PassengerOnboardingCompanyRecord(
        id: 13,
        nome: 'Feevale Shuttle',
        slug: 'feevale-shuttle',
      ),
    ];
  }

  @override
  Future<AppSessionUser> upgradeSession(
    AppSessionUser session, {
    int? motoristaId,
    String? companyName,
  }) async {
    return session.copyWith(
      companyId: 12,
      companyLinkStatus: AppCompanyLinkStatus.companyLinked,
    );
  }
}
