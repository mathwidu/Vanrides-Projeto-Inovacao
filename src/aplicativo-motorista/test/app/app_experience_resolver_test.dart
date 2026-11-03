import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/app/experience/app_experience_resolver.dart';
import 'package:vanrides_frontend_dev/app/session/app_user_role.dart';

void main() {
  group('AppExperienceResolver', () {
    test('abre selecao de experiencia no web', () {
      expect(
        AppExperienceResolver.initialExperience(isWeb: true),
        AppLaunchExperience.experienceSelection,
      );
    });

    test('abre login operacional no mobile', () {
      expect(
        AppExperienceResolver.initialExperience(isWeb: false),
        AppLaunchExperience.operationalLogin,
      );
    });

    test('mapeia papel guardian vindo do backend', () {
      expect(
        AppUserRole.fromBackendLabel('Responsável'),
        AppUserRole.guardian,
      );
    });

    test('mapeia company admin para portal', () {
      expect(
        AppExperienceResolver.destinationForRole(AppUserRole.companyAdmin),
        AppRoleDestination.adminPortal,
      );
    });
  });
}
