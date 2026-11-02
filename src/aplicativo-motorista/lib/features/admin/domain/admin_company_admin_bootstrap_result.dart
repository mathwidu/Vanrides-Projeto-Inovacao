class AdminCompanyAdminBootstrapResult {
  const AdminCompanyAdminBootstrapResult({
    required this.id,
    required this.companyId,
    required this.companySlug,
    this.companyName,
    required this.nome,
    required this.email,
    required this.telefone,
  });

  final int id;
  final int companyId;
  final String companySlug;
  final String? companyName;
  final String nome;
  final String email;
  final String telefone;

  String get companyDisplayName =>
      companyName?.trim().isNotEmpty == true
          ? companyName!.trim()
          : companySlug;

  factory AdminCompanyAdminBootstrapResult.fromJson(Map<String, dynamic> json) {
    final rawCompanySlug = json['companySlug'];
    if (rawCompanySlug is! String || rawCompanySlug.trim().isEmpty) {
      throw const FormatException(
        'Campo obrigatório "companySlug" ausente ou inválido no bootstrap.',
      );
    }

    return AdminCompanyAdminBootstrapResult(
      id: (json['id'] as num).toInt(),
      companyId: (json['companyId'] as num).toInt(),
      companySlug: rawCompanySlug.trim(),
      companyName: json['companyName'] as String?,
      nome: json['nome'] as String? ?? '',
      email: json['email'] as String? ?? '',
      telefone: json['telefone'] as String? ?? '',
    );
  }
}
