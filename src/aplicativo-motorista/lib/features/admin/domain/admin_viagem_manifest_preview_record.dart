class AdminViagemManifestPreviewRecord {
  const AdminViagemManifestPreviewRecord({
    required this.viagemId,
    required this.servicoId,
    required this.servicoNome,
    required this.dataOperacao,
    required this.paradasAtuais,
    required this.paradasAposSincronizacao,
    required this.adicionados,
    required this.removidos,
    required this.mantidos,
    required this.mudancas,
    required this.semMudancas,
    required this.passageirosAdicionados,
    required this.passageirosRemovidos,
    required this.passageirosMantidos,
  });

  final int viagemId;
  final int servicoId;
  final String servicoNome;
  final DateTime dataOperacao;
  final int paradasAtuais;
  final int paradasAposSincronizacao;
  final int adicionados;
  final int removidos;
  final int mantidos;
  final int mudancas;
  final bool semMudancas;
  final List<AdminViagemManifestPassengerRecord> passageirosAdicionados;
  final List<AdminViagemManifestPassengerRecord> passageirosRemovidos;
  final List<AdminViagemManifestPassengerRecord> passageirosMantidos;

  factory AdminViagemManifestPreviewRecord.fromJson(Map<String, dynamic> json) {
    final passageirosAdicionados = _parsePassengers(
      json['passageirosAdicionados'],
    );
    final passageirosRemovidos = _parsePassengers(json['passageirosRemovidos']);
    final passageirosMantidos = _parsePassengers(json['passageirosMantidos']);
    final adicionados =
        (json['adicionados'] as num?)?.toInt() ?? passageirosAdicionados.length;
    final removidos =
        (json['removidos'] as num?)?.toInt() ?? passageirosRemovidos.length;

    return AdminViagemManifestPreviewRecord(
      viagemId: (json['viagemId'] as num).toInt(),
      servicoId: (json['servicoId'] as num).toInt(),
      servicoNome: json['servicoNome'] as String? ?? '',
      dataOperacao:
          DateTime.tryParse(json['dataOperacao'] as String? ?? '') ??
          DateTime.now(),
      paradasAtuais: (json['paradasAtuais'] as num?)?.toInt() ?? 0,
      paradasAposSincronizacao:
          (json['paradasAposSincronizacao'] as num?)?.toInt() ?? 0,
      adicionados: adicionados,
      removidos: removidos,
      mantidos:
          (json['mantidos'] as num?)?.toInt() ?? passageirosMantidos.length,
      mudancas: (json['mudancas'] as num?)?.toInt() ?? adicionados + removidos,
      semMudancas:
          json['semMudancas'] as bool? ?? (adicionados + removidos == 0),
      passageirosAdicionados: passageirosAdicionados,
      passageirosRemovidos: passageirosRemovidos,
      passageirosMantidos: passageirosMantidos,
    );
  }
}

class AdminViagemManifestPassengerRecord {
  const AdminViagemManifestPassengerRecord({
    required this.passageiroId,
    required this.nome,
    required this.menorDeIdade,
    this.ordemAtual,
    this.ordemNova,
  });

  final int passageiroId;
  final String nome;
  final bool menorDeIdade;
  final int? ordemAtual;
  final int? ordemNova;

  factory AdminViagemManifestPassengerRecord.fromJson(
    Map<String, dynamic> json,
  ) {
    return AdminViagemManifestPassengerRecord(
      passageiroId: (json['passageiroId'] as num).toInt(),
      nome: json['nome'] as String? ?? '',
      menorDeIdade: json['menorDeIdade'] as bool? ?? false,
      ordemAtual: (json['ordemAtual'] as num?)?.toInt(),
      ordemNova: (json['ordemNova'] as num?)?.toInt(),
    );
  }
}

List<AdminViagemManifestPassengerRecord> _parsePassengers(dynamic value) {
  if (value is! List) {
    return const [];
  }
  return value
      .map(
        (item) => AdminViagemManifestPassengerRecord.fromJson(
          item as Map<String, dynamic>,
        ),
      )
      .toList(growable: false);
}
