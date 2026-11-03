class AdminDashboardSummaryRecord {
  const AdminDashboardSummaryRecord({
    required this.motoristasAtivos,
    required this.rotasAtivas,
    required this.passageirosComPresencaHoje,
    required this.servicosRecorrentes,
    required this.presencasServicoHoje,
    required this.viagensHoje,
  });

  final int motoristasAtivos;
  final int rotasAtivas;
  final int passageirosComPresencaHoje;
  final AdminServicosRecorrentesSummaryRecord servicosRecorrentes;
  final AdminPresencasServicoSummaryRecord presencasServicoHoje;
  final AdminViagensSummaryRecord viagensHoje;

  factory AdminDashboardSummaryRecord.zeros() {
    return const AdminDashboardSummaryRecord(
      motoristasAtivos: 0,
      rotasAtivas: 0,
      passageirosComPresencaHoje: 0,
      servicosRecorrentes: AdminServicosRecorrentesSummaryRecord(
        ativos: 0,
        automaticos: 0,
        semMotorista: 0,
      ),
      presencasServicoHoje: AdminPresencasServicoSummaryRecord(
        confirmadas: 0,
        ausentes: 0,
        canceladas: 0,
        totalDecisoes: 0,
      ),
      viagensHoje: AdminViagensSummaryRecord(
        total: 0,
        emAndamento: 0,
        concluidas: 0,
        planejadas: 0,
      ),
    );
  }

  factory AdminDashboardSummaryRecord.fromJson(Map<String, dynamic> json) {
    final viagens = json['viagensHoje'] as Map<String, dynamic>? ?? const {};
    final servicos =
        json['servicosRecorrentes'] as Map<String, dynamic>? ?? const {};
    final presencasServico =
        json['presencasServicoHoje'] as Map<String, dynamic>? ?? const {};
    return AdminDashboardSummaryRecord(
      motoristasAtivos: (json['motoristasAtivos'] as num?)?.toInt() ?? 0,
      rotasAtivas: (json['rotasAtivas'] as num?)?.toInt() ?? 0,
      passageirosComPresencaHoje:
          (json['passageirosComPresencaHoje'] as num?)?.toInt() ?? 0,
      servicosRecorrentes: AdminServicosRecorrentesSummaryRecord(
        ativos: (servicos['ativos'] as num?)?.toInt() ?? 0,
        automaticos: (servicos['automaticos'] as num?)?.toInt() ?? 0,
        semMotorista: (servicos['semMotorista'] as num?)?.toInt() ?? 0,
      ),
      presencasServicoHoje: AdminPresencasServicoSummaryRecord(
        confirmadas: (presencasServico['confirmadas'] as num?)?.toInt() ?? 0,
        ausentes: (presencasServico['ausentes'] as num?)?.toInt() ?? 0,
        canceladas: (presencasServico['canceladas'] as num?)?.toInt() ?? 0,
        totalDecisoes:
            (presencasServico['totalDecisoes'] as num?)?.toInt() ??
            ((presencasServico['confirmadas'] as num?)?.toInt() ?? 0) +
                ((presencasServico['ausentes'] as num?)?.toInt() ?? 0) +
                ((presencasServico['canceladas'] as num?)?.toInt() ?? 0),
      ),
      viagensHoje: AdminViagensSummaryRecord(
        total: (viagens['total'] as num?)?.toInt() ?? 0,
        emAndamento: (viagens['emAndamento'] as num?)?.toInt() ?? 0,
        concluidas: (viagens['concluidas'] as num?)?.toInt() ?? 0,
        planejadas: (viagens['planejadas'] as num?)?.toInt() ?? 0,
      ),
    );
  }
}

class AdminServicosRecorrentesSummaryRecord {
  const AdminServicosRecorrentesSummaryRecord({
    required this.ativos,
    required this.automaticos,
    required this.semMotorista,
  });

  final int ativos;
  final int automaticos;
  final int semMotorista;
}

class AdminPresencasServicoSummaryRecord {
  const AdminPresencasServicoSummaryRecord({
    required this.confirmadas,
    required this.ausentes,
    required this.canceladas,
    required this.totalDecisoes,
  });

  final int confirmadas;
  final int ausentes;
  final int canceladas;
  final int totalDecisoes;
}

class AdminViagensSummaryRecord {
  const AdminViagensSummaryRecord({
    required this.total,
    required this.emAndamento,
    required this.concluidas,
    required this.planejadas,
  });

  final int total;
  final int emAndamento;
  final int concluidas;
  final int planejadas;
}
