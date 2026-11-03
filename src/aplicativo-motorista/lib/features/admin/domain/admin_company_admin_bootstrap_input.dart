class AdminCompanyAdminBootstrapInput {
  const AdminCompanyAdminBootstrapInput({
    required this.companyName,
    required this.nome,
    required this.email,
    required this.telefone,
    required this.senha,
  });

  final String companyName;
  final String nome;
  final String email;
  final String telefone;
  final String senha;

  Map<String, dynamic> toJson() {
    return {
      'companyName': companyName.trim(),
      'nome': nome,
      'email': email,
      'telefone': telefone,
      'senha': senha,
    };
  }
}
