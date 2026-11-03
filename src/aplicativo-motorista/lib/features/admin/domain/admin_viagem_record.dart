import 'admin_motorista_record.dart';

class AdminViagemRecord {
  const AdminViagemRecord({
    required this.id,
    required this.rotaNome,
    required this.dataOperacao,
    required this.status,
    required this.motorista,
    required this.quantidadeParadas,
    this.rotaBaseId,
    this.servicoId,
    this.servicoNome,
    this.tipoOperacao,
    this.horarioPartida,
    this.manifesto = const AdminViagemManifestRecord.notApplicable(),
  });

  final int id;
  final int? rotaBaseId;
  final int? servicoId;
  final String rotaNome;
  final String? servicoNome;
  final String? tipoOperacao;
  final DateTime dataOperacao;
  final String status;
  final AdminMotoristaRecord motorista;
  final int quantidadeParadas;
  final String? horarioPartida;
  final AdminViagemManifestRecord manifesto;

  String get nomeOperacional {
    final serviceName = servicoNome;
    if (serviceName != null && serviceName.trim().isNotEmpty) {
      return serviceName;
    }
    return rotaNome;
  }

  factory AdminViagemRecord.fromJson(Map<String, dynamic> json) {
    return AdminViagemRecord(
      id: (json['id'] as num).toInt(),
      rotaBaseId: (json['rotaBaseId'] as num?)?.toInt(),
      servicoId: (json['servicoId'] as num?)?.toInt(),
      rotaNome: json['rotaNome'] as String? ?? '',
      servicoNome: json['servicoNome'] as String?,
      tipoOperacao: json['tipoOperacao'] as String?,
      dataOperacao:
          DateTime.tryParse(json['dataOperacao'] as String? ?? '') ??
          DateTime.now(),
      status: json['status'] as String? ?? 'PLANEJADA',
      motorista: AdminMotoristaRecord.fromJson(
        json['motorista'] as Map<String, dynamic>? ?? const {},
      ),
      quantidadeParadas: (json['quantidadeParadas'] as num?)?.toInt() ?? 0,
      horarioPartida: _parseHorario(json['horarioPartida']),
      manifesto: AdminViagemManifestRecord.fromJson(json['manifesto']),
    );
  }
}

class AdminViagemManifestRecord {
  const AdminViagemManifestRecord({
    required this.status,
    required this.mudancas,
    required this.adicionados,
    required this.removidos,
    this.mensagem,
  });

  const AdminViagemManifestRecord.notApplicable()
    : status = 'NOT_APPLICABLE',
      mudancas = 0,
      adicionados = 0,
      removidos = 0,
      mensagem = null;

  final String status;
  final int mudancas;
  final int adicionados;
  final int removidos;
  final String? mensagem;

  bool get isPending => _normalized == 'PENDING';

  bool get isBlocked => _normalized == 'BLOCKED';

  bool get isUpToDate => _normalized == 'UP_TO_DATE';

  bool get isVisible => isPending || isBlocked || isUpToDate;

  String get label {
    return switch (_normalized) {
      'PENDING' => 'Lista pendente',
      'BLOCKED' => 'Revisão manual',
      'UP_TO_DATE' => 'Lista atualizada',
      _ => 'Lista',
    };
  }

  String get deltaLabel {
    final parts = <String>[];
    if (adicionados > 0) {
      parts.add(_manifestDeltaAction(adicionados, 'incluir'));
    }
    if (removidos > 0) {
      parts.add(_manifestDeltaAction(removidos, 'remover'));
    }
    if (parts.isEmpty && mudancas > 0) {
      return '$mudancas mudanças';
    }
    return parts.join(' / ');
  }

  String get _normalized => status.trim().toUpperCase();

  factory AdminViagemManifestRecord.fromJson(dynamic value) {
    if (value is! Map<String, dynamic>) {
      return const AdminViagemManifestRecord.notApplicable();
    }
    return AdminViagemManifestRecord(
      status: value['status'] as String? ?? 'NOT_APPLICABLE',
      mudancas: (value['mudancas'] as num?)?.toInt() ?? 0,
      adicionados: (value['adicionados'] as num?)?.toInt() ?? 0,
      removidos: (value['removidos'] as num?)?.toInt() ?? 0,
      mensagem: value['mensagem'] as String?,
    );
  }
}

String _manifestDeltaAction(int count, String action) {
  if (count == 1) {
    return '1 para $action';
  }
  return '$count para $action';
}

String? _parseHorario(dynamic value) {
  final raw = value as String?;
  if (raw == null || raw.isEmpty) return null;
  return raw.length >= 5 ? raw.substring(0, 5) : raw;
}
