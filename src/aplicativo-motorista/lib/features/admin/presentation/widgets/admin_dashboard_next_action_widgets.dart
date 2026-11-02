part of 'admin_dashboard_screen.dart';

class _DashboardNextAction {
  const _DashboardNextAction({
    required this.title,
    required this.body,
    required this.label,
    required this.icon,
    required this.background,
    required this.foreground,
    this.isReview = true,
    this.onTap,
  });

  final String title;
  final String body;
  final String label;
  final IconData icon;
  final Color background;
  final Color foreground;
  final bool isReview;
  final VoidCallback? onTap;

  static List<_DashboardNextAction> resolveAll({
    required AdminDashboardSummaryRecord summary,
    required bool isLoading,
    VoidCallback? onOpenServicos,
    VoidCallback? onOpenViagens,
    VoidCallback? onOpenMotoristas,
    VoidCallback? onOpenPassageiros,
  }) {
    final services = summary.servicosRecorrentes;
    final trips = summary.viagensHoje;
    final presences = summary.presencasServicoHoje;

    if (isLoading) {
      return const [
        _DashboardNextAction(
          title: 'Carregando operação',
          body: 'Buscando o estado mais recente do dia.',
          label: 'Aguarde',
          icon: Icons.sync_outlined,
          background: AdminPortalColors.surfaceSoft,
          foreground: AdminPortalColors.textMuted,
          isReview: false,
        ),
      ];
    }

    final actions = <_DashboardNextAction>[];

    if (services.ativos == 0) {
      actions.add(
        _DashboardNextAction(
          title: 'Crie o primeiro serviço recorrente',
          body: 'Defina a linha, horário e destino antes de montar viagens.',
          label: 'Abrir serviços',
          icon: Icons.route_outlined,
          background: AdminPortalColors.orangeSoft,
          foreground: AdminPortalColors.orange,
          onTap: onOpenServicos,
        ),
      );
    }

    if (summary.motoristasAtivos == 0) {
      actions.add(
        _DashboardNextAction(
          title: 'Cadastre motorista operacional',
          body: 'Convide ou aprove um motorista para assumir a operação.',
          label: 'Motoristas',
          icon: Icons.directions_bus_outlined,
          background: AdminPortalColors.orangeSoft,
          foreground: AdminPortalColors.orange,
          onTap: onOpenMotoristas,
        ),
      );
    }

    if (services.semMotorista > 0) {
      actions.add(
        _DashboardNextAction(
          title: 'Defina motorista para serviços',
          body:
              '${services.semMotorista} serviço(s) ainda precisam de escala padrão.',
          label: 'Revisar serviços',
          icon: Icons.assignment_ind_outlined,
          background: AdminPortalColors.orangeSoft,
          foreground: AdminPortalColors.orange,
          onTap: onOpenServicos,
        ),
      );
    }

    if (summary.passageirosComPresencaHoje == 0 && trips.total == 0) {
      actions.add(
        _DashboardNextAction(
          title: 'Libere passageiros para os serviços',
          body: 'A linha precisa ter passageiros elegíveis antes da operação.',
          label: 'Passageiros',
          icon: Icons.person_pin_circle_outlined,
          background: AdminPortalColors.blueSoft,
          foreground: AdminPortalColors.blue,
          onTap: onOpenPassageiros,
        ),
      );
    }

    if (trips.total == 0) {
      actions.add(
        _DashboardNextAction(
          title: 'Monte as viagens do dia',
          body:
              'Os serviços estão prontos. Gere as viagens da data operacional.',
          label: 'Abrir viagens',
          icon: Icons.auto_awesome_motion_outlined,
          background: AdminPortalColors.blueSoft,
          foreground: AdminPortalColors.blue,
          onTap: onOpenViagens,
          isReview: actions.isNotEmpty,
        ),
      );
    }

    if (trips.emAndamento > 0) {
      actions.add(
        _DashboardNextAction(
          title: 'Acompanhe viagens em execução',
          body: '${trips.emAndamento} viagem(ns) em andamento agora.',
          label: 'Abrir viagens',
          icon: Icons.route_outlined,
          background: AdminPortalColors.greenSoft,
          foreground: AdminPortalColors.green,
          isReview: false,
          onTap: onOpenViagens,
        ),
      );
    }

    if (presences.totalDecisoes == 0) {
      actions.add(
        _DashboardNextAction(
          title: 'Revise presenças de hoje',
          body:
              'Ainda não há decisões de presença registradas para a operação.',
          label: 'Abrir viagens',
          icon: Icons.fact_check_outlined,
          background: AdminPortalColors.surfaceSoft,
          foreground: AdminPortalColors.textMuted,
          onTap: onOpenViagens,
        ),
      );
    }

    if (actions.isNotEmpty) {
      return actions.take(3).toList(growable: false);
    }

    return [
      _DashboardNextAction(
        title: 'Operação pronta para revisão',
        body: 'Acompanhe lista, motorista e execução das viagens do dia.',
        label: 'Abrir viagens',
        icon: Icons.verified_outlined,
        background: AdminPortalColors.greenSoft,
        foreground: AdminPortalColors.green,
        isReview: false,
        onTap: onOpenViagens,
      ),
    ];
  }
}

class _DashboardNextActionPanel extends StatelessWidget {
  const _DashboardNextActionPanel({required this.actions});

  final List<_DashboardNextAction> actions;

  @override
  Widget build(BuildContext context) {
    final hasReview = actions.any((action) => action.isReview);
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: AdminPortalColors.elevatedSurface.withValues(alpha: 0.92),
        borderRadius: BorderRadius.circular(28),
        border: Border.all(
          color:
              hasReview
                  ? AdminPortalColors.orange.withValues(alpha: 0.28)
                  : AdminPortalColors.blue.withValues(alpha: 0.22),
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            hasReview ? 'Precisa de revisão' : 'Próxima ação',
            style: const TextStyle(
              color: AdminPortalColors.text,
              fontSize: 20,
              fontWeight: FontWeight.w800,
            ),
          ),
          const SizedBox(height: 6),
          Text(
            hasReview
                ? 'Resolva estes pontos antes de considerar o dia pronto.'
                : 'O dia está organizado. Continue acompanhando a operação.',
            style: const TextStyle(
              color: AdminPortalColors.textMuted,
              fontSize: 14,
            ),
          ),
          const SizedBox(height: 16),
          for (final entry in actions.indexed) ...[
            _DashboardNextActionRow(action: entry.$2, isPrimary: entry.$1 == 0),
            if (entry.$1 < actions.length - 1) const SizedBox(height: 12),
          ],
        ],
      ),
    );
  }
}

class _DashboardNextActionRow extends StatelessWidget {
  const _DashboardNextActionRow({
    required this.action,
    required this.isPrimary,
  });

  final _DashboardNextAction action;
  final bool isPrimary;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: AdminPortalColors.surface.withValues(alpha: 0.28),
        borderRadius: BorderRadius.circular(22),
        border: Border.all(color: action.foreground.withValues(alpha: 0.18)),
      ),
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.center,
          children: [
            Container(
              width: 44,
              height: 44,
              decoration: BoxDecoration(
                color: action.background.withValues(alpha: 0.82),
                borderRadius: BorderRadius.circular(18),
                border: Border.all(
                  color: action.foreground.withValues(alpha: 0.24),
                ),
              ),
              child: Icon(action.icon, color: action.foreground, size: 22),
            ),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  if (isPrimary) ...[
                    const Text(
                      'Próxima ação',
                      style: TextStyle(
                        color: AdminPortalColors.textMuted,
                        fontSize: 12,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    const SizedBox(height: 5),
                  ],
                  Text(
                    action.title,
                    style: const TextStyle(
                      color: AdminPortalColors.text,
                      fontSize: 20,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    action.body,
                    style: const TextStyle(
                      color: AdminPortalColors.textMuted,
                      fontSize: 14,
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(width: 12),
            Flexible(
              flex: 0,
              child: TextButton.icon(
                onPressed: action.onTap,
                icon: Icon(action.icon, size: 18),
                label: Text(action.label),
                style: TextButton.styleFrom(
                  foregroundColor: action.foreground,
                  padding: const EdgeInsets.symmetric(
                    horizontal: 14,
                    vertical: 12,
                  ),
                  textStyle: const TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.w800,
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
