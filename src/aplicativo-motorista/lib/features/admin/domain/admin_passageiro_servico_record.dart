import 'admin_motorista_record.dart';

class AdminPassageiroServicoRecord {
  const AdminPassageiroServicoRecord({
    required this.servicoId,
    required this.nome,
    required this.tipoOperacao,
    required this.habilitado,
    this.horarioPartida,
    this.motoristaPadrao,
    this.elegibilidadeId,
    this.ordemPadrao,
    this.dataInicio,
    this.dataFim,
  });

  final int servicoId;
  final String nome;
  final String tipoOperacao;
  final String? horarioPartida;
  final AdminMotoristaRecord? motoristaPadrao;
  final bool habilitado;
  final int? elegibilidadeId;
  final int? ordemPadrao;
  final String? dataInicio;
  final String? dataFim;

  factory AdminPassageiroServicoRecord.fromJson(Map<String, dynamic> json) {
    final motoristaJson = json['motoristaPadrao'];
    return AdminPassageiroServicoRecord(
      servicoId: (json['servicoId'] as num).toInt(),
      nome: json['nome'] as String? ?? '',
      tipoOperacao: json['tipoOperacao'] as String? ?? 'IDA',
      horarioPartida: _parseHorario(json['horarioPartida']),
      motoristaPadrao:
          motoristaJson is Map<String, dynamic>
              ? AdminMotoristaRecord.fromJson(motoristaJson)
              : null,
      habilitado: json['habilitado'] as bool? ?? false,
      elegibilidadeId: (json['elegibilidadeId'] as num?)?.toInt(),
      ordemPadrao: (json['ordemPadrao'] as num?)?.toInt(),
      dataInicio: json['dataInicio'] as String?,
      dataFim: json['dataFim'] as String?,
    );
  }
}

String? _parseHorario(dynamic value) {
  final raw = value as String?;
  if (raw == null || raw.isEmpty) return null;
  return raw.length >= 5 ? raw.substring(0, 5) : raw;
}
