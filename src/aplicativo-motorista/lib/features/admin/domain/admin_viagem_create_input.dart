class AdminViagemCreateInput {
  const AdminViagemCreateInput({
    required this.dataOperacao,
    this.rotaBaseId,
    this.servicoId,
    this.motoristaId,
  });

  final int? rotaBaseId;
  final int? servicoId;
  final int? motoristaId;
  final DateTime dataOperacao;

  Map<String, dynamic> toJson() {
    final year = dataOperacao.year.toString().padLeft(4, '0');
    final month = dataOperacao.month.toString().padLeft(2, '0');
    final day = dataOperacao.day.toString().padLeft(2, '0');
    return {
      if (rotaBaseId != null) 'rotaBaseId': rotaBaseId,
      if (servicoId != null) 'servicoId': servicoId,
      if (motoristaId != null) 'motoristaId': motoristaId,
      'dataOperacao': '$year-$month-$day',
    };
  }
}
