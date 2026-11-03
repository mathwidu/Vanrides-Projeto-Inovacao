import '../session/app_user_role.dart';

enum AppLaunchExperience {
  operationalLogin,
  experienceSelection,
}

enum AppRoleDestination {
  driverHome,
  passengerHome,
  guardianHome,
  adminPortal,
  unsupported,
}

class AppExperienceResolver {
  const AppExperienceResolver._();

  static AppLaunchExperience initialExperience({required bool isWeb}) {
    return isWeb
        ? AppLaunchExperience.experienceSelection
        : AppLaunchExperience.operationalLogin;
  }

  static AppRoleDestination destinationForRole(AppUserRole role) {
    switch (role) {
      case AppUserRole.driver:
        return AppRoleDestination.driverHome;
      case AppUserRole.passenger:
        return AppRoleDestination.passengerHome;
      case AppUserRole.guardian:
        return AppRoleDestination.guardianHome;
      case AppUserRole.companyAdmin:
        return AppRoleDestination.adminPortal;
      case AppUserRole.unknown:
        return AppRoleDestination.unsupported;
    }
  }
}
