class AdminResponsavelCreateInput {
  const AdminResponsavelCreateInput({
    required this.nome,
    required this.email,
    required this.telefone,
    this.dataNascimento,
    this.senha,
  });

  final String nome;
  final String email;
  final String telefone;
  final String? dataNascimento;
  final String? senha;

  Map<String, dynamic> toJson() {
    final birthDateValue = dataNascimento?.trim();
    return <String, dynamic>{
      'nome': nome,
      'email': email,
      'telefone': telefone,
      if (birthDateValue != null && birthDateValue.isNotEmpty)
        'dataNascimento': birthDateValue,
      if (senha != null && senha!.trim().isNotEmpty) 'senha': senha,
    };
  }
}
