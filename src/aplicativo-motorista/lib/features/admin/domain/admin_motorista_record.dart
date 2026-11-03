class AdminMotoristaRecord {
  const AdminMotoristaRecord({
    required this.id,
    required this.nome,
    required this.email,
    required this.telefone,
    this.dataNascimento,
    this.fotoPerfilUrl,
  });

  final int id;
  final String nome;
  final String email;
  final String telefone;
  final String? dataNascimento;
  final String? fotoPerfilUrl;

  factory AdminMotoristaRecord.fromJson(Map<String, dynamic> json) {
    return AdminMotoristaRecord(
      id: (json['id'] as num).toInt(),
      nome: json['nome'] as String? ?? '',
      email: json['email'] as String? ?? '',
      telefone: json['telefone'] as String? ?? '',
      dataNascimento: json['dataNascimento'] as String?,
      fotoPerfilUrl: json['fotoPerfilUrl'] as String?,
    );
  }
}
