import 'admin_motorista_record.dart';

class AdminPassageiroLinkedRecord {
  const AdminPassageiroLinkedRecord({
    required this.id,
    required this.nome,
    required this.email,
    required this.menorDeIdade,
    this.motorista,
    this.dataNascimento,
    this.fotoPerfilUrl,
  });

  final int id;
  final String nome;
  final String email;
  final bool menorDeIdade;
  final AdminMotoristaRecord? motorista;
  final String? dataNascimento;
  final String? fotoPerfilUrl;

  factory AdminPassageiroLinkedRecord.fromJson(Map<String, dynamic> json) {
    final motoristaJson = json['motorista'] as Map<String, dynamic>?;
    return AdminPassageiroLinkedRecord(
      id: (json['id'] as num).toInt(),
      nome: json['nome'] as String? ?? '',
      email: json['email'] as String? ?? '',
      menorDeIdade: json['menorDeIdade'] as bool? ?? false,
      motorista:
          motoristaJson == null
              ? null
              : AdminMotoristaRecord.fromJson(motoristaJson),
      dataNascimento: json['dataNascimento'] as String?,
      fotoPerfilUrl: json['fotoPerfilUrl'] as String?,
    );
  }
}
