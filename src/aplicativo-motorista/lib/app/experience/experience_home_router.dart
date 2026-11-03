import '../session/app_company_link_status.dart';
import 'package:flutter/material.dart';

import '../../features/account/data/account_profile_service.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/presentation/home_motorista_screen.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/home_passageiro_screen.dart';
import '../../features/admin/presentation/admin_shell_screen.dart';
import '../../features/admin/domain/admin_portal_session.dart';
import '../../features/driver_trip/presentation/driver_company_pending_screen.dart';
import '../../features/guardian/domain/guardian_session.dart';
import '../../features/guardian/presentation/guardian_shell_screen.dart';
import '../../features/passenger_onboarding/domain/passenger_onboarding_models.dart';
import '../../features/passenger_onboarding/presentation/passenger_company_link_flow.dart';
import '../../features/shared/presentation/unsupported_role_screen.dart';
import '../session/app_session_user.dart';
import 'app_experience_resolver.dart';

class ExperienceHomeRouter extends StatelessWidget {
  const ExperienceHomeRouter({super.key, required this.sessionUser});

  final AppSessionUser sessionUser;

  @override
  Widget build(BuildContext context) {
    switch (AppExperienceResolver.destinationForRole(sessionUser.role)) {
      case AppRoleDestination.driverHome:
        if (sessionUser.companyLinkStatus !=
                AppCompanyLinkStatus.companyLinked ||
            sessionUser.companyId == null) {
          return DriverCompanyPendingScreen(sessionUser: sessionUser);
        }
        return HomeMotoristaScreen(
          nome: sessionUser.displayName,
          motoristaId: sessionUser.userId,
          email: sessionUser.email,
          senha: sessionUser.password,
          accessToken: sessionUser.accessToken,
        );
      case AppRoleDestination.passengerHome:
        if (sessionUser.companyLinkStatus == AppCompanyLinkStatus.noCompany) {
          return PassengerCompanyGateScreen(sessionUser: sessionUser);
        }
        if (sessionUser.companyLinkStatus ==
            AppCompanyLinkStatus.pendingApproval) {
          return PassengerCompanyPendingScreen(
            sessionUser: sessionUser,
            status: const PassengerOnboardingStatusRecord(
              companyLinkStatus: AppCompanyLinkStatus.pendingApproval,
            ),
          );
        }
        return HomePassageiroScreen(
          nome: sessionUser.displayName,
          idPassageiro: sessionUser.userId,
          motoristaId: sessionUser.motoristaId,
          email: sessionUser.email,
          senha: sessionUser.password,
          accessToken: sessionUser.accessToken,
          accountProfileGateway: AccountProfileService(
            email: sessionUser.email,
            senha: sessionUser.password,
            accessToken: sessionUser.accessToken,
          ),
        );
      case AppRoleDestination.guardianHome:
        if (sessionUser.accessToken == null || sessionUser.companyId == null) {
          return const UnsupportedRoleScreen(
            roleLabel: 'Responsável familiar sem vínculo ativo',
          );
        }
        return GuardianShellScreen(
          session: GuardianSession(
            guardianId: sessionUser.userId,
            guardianName: sessionUser.displayName,
            companyId: sessionUser.companyId!,
            email: sessionUser.email,
            accessToken: sessionUser.accessToken!,
            backendRoleLabel: sessionUser.backendRoleLabel,
          ),
          accountProfileGateway: AccountProfileService(
            email: sessionUser.email,
            senha: sessionUser.password,
            accessToken: sessionUser.accessToken,
          ),
        );
      case AppRoleDestination.adminPortal:
        if (sessionUser.accessToken == null || sessionUser.companyId == null) {
          return const UnsupportedRoleScreen(
            roleLabel: 'Administrador sem empresa vinculada',
          );
        }
        return AdminShellScreen(
          session: AdminPortalSession(
            adminId: sessionUser.userId,
            adminName: sessionUser.displayName,
            companyId: sessionUser.companyId!,
            companyName: '',
            email: sessionUser.email,
            accessToken: sessionUser.accessToken!,
            backendRoleLabel: sessionUser.backendRoleLabel,
          ),
          accountProfileGateway: AccountProfileService(
            email: sessionUser.email,
            senha: sessionUser.password,
            accessToken: sessionUser.accessToken,
          ),
        );
      case AppRoleDestination.unsupported:
        return UnsupportedRoleScreen(roleLabel: sessionUser.backendRoleLabel);
    }
  }
}
