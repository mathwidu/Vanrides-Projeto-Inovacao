import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:vanrides_frontend_dev/app/session/app_company_link_status.dart';
import 'package:vanrides_frontend_dev/app/session/app_session_user.dart';
import 'package:vanrides_frontend_dev/app/session/app_user_role.dart';
import 'package:vanrides_frontend_dev/app/theme/vanrides_theme.dart';
import 'package:vanrides_frontend_dev/features/driver_onboarding/data/driver_onboarding_service.dart';
import 'package:vanrides_frontend_dev/features/driver_onboarding/domain/driver_onboarding_models.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/presentation/driver_company_pending_screen.dart';
import 'package:vanrides_frontend_dev/features/guardian/data/guardian_localizacao_service.dart';
import 'package:vanrides_frontend_dev/features/guardian/data/guardian_passageiro_service.dart';
import 'package:vanrides_frontend_dev/features/guardian/data/guardian_presenca_service.dart';
import 'package:vanrides_frontend_dev/features/guardian/domain/guardian_linked_passageiro_record.dart';
import 'package:vanrides_frontend_dev/features/guardian/domain/guardian_localizacao_snapshot.dart';
import 'package:vanrides_frontend_dev/features/guardian/domain/guardian_presenca_record.dart';
import 'package:vanrides_frontend_dev/features/guardian/domain/guardian_session.dart';
import 'package:vanrides_frontend_dev/features/guardian/presentation/guardian_shell_screen.dart';
import 'package:vanrides_frontend_dev/l10n/app_strings.dart';

const _driverSession = AppSessionUser(
  userId: 301,
  displayName: 'Maria Motorista',
  email: 'maria.motorista@vanrides.dev',
  password: 'Senha123',
  role: AppUserRole.driver,
  backendRoleLabel: 'MOTORISTA',
  accessToken: 'driver.token.test',
  companyLinkStatus: AppCompanyLinkStatus.noCompany,
);

const _guardianSession = GuardianSession(
  guardianId: 501,
  guardianName: 'Marina Responsavel',
  companyId: 12,
  email: 'marina.responsavel@vanrides.dev',
  accessToken: 'guardian.token.test',
  backendRoleLabel: 'RESPONSAVEL',
);

void main() {
  testWidgets(
    'visual smoke dos estados vazios de motorista e responsavel',
    (tester) async {
      await _captureDriverWithoutCompany(tester);
      await _captureGuardianWithoutPassengers(tester);
    },
    timeout: const Timeout(Duration(minutes: 4)),
  );
}

Future<void> _captureDriverWithoutCompany(WidgetTester tester) async {
  await _pumpMobile(
    tester,
    const DriverCompanyPendingScreen(
      sessionUser: _driverSession,
      gateway: _VisualDriverOnboardingGateway(),
    ),
  );

  expect(find.text('Motorista sem empresa'), findsOneWidget);
  expect(find.text('Buscar empresa'), findsOneWidget);
  expect(find.text('VanRides Escolar'), findsOneWidget);

  await _visualReady(tester, 'driver-onboarding-pending');
}

Future<void> _captureGuardianWithoutPassengers(WidgetTester tester) async {
  await _pumpMobile(
    tester,
    const GuardianShellScreen(
      session: _guardianSession,
      homeGateway: _EmptyGuardianPassageiroGateway(),
      presencaGateway: _EmptyGuardianPresencaGateway(),
      localizacaoGateway: _EmptyGuardianLocalizacaoGateway(),
    ),
  );

  expect(
    find.text(AppStrings.guardian.nenhumPassageiroVinculado),
    findsOneWidget,
  );
  expect(find.text(AppStrings.guardian.atualizar), findsWidgets);

  await _visualReady(tester, 'guardian-empty');
}

Future<void> _pumpMobile(WidgetTester tester, Widget child) async {
  await tester.binding.setSurfaceSize(const Size(390, 844));
  addTearDown(() => tester.binding.setSurfaceSize(null));
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
  debugPrintSynchronously('VANRIDES_ROLE_EMPTY_VISUAL_READY:$state');
  await tester.pump(const Duration(seconds: 2));
}

class _VisualDriverOnboardingGateway implements DriverOnboardingGateway {
  const _VisualDriverOnboardingGateway();

  @override
  Future<DriverOnboardingStatusRecord> createRequest(
    AppSessionUser session, {
    required int companyId,
  }) async {
    return const DriverOnboardingStatusRecord(
      companyLinkStatus: AppCompanyLinkStatus.pendingApproval,
      companyId: 12,
      companyName: 'VanRides Escolar',
      pendingRequest: PendingDriverCompanyLinkRequestRecord(
        id: 71,
        companyId: 12,
        companyName: 'VanRides Escolar',
        status: 'PENDING_APPROVAL',
      ),
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
  Future<DriverInvitePreviewRecord> previewInvite(String token) async {
    return const DriverInvitePreviewRecord(
      companyId: 12,
      companyName: 'VanRides Escolar',
      role: 'DRIVER',
      status: 'ACTIVE',
    );
  }

  @override
  Future<DriverOnboardingStatusRecord> redeemInvite(
    AppSessionUser session, {
    required String token,
  }) async {
    return const DriverOnboardingStatusRecord(
      companyLinkStatus: AppCompanyLinkStatus.companyLinked,
      companyId: 12,
      companyName: 'VanRides Escolar',
      activeMembershipId: 88,
    );
  }

  @override
  Future<List<DriverOnboardingCompanyRecord>> searchCompanies(
    AppSessionUser session, {
    String? query,
  }) async {
    return const [
      DriverOnboardingCompanyRecord(
        id: 12,
        nome: 'VanRides Escolar',
        slug: 'vanrides-escolar',
      ),
      DriverOnboardingCompanyRecord(
        id: 13,
        nome: 'Transporte Feevale',
        slug: 'feevale-operacao',
      ),
    ];
  }

  @override
  Future<AppSessionUser> upgradeSession(
    AppSessionUser session, {
    String? companyName,
  }) async {
    return session.copyWith(
      companyId: 12,
      companyLinkStatus: AppCompanyLinkStatus.companyLinked,
    );
  }
}

class _EmptyGuardianPassageiroGateway implements GuardianPassageiroGateway {
  const _EmptyGuardianPassageiroGateway();

  @override
  Future<List<GuardianLinkedPassageiroRecord>> fetchLinkedPassageiros() async {
    return const [];
  }
}

class _EmptyGuardianPresencaGateway implements GuardianPresencaGateway {
  const _EmptyGuardianPresencaGateway();

  @override
  Future<void> confirmarPresenca({
    required int passageiroId,
    required DateTime dataPresenca,
  }) async {}

  @override
  Future<List<GuardianPresencaRecord>> fetchPresencasByPassageiro(
    int passageiroId,
  ) async {
    return const [];
  }

  @override
  Future<void> removerPresenca(int presencaId) async {}
}

class _EmptyGuardianLocalizacaoGateway implements GuardianLocalizacaoGateway {
  const _EmptyGuardianLocalizacaoGateway();

  @override
  Future<List<GuardianLocalizacaoSnapshot>> fetchHistoricoPorMotorista(
    int motoristaId,
  ) async {
    return const [];
  }
}
