import 'admin_motorista_record.dart';
import 'admin_operation_days.dart';

class AdminRotaBaseRecord {
  const AdminRotaBaseRecord({
    required this.id,
    required this.nome,
    required this.motorista,
    required this.quantidadeParadas,
    required this.passageiroIds,
    required this.ativo,
    required this.geracaoAutomaticaAtiva,
    required this.diasOperacao,
    this.horarioPartida,
    this.destinoFinalNome,
    this.destinoFinalEndereco,
    this.destinoFinalLatitude,
    this.destinoFinalLongitude,
  });

  final int id;
  final String nome;
  final AdminMotoristaRecord motorista;
  final int quantidadeParadas;
  final List<int> passageiroIds;
  final bool ativo;
  final bool geracaoAutomaticaAtiva;
  final List<String> diasOperacao;
  final String? horarioPartida;
  final String? destinoFinalNome;
  final String? destinoFinalEndereco;
  final double? destinoFinalLatitude;
  final double? destinoFinalLongitude;

  bool get hasDestinoFinal =>
      destinoFinalLatitude != null && destinoFinalLongitude != null;

  factory AdminRotaBaseRecord.fromJson(Map<String, dynamic> json) {
    return AdminRotaBaseRecord(
      id: (json['id'] as num).toInt(),
      nome: json['nome'] as String? ?? '',
      motorista: AdminMotoristaRecord.fromJson(
        json['motorista'] as Map<String, dynamic>? ?? const {},
      ),
      quantidadeParadas: (json['quantidadeParadas'] as num?)?.toInt() ?? 0,
      passageiroIds: (json['passageiroIds'] as List<dynamic>? ?? const [])
          .map((e) => (e as num).toInt())
          .toList(growable: false),
      ativo: json['ativo'] as bool? ?? false,
      geracaoAutomaticaAtiva: json['geracaoAutomaticaAtiva'] as bool? ?? false,
      diasOperacao: decodeAdminOperationDays(
        json['diasOperacao'] as List<dynamic>? ?? const [],
      ),
      horarioPartida: _parseHorario(json['horarioPartida']),
      destinoFinalNome: json['destinoFinalNome'] as String?,
      destinoFinalEndereco: json['destinoFinalEndereco'] as String?,
      destinoFinalLatitude: (json['destinoFinalLatitude'] as num?)?.toDouble(),
      destinoFinalLongitude: (json['destinoFinalLongitude'] as num?)?.toDouble(),
    );
  }
}

String? _parseHorario(dynamic value) {
  final raw = value as String?;
  if (raw == null || raw.isEmpty) return null;
  return raw.length >= 5 ? raw.substring(0, 5) : raw;
}
