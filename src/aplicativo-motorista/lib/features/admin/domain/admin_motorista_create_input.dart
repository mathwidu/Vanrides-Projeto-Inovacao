class AdminMotoristaCreateInput {
  const AdminMotoristaCreateInput({
    required this.nome,
    required this.email,
    required this.telefone,
    required this.dataNascimento,
    this.senha,
  });

  final String nome;
  final String email;
  final String telefone;
  final String dataNascimento;
  final String? senha;

  Map<String, dynamic> toJson() {
    final payload = <String, dynamic>{
      'nome': nome,
      'email': email,
      'telefone': telefone,
      'dataNascimento': dataNascimento,
    };
    final senhaValue = senha?.trim();
    if (senhaValue != null && senhaValue.isNotEmpty) {
      payload['senha'] = senhaValue;
    }
    return payload;
  }
}
