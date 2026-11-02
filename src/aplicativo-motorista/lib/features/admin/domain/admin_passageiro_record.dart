import 'admin_motorista_record.dart';
import 'admin_responsavel_record.dart';

class AdminPassageiroRecord {
  const AdminPassageiroRecord({
    required this.id,
    required this.nome,
    required this.email,
    required this.telefone,
    this.endereco = '',
    this.bairro = '',
    this.cidade = '',
    this.estado = '',
    this.cep = '',
    this.latitude,
    this.longitude,
    required this.menorDeIdade,
    this.motoristaId,
    this.motoristaNome,
    this.motorista,
    this.responsavelCount = 0,
    this.responsaveis = const [],
    this.dataNascimento,
    this.fotoPerfilUrl,
  });

  final int id;
  final String nome;
  final String email;
  final String telefone;
  final String endereco;
  final String bairro;
  final String cidade;
  final String estado;
  final String cep;
  final double? latitude;
  final double? longitude;
  final bool menorDeIdade;
  final int? motoristaId;
  final String? motoristaNome;
  final AdminMotoristaRecord? motorista;
  final int responsavelCount;
  final List<AdminResponsavelRecord> responsaveis;
  final String? dataNascimento;
  final String? fotoPerfilUrl;

  String get enderecoCompleto {
    final parts = [endereco, bairro, cidade, estado, cep]
        .map((item) => item.trim())
        .where((item) => item.isNotEmpty)
        .toList(growable: false);
    return parts.isEmpty ? 'Endereco nao informado' : parts.join(', ');
  }

  factory AdminPassageiroRecord.fromJson(Map<String, dynamic> json) {
    final motoristaJson = json['motorista'] as Map<String, dynamic>?;
    final responsaveisJson = json['responsaveis'] as List<dynamic>?;
    final motorista =
        motoristaJson == null
            ? null
            : AdminMotoristaRecord.fromJson(motoristaJson);
    return AdminPassageiroRecord(
      id: (json['id'] as num).toInt(),
      nome: json['nome'] as String? ?? '',
      email: json['email'] as String? ?? '',
      telefone: json['telefone'] as String? ?? '',
      endereco: json['endereco'] as String? ?? '',
      bairro: json['bairro'] as String? ?? '',
      cidade: json['cidade'] as String? ?? '',
      estado: json['estado'] as String? ?? '',
      cep: json['cep'] as String? ?? '',
      latitude: (json['latitude'] as num?)?.toDouble(),
      longitude: (json['longitude'] as num?)?.toDouble(),
      menorDeIdade: json['menorDeIdade'] as bool? ?? false,
      motoristaId:
          motorista?.id ??
          (motoristaJson == null
              ? null
              : (motoristaJson['id'] as num?)?.toInt()),
      motoristaNome:
          motorista?.nome ??
          (motoristaJson == null ? null : motoristaJson['nome'] as String?),
      motorista: motorista,
      responsavelCount:
          (json['responsavelCount'] as num?)?.toInt() ??
          responsaveisJson?.length ??
          0,
      responsaveis:
          responsaveisJson
              ?.map(
                (item) => AdminResponsavelRecord.fromJson(
                  item as Map<String, dynamic>,
                ),
              )
              .toList(growable: false) ??
          const [],
      dataNascimento: json['dataNascimento'] as String?,
      fotoPerfilUrl: json['fotoPerfilUrl'] as String?,
    );
  }

  AdminPassageiroRecord copyWith({
    int? id,
    String? nome,
    String? email,
    String? telefone,
    String? endereco,
    String? bairro,
    String? cidade,
    String? estado,
    String? cep,
    double? latitude,
    double? longitude,
    bool? menorDeIdade,
    int? motoristaId,
    String? motoristaNome,
    AdminMotoristaRecord? motorista,
    int? responsavelCount,
    List<AdminResponsavelRecord>? responsaveis,
    String? dataNascimento,
    String? fotoPerfilUrl,
  }) {
    return AdminPassageiroRecord(
      id: id ?? this.id,
      nome: nome ?? this.nome,
      email: email ?? this.email,
      telefone: telefone ?? this.telefone,
      endereco: endereco ?? this.endereco,
      bairro: bairro ?? this.bairro,
      cidade: cidade ?? this.cidade,
      estado: estado ?? this.estado,
      cep: cep ?? this.cep,
      latitude: latitude ?? this.latitude,
      longitude: longitude ?? this.longitude,
      menorDeIdade: menorDeIdade ?? this.menorDeIdade,
      motoristaId: motoristaId ?? this.motoristaId,
      motoristaNome: motoristaNome ?? this.motoristaNome,
      motorista: motorista ?? this.motorista,
      responsavelCount: responsavelCount ?? this.responsavelCount,
      responsaveis: responsaveis ?? this.responsaveis,
      dataNascimento: dataNascimento ?? this.dataNascimento,
      fotoPerfilUrl: fotoPerfilUrl ?? this.fotoPerfilUrl,
    );
  }
}
