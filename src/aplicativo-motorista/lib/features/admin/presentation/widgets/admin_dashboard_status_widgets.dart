part of 'admin_dashboard_screen.dart';

class _SummaryRow extends StatelessWidget {
  const _SummaryRow({
    required this.label,
    required this.value,
    required this.accent,
  });

  final String label;
  final String value;
  final Color accent;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Container(
          width: 10,
          height: 10,
          decoration: BoxDecoration(
            color: accent,
            shape: BoxShape.circle,
            boxShadow: [
              BoxShadow(color: accent.withValues(alpha: 0.2), blurRadius: 10),
            ],
          ),
        ),
        const SizedBox(width: 10),
        Expanded(
          child: Text(
            label,
            style: const TextStyle(
              color: AdminPortalColors.textMuted,
              fontSize: 14,
            ),
          ),
        ),
        Text(
          value,
          style: const TextStyle(
            color: AdminPortalColors.text,
            fontSize: 18,
            fontWeight: FontWeight.w800,
          ),
        ),
      ],
    );
  }
}

class _RouteStatusRow extends StatelessWidget {
  const _RouteStatusRow({
    required this.title,
    required this.subtitle,
    required this.capacityLabel,
    required this.stateLabel,
    required this.stateBackground,
    required this.stateColor,
    required this.accent,
    required this.actionLabel,
    this.onTap,
  });

  final String title;
  final String subtitle;
  final String capacityLabel;
  final String stateLabel;
  final Color stateBackground;
  final Color stateColor;
  final Color accent;
  final String actionLabel;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    return ClipRRect(
      borderRadius: BorderRadius.circular(28),
      child: Container(
        decoration: BoxDecoration(
          color: AdminPortalColors.elevatedSurface.withValues(alpha: 0.9),
          borderRadius: BorderRadius.circular(28),
          border: Border.all(color: AdminPortalColors.outline),
          boxShadow: const [
            BoxShadow(
              color: Color(0x66030A12),
              blurRadius: 22,
              offset: Offset(0, 14),
            ),
          ],
        ),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Expanded(
              child: Padding(
                padding: const EdgeInsets.symmetric(
                  horizontal: 18,
                  vertical: 18,
                ),
                child: Wrap(
                  spacing: 16,
                  runSpacing: 14,
                  crossAxisAlignment: WrapCrossAlignment.center,
                  children: [
                    _RouteAccentIcon(accent: accent),
                    SizedBox(
                      width: 280,
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            title,
                            style: const TextStyle(
                              color: AdminPortalColors.text,
                              fontSize: 18,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                          const SizedBox(height: 3),
                          Text(
                            subtitle,
                            style: const TextStyle(
                              color: AdminPortalColors.textMuted,
                              fontSize: 13,
                            ),
                          ),
                        ],
                      ),
                    ),
                    Text(
                      capacityLabel,
                      style: const TextStyle(
                        color: AdminPortalColors.text,
                        fontSize: 14,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    _RouteCapacityBar(accent: accent),
                    AdminPortalTag(
                      label: stateLabel,
                      background: stateBackground,
                      foreground: stateColor,
                    ),
                    TextButton.icon(
                      onPressed: onTap,
                      icon: const Icon(Icons.arrow_forward_rounded, size: 17),
                      label: Text(actionLabel),
                      style: TextButton.styleFrom(
                        foregroundColor: AdminPortalColors.textMuted,
                        padding: const EdgeInsets.symmetric(
                          horizontal: 12,
                          vertical: 10,
                        ),
                        textStyle: const TextStyle(
                          fontSize: 13,
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _DashboardTodayReadinessPanel extends StatelessWidget {
  const _DashboardTodayReadinessPanel({
    required this.summary,
    required this.isLoading,
  });

  final AdminDashboardSummaryRecord summary;
  final bool isLoading;

  @override
  Widget build(BuildContext context) {
    final state = _DashboardTodayReadinessState.from(
      summary: summary,
      isLoading: isLoading,
    );

    return Container(
      padding: const EdgeInsets.fromLTRB(18, 16, 18, 16),
      decoration: BoxDecoration(
        color: AdminPortalColors.elevatedSurface.withValues(alpha: 0.92),
        borderRadius: BorderRadius.circular(28),
        border: Border.all(color: state.accent.withValues(alpha: 0.24)),
      ),
      child: Row(
        children: [
          Container(
            width: 46,
            height: 46,
            decoration: BoxDecoration(
              color: state.accent.withValues(alpha: 0.14),
              borderRadius: BorderRadius.circular(18),
              border: Border.all(color: state.accent.withValues(alpha: 0.26)),
            ),
            child: Icon(state.icon, color: state.accent, size: 23),
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  'Hoje operacional',
                  style: TextStyle(
                    color: AdminPortalColors.textMuted,
                    fontSize: 12,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  state.title,
                  style: const TextStyle(
                    color: AdminPortalColors.text,
                    fontSize: 22,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  state.subtitle,
                  style: const TextStyle(
                    color: AdminPortalColors.textMuted,
                    fontSize: 14,
                    fontWeight: FontWeight.w600,
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

class _DashboardTodayReadinessState {
  const _DashboardTodayReadinessState({
    required this.title,
    required this.subtitle,
    required this.icon,
    required this.accent,
  });

  final String title;
  final String subtitle;
  final IconData icon;
  final Color accent;

  factory _DashboardTodayReadinessState.from({
    required AdminDashboardSummaryRecord summary,
    required bool isLoading,
  }) {
    if (isLoading) {
      return const _DashboardTodayReadinessState(
        title: 'Carregando dia',
        subtitle: 'Buscando serviços, presenças e viagens.',
        icon: Icons.sync_outlined,
        accent: AdminPortalColors.blue,
      );
    }

    final trips = summary.viagensHoje;
    if (trips.emAndamento > 0) {
      return _DashboardTodayReadinessState(
        title: 'Dia em execução',
        subtitle:
            '${_adminTripCount(trips.emAndamento, 'em andamento')} • '
            '${_adminTripCount(trips.concluidas, 'concluída')} • '
            '${_adminTripCount(trips.planejadas, 'planejada')}',
        icon: Icons.near_me_outlined,
        accent: AdminPortalColors.green,
      );
    }

    if (trips.total == 0) {
      return _DashboardTodayReadinessState(
        title: 'Dia ainda não montado',
        subtitle:
            'Serviços prontos: ${summary.servicosRecorrentes.ativos} • '
            'decisões de presença: ${summary.presencasServicoHoje.totalDecisoes}',
        icon: Icons.event_busy_outlined,
        accent: AdminPortalColors.orange,
      );
    }

    if (trips.concluidas >= trips.total) {
      return _DashboardTodayReadinessState(
        title: 'Dia concluído',
        subtitle:
            '${_adminTripCount(trips.concluidas, 'concluída')} • '
            '${summary.presencasServicoHoje.totalDecisoes} decisões de presença',
        icon: Icons.verified_outlined,
        accent: AdminPortalColors.green,
      );
    }

    return _DashboardTodayReadinessState(
      title: 'Dia publicado',
      subtitle:
          '${_adminTripCount(trips.total, 'viagem publicada')} • '
          '${summary.presencasServicoHoje.totalDecisoes} decisões de presença',
      icon: Icons.route_outlined,
      accent: AdminPortalColors.blue,
    );
  }
}

String _adminTripCount(int count, String singular) {
  if (count == 1) {
    if (singular == 'em andamento') {
      return '1 viagem em andamento';
    }
    return '1 $singular';
  }
  final plural = switch (singular) {
    'em andamento' => 'viagens em andamento',
    'concluída' => 'concluídas',
    'planejada' => 'planejadas',
    'viagem publicada' => 'viagens publicadas',
    _ => '${singular}s',
  };
  return '$count $plural';
}

class _RouteAccentIcon extends StatelessWidget {
  const _RouteAccentIcon({required this.accent});

  final Color accent;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 40,
      height: 40,
      decoration: BoxDecoration(
        color: accent.withValues(alpha: 0.14),
        borderRadius: BorderRadius.circular(999),
        border: Border.all(color: accent.withValues(alpha: 0.28)),
      ),
      child: Icon(Icons.alt_route_outlined, color: accent, size: 20),
    );
  }
}

class _RouteCapacityBar extends StatelessWidget {
  const _RouteCapacityBar({required this.accent});

  final Color accent;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 96,
      height: 4,
      decoration: BoxDecoration(
        color: AdminPortalColors.canvas.withValues(alpha: 0.62),
        borderRadius: BorderRadius.circular(999),
      ),
      child: Align(
        alignment: Alignment.centerLeft,
        child: Container(
          width: 54,
          decoration: BoxDecoration(
            color: accent,
            borderRadius: BorderRadius.circular(999),
          ),
        ),
      ),
    );
  }
}
