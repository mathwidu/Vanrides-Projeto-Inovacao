import 'admin_passageiro_linked_record.dart';

class AdminResponsavelRecord {
  const AdminResponsavelRecord({
    required this.id,
    required this.nome,
    required this.email,
    required this.telefone,
    this.dataNascimento,
    this.fotoPerfilUrl,
    this.passageiroCount = 0,
    this.acessoConfigurado = false,
    this.passageiros = const [],
  });

  final int id;
  final String nome;
  final String email;
  final String telefone;
  final String? dataNascimento;
  final String? fotoPerfilUrl;
  final int passageiroCount;
  final bool acessoConfigurado;
  final List<AdminPassageiroLinkedRecord> passageiros;

  factory AdminResponsavelRecord.fromJson(Map<String, dynamic> json) {
    final passageirosJson = json['passageiros'] as List<dynamic>?;
    return AdminResponsavelRecord(
      id: (json['id'] as num).toInt(),
      nome: json['nome'] as String? ?? '',
      email: json['email'] as String? ?? '',
      telefone: json['telefone'] as String? ?? '',
      dataNascimento: json['dataNascimento'] as String?,
      fotoPerfilUrl: json['fotoPerfilUrl'] as String?,
      passageiroCount:
          (json['passageiroCount'] as num?)?.toInt() ??
          passageirosJson?.length ??
          0,
      acessoConfigurado: json['acessoConfigurado'] as bool? ?? false,
      passageiros:
          passageirosJson
              ?.map(
                (item) => AdminPassageiroLinkedRecord.fromJson(
                  item as Map<String, dynamic>,
                ),
              )
              .toList(growable: false) ??
          const [],
    );
  }

  AdminResponsavelRecord copyWith({
    int? id,
    String? nome,
    String? email,
    String? telefone,
    String? dataNascimento,
    String? fotoPerfilUrl,
    int? passageiroCount,
    bool? acessoConfigurado,
    List<AdminPassageiroLinkedRecord>? passageiros,
  }) {
    return AdminResponsavelRecord(
      id: id ?? this.id,
      nome: nome ?? this.nome,
      email: email ?? this.email,
      telefone: telefone ?? this.telefone,
      dataNascimento: dataNascimento ?? this.dataNascimento,
      fotoPerfilUrl: fotoPerfilUrl ?? this.fotoPerfilUrl,
      passageiroCount: passageiroCount ?? this.passageiroCount,
      acessoConfigurado: acessoConfigurado ?? this.acessoConfigurado,
      passageiros: passageiros ?? this.passageiros,
    );
  }
}
