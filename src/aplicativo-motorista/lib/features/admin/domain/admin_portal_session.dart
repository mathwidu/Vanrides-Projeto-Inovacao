class AdminPortalSession {
  const AdminPortalSession({
    required this.adminId,
    required this.adminName,
    required this.companyId,
    required this.companyName,
    required this.email,
    required this.accessToken,
    this.companySlug,
    this.backendRoleLabel = 'Admin',
  });

  final int adminId;
  final String adminName;
  final int companyId;
  final String companyName;
  final String email;
  final String accessToken;
  final String? companySlug;
  final String backendRoleLabel;

  String get companyDisplayName {
    if (companySlug != null && companySlug!.trim().isNotEmpty) {
      return companySlug!.trim();
    }
    if (companyName.trim().isNotEmpty) {
      return companyName.trim();
    }
    return 'Empresa';
  }

  AdminPortalSession copyWith({
    int? adminId,
    String? adminName,
    int? companyId,
    String? companyName,
    String? email,
    String? accessToken,
    String? companySlug,
    String? backendRoleLabel,
  }) {
    return AdminPortalSession(
      adminId: adminId ?? this.adminId,
      adminName: adminName ?? this.adminName,
      companyId: companyId ?? this.companyId,
      companyName: companyName ?? this.companyName,
      email: email ?? this.email,
      accessToken: accessToken ?? this.accessToken,
      companySlug: companySlug ?? this.companySlug,
      backendRoleLabel: backendRoleLabel ?? this.backendRoleLabel,
    );
  }
}
