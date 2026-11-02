import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/app/session/app_company_link_status.dart';
import 'package:vanrides_frontend_dev/app/session/app_session_store.dart';
import 'package:vanrides_frontend_dev/app/session/app_session_user.dart';
import 'package:vanrides_frontend_dev/app/session/app_user_role.dart';
import 'package:vanrides_frontend_dev/features/auth/presentation/login_screen.dart';
import 'package:vanrides_frontend_dev/features/driver_onboarding/data/driver_onboarding_service.dart';
import 'package:vanrides_frontend_dev/features/driver_onboarding/domain/driver_onboarding_models.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/presentation/driver_company_pending_screen.dart';

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
  testWidgets('sem empresa mostra convite salvo e busca por empresa', (
    tester,
  ) async {
    await _pumpScreen(
      tester,
      gateway: _FakeDriverOnboardingGateway(),
      sessionStore: _MemorySessionStore(pendingDriverInviteToken: 'DRV-OK'),
    );

    expect(find.text('Aceitar convite da empresa'), findsOneWidget);
    expect(find.text('Buscar empresa'), findsOneWidget);
    expect(find.text('Solicitar'), findsOneWidget);
    expect(find.textContaining('Aguardando aprovação'), findsNothing);
  });

  testWidgets('solicitacao manual mostra pendencia sem convite direto', (
    tester,
  ) async {
    await _pumpScreen(
      tester,
      gateway: _FakeDriverOnboardingGateway(
        status: const DriverOnboardingStatusRecord(
          companyLinkStatus: AppCompanyLinkStatus.pendingApproval,
          pendingRequest: PendingDriverCompanyLinkRequestRecord(
            id: 55,
            companyId: 7,
            companyName: 'Feevale Vans',
            status: 'PENDING_APPROVAL',
          ),
        ),
      ),
    );

    expect(find.text('Aguardando aprovação da empresa'), findsOneWidget);
    expect(find.text('Solicitação manual'), findsOneWidget);
    expect(find.textContaining('A empresa precisa aprovar'), findsOneWidget);
    expect(find.text('Aceitar convite da empresa'), findsNothing);
    expect(find.text('Buscar empresa'), findsNothing);
  });

  testWidgets('sair limpa sessao persistida antes de voltar ao login', (
    tester,
  ) async {
    final sessionStore = _MemorySessionStore();

    await _pumpScreen(
      tester,
      gateway: _FakeDriverOnboardingGateway(),
      sessionStore: sessionStore,
    );

    await tester.ensureVisible(find.text('Sair e usar outra conta'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Sair e usar outra conta'));
    await tester.pumpAndSettle();

    expect(sessionStore.cleared, isTrue);
    expect(find.byType(LoginScreen), findsOneWidget);
  });
}

Future<void> _pumpScreen(
  WidgetTester tester, {
  required DriverOnboardingGateway gateway,
  AppSessionStore? sessionStore,
}) async {
  await tester.pumpWidget(
    MaterialApp(
      home: DriverCompanyPendingScreen(
        sessionUser: _driverSession,
        gateway: gateway,
        sessionStore: sessionStore ?? _MemorySessionStore(),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

class _FakeDriverOnboardingGateway implements DriverOnboardingGateway {
  _FakeDriverOnboardingGateway({
    this.status = const DriverOnboardingStatusRecord(
      companyLinkStatus: AppCompanyLinkStatus.noCompany,
    ),
  });

  final DriverOnboardingStatusRecord status;

  @override
  Future<DriverInvitePreviewRecord> previewInvite(String token) async {
    throw UnimplementedError();
  }

  @override
  Future<DriverOnboardingStatusRecord> redeemInvite(
    AppSessionUser session, {
    required String token,
  }) async {
    throw UnimplementedError();
  }

  @override
  Future<DriverOnboardingStatusRecord> fetchStatus(
    AppSessionUser session,
  ) async {
    return status;
  }

  @override
  Future<List<DriverOnboardingCompanyRecord>> searchCompanies(
    AppSessionUser session, {
    String? query,
  }) async {
    return const [
      DriverOnboardingCompanyRecord(
        id: 7,
        nome: 'Feevale Vans',
        slug: 'feevale-vans',
      ),
    ];
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
      companyLinkStatus: AppCompanyLinkStatus.companyLinked,
    );
  }
}

class _MemorySessionStore extends AppSessionStore {
  _MemorySessionStore({this.pendingDriverInviteToken});

  String? pendingDriverInviteToken;
  var cleared = false;

  @override
  Future<String?> readPendingDriverInviteToken() async {
    return pendingDriverInviteToken;
  }

  @override
  Future<void> writePendingDriverInviteToken(String token) async {
    pendingDriverInviteToken = token.trim().isEmpty ? null : token.trim();
  }

  @override
  Future<void> clearPendingDriverInviteToken() async {
    pendingDriverInviteToken = null;
  }

  @override
  Future<void> clear() async {
    cleared = true;
  }
}
