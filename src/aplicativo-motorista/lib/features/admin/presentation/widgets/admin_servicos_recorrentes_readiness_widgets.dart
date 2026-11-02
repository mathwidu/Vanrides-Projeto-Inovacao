part of 'admin_servicos_recorrentes_detail_widgets.dart';

class AdminServicoReadinessSurface extends StatelessWidget {
  const AdminServicoReadinessSurface({
    super.key,
    required this.servico,
    this.today,
  });

  final AdminServicoRecorrenteRecord servico;
  final DateTime? today;

  @override
  Widget build(BuildContext context) {
    final readiness = _ServicoTodayReadiness.resolve(
      servico,
      today ?? DateTime.now(),
    );
    final hasPassengers = servico.quantidadePassageiros > 0;
    final hasDays = servico.diasOperacao.isNotEmpty;
    final hasDriver = servico.hasMotoristaPadrao;

    return AdminPortalSurface(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const _AdminServicoDetailSectionTitle('Prontidão operacional'),
          const SizedBox(height: 16),
          _ReadinessItem(
            icon:
                readiness.isReady
                    ? Icons.check_circle_outline
                    : Icons.error_outline,
            title: 'Status para hoje',
            value: readiness.statusLabel,
            background:
                readiness.isReady
                    ? AdminPortalColors.greenSoft
                    : AdminPortalColors.orangeSoft,
            foreground:
                readiness.isReady
                    ? AdminPortalColors.green
                    : AdminPortalColors.orange,
          ),
          Padding(
            padding: const EdgeInsets.only(bottom: 12),
            child: Text(
              readiness.summary,
              style: const TextStyle(
                color: AdminPortalColors.textMuted,
                height: 1.45,
              ),
            ),
          ),
          _ReadinessItem(
            icon: Icons.auto_mode_outlined,
            title: 'Viagem do dia',
            value:
                servico.geracaoAutomaticaAtiva
                    ? 'Geração automática'
                    : 'Geração manual',
            background:
                servico.geracaoAutomaticaAtiva
                    ? AdminPortalColors.greenSoft
                    : AdminPortalColors.orangeSoft,
            foreground:
                servico.geracaoAutomaticaAtiva
                    ? AdminPortalColors.green
                    : AdminPortalColors.orange,
          ),
          _ReadinessItem(
            icon: Icons.groups_outlined,
            title: 'Passageiros',
            value:
                hasPassengers
                    ? '${servico.quantidadePassageiros} elegíveis'
                    : 'Sem elegibilidade',
            background:
                hasPassengers
                    ? AdminPortalColors.greenSoft
                    : AdminPortalColors.redSoft,
            foreground:
                hasPassengers ? AdminPortalColors.green : AdminPortalColors.red,
          ),
          _ReadinessItem(
            icon: hasDriver ? Icons.badge_outlined : Icons.event_seat_outlined,
            title: 'Motorista',
            value: servico.motoristaPadrao?.nome ?? 'Escala por viagem',
            background:
                hasDriver
                    ? AdminPortalColors.greenSoft
                    : AdminPortalColors.blueSoft,
            foreground:
                hasDriver ? AdminPortalColors.green : AdminPortalColors.blue,
          ),
          _ReadinessItem(
            icon: Icons.calendar_month_outlined,
            title: 'Agenda',
            value: hasDays ? servico.diasOperacao.join(', ') : 'Sem dias',
            background:
                hasDays
                    ? AdminPortalColors.greenSoft
                    : AdminPortalColors.redSoft,
            foreground:
                hasDays ? AdminPortalColors.green : AdminPortalColors.red,
          ),
          _ReadinessItem(
            icon: Icons.airline_seat_recline_normal_outlined,
            title: 'Capacidade',
            value:
                servico.capacidadePlanejada == null
                    ? 'Não informada'
                    : '${servico.quantidadePassageiros}/${servico.capacidadePlanejada} lugares',
            background:
                readiness.hasExceededCapacity
                    ? AdminPortalColors.redSoft
                    : AdminPortalColors.greenSoft,
            foreground:
                readiness.hasExceededCapacity
                    ? AdminPortalColors.red
                    : AdminPortalColors.green,
            showDivider: false,
          ),
          const SizedBox(height: 16),
          _ServiceReadinessChecklist(readiness: readiness),
        ],
      ),
    );
  }
}

class AdminServicoChangeImpactNotice extends StatelessWidget {
  const AdminServicoChangeImpactNotice({super.key});

  @override
  Widget build(BuildContext context) {
    return const AdminPortalSurface(
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(Icons.info_outline, color: AdminPortalColors.blue, size: 22),
          SizedBox(width: 12),
          Expanded(
            child: Text(
              'Alterações neste serviço valem para as próximas montagens. Se a viagem de hoje já foi criada, ajuste em Viagens do dia.',
              style: TextStyle(
                color: AdminPortalColors.textMuted,
                height: 1.45,
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _ServiceReadinessChecklist extends StatelessWidget {
  const _ServiceReadinessChecklist({required this.readiness});

  final _ServicoTodayReadiness readiness;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AdminPortalColors.surfaceSoft,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: AdminPortalColors.divider),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            'Checklist para montar hoje',
            style: TextStyle(
              color: AdminPortalColors.text,
              fontWeight: FontWeight.w800,
            ),
          ),
          const SizedBox(height: 12),
          if (readiness.issues.isEmpty)
            const _ChecklistLine(
              text: 'Tudo pronto para gerar a viagem de hoje.',
              done: true,
            )
          else
            for (final issue in readiness.issues)
              _ChecklistLine(text: issue, done: false),
        ],
      ),
    );
  }
}

class _ChecklistLine extends StatelessWidget {
  const _ChecklistLine({required this.text, required this.done});

  final String text;
  final bool done;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(
            done ? Icons.check_circle_outline : Icons.radio_button_unchecked,
            color: done ? AdminPortalColors.green : AdminPortalColors.orange,
            size: 18,
          ),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              text,
              style: const TextStyle(
                color: AdminPortalColors.text,
                height: 1.35,
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _ReadinessItem extends StatelessWidget {
  const _ReadinessItem({
    required this.icon,
    required this.title,
    required this.value,
    required this.background,
    required this.foreground,
    this.showDivider = true,
  });

  final IconData icon;
  final String title;
  final String value;
  final Color background;
  final Color foreground;
  final bool showDivider;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 12),
      decoration: BoxDecoration(
        border:
            showDivider
                ? const Border(
                  bottom: BorderSide(color: AdminPortalColors.divider),
                )
                : null,
      ),
      child: Row(
        children: [
          Container(
            width: 38,
            height: 38,
            decoration: BoxDecoration(
              color: background,
              shape: BoxShape.circle,
            ),
            child: Icon(icon, color: foreground, size: 20),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: const TextStyle(
                    color: AdminPortalColors.textMuted,
                    fontSize: 12,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  value,
                  style: const TextStyle(
                    color: AdminPortalColors.text,
                    fontWeight: FontWeight.w800,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

String _readinessLabel(AdminServicoRecorrenteRecord servico) {
  if (!servico.ativo) return 'Inativo';
  if (servico.diasOperacao.isEmpty) return 'Sem agenda';
  if (servico.quantidadePassageiros == 0) return 'Sem passageiros';
  if (!servico.geracaoAutomaticaAtiva) return 'Geração manual';
  if (!servico.hasMotoristaPadrao) return 'Exige escala no dia';
  return 'Pronto';
}

Color _readinessBackground(AdminServicoRecorrenteRecord servico) {
  if (!servico.ativo ||
      servico.diasOperacao.isEmpty ||
      servico.quantidadePassageiros == 0) {
    return AdminPortalColors.redSoft;
  }
  if (!servico.geracaoAutomaticaAtiva) return AdminPortalColors.orangeSoft;
  if (!servico.hasMotoristaPadrao) return AdminPortalColors.orangeSoft;
  return AdminPortalColors.greenSoft;
}

Color _readinessForeground(AdminServicoRecorrenteRecord servico) {
  if (!servico.ativo ||
      servico.diasOperacao.isEmpty ||
      servico.quantidadePassageiros == 0) {
    return AdminPortalColors.red;
  }
  if (!servico.geracaoAutomaticaAtiva) return AdminPortalColors.orange;
  if (!servico.hasMotoristaPadrao) return AdminPortalColors.orange;
  return AdminPortalColors.green;
}

const _politicaOptions = {
  'INCLUIR_POR_PADRAO': 'Incluir por padrão',
  'EXIGE_CONFIRMACAO': 'Exige confirmação',
};

class _ServicoTodayReadiness {
  const _ServicoTodayReadiness({
    required this.statusLabel,
    required this.summary,
    required this.issues,
    required this.hasExceededCapacity,
  });

  final String statusLabel;
  final String summary;
  final List<String> issues;
  final bool hasExceededCapacity;

  bool get isReady => issues.isEmpty;

  static _ServicoTodayReadiness resolve(
    AdminServicoRecorrenteRecord servico,
    DateTime today,
  ) {
    final todayCode = _operationDayCode(today);
    final hasDestinationOrRoute = _hasDestinationOrRoute(servico);
    final hasExceededCapacity =
        servico.capacidadePlanejada != null &&
        servico.quantidadePassageiros > servico.capacidadePlanejada!;
    final operatesToday = servico.diasOperacao.contains(todayCode);
    final issues = <String>[
      if (!servico.hasMotoristaPadrao)
        'Escolha um motorista padrão ou escale na viagem do dia.',
      if (servico.quantidadePassageiros == 0)
        'Adicione ao menos um passageiro elegível.',
      if (hasExceededCapacity)
        'Reduza elegíveis ou aumente a capacidade planejada.',
      if (!hasDestinationOrRoute)
        'Informe destino final ou vincule uma rota-base.',
      if (!operatesToday) 'Hoje ($todayCode) não está nos dias de operação.',
    ];

    if (!servico.ativo) {
      issues.insert(0, 'Reative o serviço antes de montar novas viagens.');
    }

    if (issues.isEmpty) {
      return const _ServicoTodayReadiness(
        statusLabel: 'Pode montar viagem hoje',
        summary: 'Tudo pronto para gerar a viagem de hoje.',
        issues: [],
        hasExceededCapacity: false,
      );
    }

    final hasOnlyScheduleBlock =
        issues.length == 1 && !operatesToday && servico.ativo;
    return _ServicoTodayReadiness(
      statusLabel:
          hasOnlyScheduleBlock
              ? 'Fora do dia de operação'
              : hasExceededCapacity
              ? 'Capacidade excedida'
              : 'Ajuste antes de montar hoje',
      summary:
          'Resolva o checklist antes de gerar ou revisar a viagem deste serviço.',
      issues: List.unmodifiable(issues),
      hasExceededCapacity: hasExceededCapacity,
    );
  }
}

String _operationDayCode(DateTime date) {
  return switch (date.weekday) {
    DateTime.monday => 'SEG',
    DateTime.tuesday => 'TER',
    DateTime.wednesday => 'QUA',
    DateTime.thursday => 'QUI',
    DateTime.friday => 'SEX',
    DateTime.saturday => 'SAB',
    DateTime.sunday => 'DOM',
    _ => 'SEG',
  };
}

bool _hasDestinationOrRoute(AdminServicoRecorrenteRecord servico) {
  final hasName = (servico.destinoFinalNome ?? '').trim().isNotEmpty;
  final hasAddress = (servico.destinoFinalEndereco ?? '').trim().isNotEmpty;
  return hasName || hasAddress || servico.rotaBaseLegadaId != null;
}
