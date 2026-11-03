class AdminViagensReviewIntent {
  const AdminViagensReviewIntent({
    required this.sourceLabel,
    required this.title,
    required this.description,
  });

  factory AdminViagensReviewIntent.passageiroServicos(String passageiroNome) {
    final nome = passageiroNome.trim();
    final alvo = nome.isEmpty ? 'passageiro' : nome;
    return AdminViagensReviewIntent(
      sourceLabel: 'PASSAGEIRO',
      title: 'Revise as viagens de $alvo',
      description:
          'Serviços do passageiro foram alterados. Atualize o dia e abra as viagens marcadas para revisar lista, paradas e presença.',
    );
  }

  factory AdminViagensReviewIntent.servicoRecorrente({
    required String servicoNome,
    required bool created,
  }) {
    final nome = servicoNome.trim();
    final alvo = nome.isEmpty ? 'serviço recorrente' : nome;
    return AdminViagensReviewIntent(
      sourceLabel: 'SERVIÇO',
      title: created ? 'Monte as viagens de $alvo' : 'Revise $alvo nas viagens',
      description:
          created
              ? 'Um serviço recorrente foi criado. Monte o dia para publicar as viagens que nascem desse serviço.'
              : 'O serviço recorrente foi alterado. Atualize o dia e abra as viagens impactadas para revisar a lista antes da operação.',
    );
  }

  final String sourceLabel;
  final String title;
  final String description;
}
