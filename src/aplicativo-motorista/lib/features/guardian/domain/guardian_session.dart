class GuardianSession {
  const GuardianSession({
    required this.guardianId,
    required this.guardianName,
    required this.companyId,
    required this.email,
    required this.accessToken,
    this.backendRoleLabel = 'Responsavel',
  });

  final int guardianId;
  final String guardianName;
  final int companyId;
  final String email;
  final String accessToken;
  final String backendRoleLabel;

  GuardianSession copyWith({
    int? guardianId,
    String? guardianName,
    int? companyId,
    String? email,
    String? accessToken,
    String? backendRoleLabel,
  }) {
    return GuardianSession(
      guardianId: guardianId ?? this.guardianId,
      guardianName: guardianName ?? this.guardianName,
      companyId: companyId ?? this.companyId,
      email: email ?? this.email,
      accessToken: accessToken ?? this.accessToken,
      backendRoleLabel: backendRoleLabel ?? this.backendRoleLabel,
    );
  }
}
