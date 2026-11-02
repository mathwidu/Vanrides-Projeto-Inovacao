import 'admin_viagem_record.dart';

class AdminViagemBulkCreateResult {
  const AdminViagemBulkCreateResult({
    required this.dataOperacao,
    required this.criadas,
    required this.existentes,
    required this.ignoradas,
    required this.viagens,
    this.atualizadas = 0,
    this.bloqueadas = 0,
    this.servicosIgnorados = const [],
    this.servicosBloqueados = const [],
  });

  final DateTime dataOperacao;
  final int criadas;
  final int existentes;
  final int atualizadas;
  final int bloqueadas;
  final int ignoradas;
  final List<AdminViagemRecord> viagens;
  final List<AdminViagemIgnoradaRecord> servicosIgnorados;
  final List<AdminViagemIgnoradaRecord> servicosBloqueados;

  int get totalConsideradas =>
      criadas + atualizadas + existentes + bloqueadas + ignoradas;

  factory AdminViagemBulkCreateResult.fromJson(Map<String, dynamic> json) {
    return AdminViagemBulkCreateResult(
      dataOperacao:
          DateTime.tryParse(json['dataOperacao'] as String? ?? '') ??
          DateTime.now(),
      criadas: (json['criadas'] as num?)?.toInt() ?? 0,
      existentes: (json['existentes'] as num?)?.toInt() ?? 0,
      atualizadas: (json['atualizadas'] as num?)?.toInt() ?? 0,
      bloqueadas: (json['bloqueadas'] as num?)?.toInt() ?? 0,
      ignoradas: (json['ignoradas'] as num?)?.toInt() ?? 0,
      viagens: (json['viagens'] as List<dynamic>? ?? const [])
          .map(
            (item) => AdminViagemRecord.fromJson(item as Map<String, dynamic>),
          )
          .toList(growable: false),
      servicosIgnorados: (json['servicosIgnorados'] as List<dynamic>? ??
              const [])
          .map(
            (item) => AdminViagemIgnoradaRecord.fromJson(
              item as Map<String, dynamic>,
            ),
          )
          .toList(growable: false),
      servicosBloqueados: (json['servicosBloqueados'] as List<dynamic>? ??
              const [])
          .map(
            (item) => AdminViagemIgnoradaRecord.fromJson(
              item as Map<String, dynamic>,
            ),
          )
          .toList(growable: false),
    );
  }
}

class AdminViagemIgnoradaRecord {
  const AdminViagemIgnoradaRecord({
    required this.servicoId,
    required this.servicoNome,
    required this.motivo,
    required this.mensagem,
  });

  final int? servicoId;
  final String? servicoNome;
  final String motivo;
  final String mensagem;

  String get nomeOperacional =>
      servicoNome?.trim().isNotEmpty == true
          ? servicoNome!.trim()
          : 'Serviço recorrente';

  String get motivoLabel {
    return switch (motivo) {
      'FORA_DA_DATA' => 'fora da data',
      'SEM_MOTORISTA' => 'sem motorista',
      'SEM_PASSAGEIROS_OPERACIONAIS' => 'sem passageiros operacionais',
      'CAPACIDADE_EXCEDIDA' => 'capacidade excedida',
      'VIAGEM_EM_ANDAMENTO' => 'viagem em andamento',
      'VIAGEM_CONCLUIDA' => 'viagem concluída',
      'VIAGEM_CANCELADA' => 'viagem cancelada',
      'MANIFESTO_INVALIDO' => 'lista pendente',
      _ => mensagem.trim().isNotEmpty ? mensagem.trim() : 'revisão necessária',
    };
  }

  factory AdminViagemIgnoradaRecord.fromJson(Map<String, dynamic> json) {
    return AdminViagemIgnoradaRecord(
      servicoId: (json['servicoId'] as num?)?.toInt(),
      servicoNome: json['servicoNome'] as String?,
      motivo: json['motivo'] as String? ?? 'DESCONHECIDO',
      mensagem: json['mensagem'] as String? ?? '',
    );
  }
}
