enum AppUserRole {
  driver,
  passenger,
  guardian,
  companyAdmin,
  unknown;

  static AppUserRole fromBackendLabel(String? value) {
    final normalized = value?.trim().toLowerCase();
    switch (normalized) {
      case 'motorista':
      case 'driver':
        return AppUserRole.driver;
      case 'passageiro':
      case 'passenger':
        return AppUserRole.passenger;
      case 'responsavel':
      case 'responsável':
      case 'guardian':
        return AppUserRole.guardian;
      case 'company_admin':
      case 'companyadmin':
      case 'admin':
      case 'empresa':
        return AppUserRole.companyAdmin;
      default:
        return AppUserRole.unknown;
    }
  }

  String get label {
    switch (this) {
      case AppUserRole.driver:
        return 'Motorista';
      case AppUserRole.passenger:
        return 'Passageiro';
      case AppUserRole.guardian:
        return 'Responsavel';
      case AppUserRole.companyAdmin:
        return 'Administrador da empresa';
      case AppUserRole.unknown:
        return 'Perfil desconhecido';
    }
  }
}
