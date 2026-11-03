class PassengerAvailableServiceRecord {
  const PassengerAvailableServiceRecord({
    required this.servicoId,
    required this.nome,
    required this.tipoOperacao,
    required this.dataOperacao,
    required this.politicaPresenca,
    required this.presencaStatus,
    this.horarioPartida,
    this.destinoFinalNome,
    this.destinoFinalEndereco,
    this.motoristaPadrao,
    this.presencaId,
  });

  final int servicoId;
  final String nome;
  final String tipoOperacao;
  final DateTime dataOperacao;
  final String? horarioPartida;
  final String? destinoFinalNome;
  final String? destinoFinalEndereco;
  final String politicaPresenca;
  final ServicePresenceMotoristaRecord? motoristaPadrao;
  final int? presencaId;
  final String presencaStatus;

  bool get hasDecision => presencaId != null && presencaStatus != 'UNKNOWN';
  bool get isConfirmed => presencaStatus == 'CONFIRMED';
  bool get isAbsent => presencaStatus == 'ABSENT';
  bool get requiresPresenceDecision =>
      politicaPresenca.trim().toUpperCase() == 'EXIGE_CONFIRMACAO';

  String get tipoOperacaoLabel => switch (tipoOperacao.toUpperCase()) {
    'VOLTA' => 'Volta',
    'CIRCULAR' => 'Circular',
    'FRETADO' => 'Fretado',
    _ => 'Ida',
  };

  String get statusLabel => switch (presencaStatus.toUpperCase()) {
    'CONFIRMED' => 'Vou',
    'ABSENT' => 'Não vou',
    'CANCELED_BY_COMPANY' => 'Cancelado pela empresa',
    _ => 'Sem decisão',
  };

  factory PassengerAvailableServiceRecord.fromJson(Map<String, dynamic> json) {
    final motorista = json['motoristaPadrao'];
    return PassengerAvailableServiceRecord(
      servicoId: (json['servicoId'] as num?)?.toInt() ?? 0,
      nome: (json['nome'] as String?)?.trim() ?? '',
      tipoOperacao: (json['tipoOperacao'] as String?)?.trim() ?? 'IDA',
      dataOperacao: _parseDate(json['dataOperacao']),
      horarioPartida: _parseHorario(json['horarioPartida']),
      destinoFinalNome: (json['destinoFinalNome'] as String?)?.trim(),
      destinoFinalEndereco: (json['destinoFinalEndereco'] as String?)?.trim(),
      politicaPresenca:
          (json['politicaPresenca'] as String?)?.trim() ?? 'INCLUIR_POR_PADRAO',
      motoristaPadrao:
          motorista is Map<String, dynamic>
              ? ServicePresenceMotoristaRecord.fromJson(motorista)
              : null,
      presencaId: (json['presencaId'] as num?)?.toInt(),
      presencaStatus:
          (json['presencaStatus'] as String?)?.trim().toUpperCase() ??
          'UNKNOWN',
    );
  }
}

class ServicePresenceMotoristaRecord {
  const ServicePresenceMotoristaRecord({
    required this.id,
    required this.nome,
    required this.email,
    required this.telefone,
  });

  final int id;
  final String nome;
  final String email;
  final String telefone;

  factory ServicePresenceMotoristaRecord.fromJson(Map<String, dynamic> json) {
    return ServicePresenceMotoristaRecord(
      id: (json['id'] as num?)?.toInt() ?? 0,
      nome: (json['nome'] as String?)?.trim() ?? 'Motorista',
      email: (json['email'] as String?)?.trim() ?? '',
      telefone: (json['telefone'] as String?)?.trim() ?? '',
    );
  }
}

DateTime _parseDate(dynamic value) {
  final raw = (value as String?)?.trim() ?? '';
  final parts = raw.split('-');
  if (parts.length == 3) {
    return DateTime(
      int.tryParse(parts[0]) ?? DateTime.now().year,
      int.tryParse(parts[1]) ?? DateTime.now().month,
      int.tryParse(parts[2]) ?? DateTime.now().day,
    );
  }
  final now = DateTime.now();
  return DateTime(now.year, now.month, now.day);
}

String? _parseHorario(dynamic value) {
  final raw = (value as String?)?.trim();
  if (raw == null || raw.isEmpty) return null;
  return raw.length >= 5 ? raw.substring(0, 5) : raw;
}
