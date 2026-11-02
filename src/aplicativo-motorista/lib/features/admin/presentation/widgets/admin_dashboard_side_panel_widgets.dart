part of 'admin_dashboard_screen.dart';

class _DashboardSidePanel extends StatelessWidget {
  const _DashboardSidePanel({
    required this.summary,
    required this.isLoading,
    this.onOpenServicos,
    this.onOpenViagens,
    this.onOpenMotoristas,
    this.onOpenPassageiros,
  });

  final AdminDashboardSummaryRecord summary;
  final bool isLoading;
  final VoidCallback? onOpenServicos;
  final VoidCallback? onOpenViagens;
  final VoidCallback? onOpenMotoristas;
  final VoidCallback? onOpenPassageiros;

  @override
  Widget build(BuildContext context) {
    final actions = [
      if (onOpenServicos != null)
        _ActionButton(
          label: 'Abrir serviços',
          icon: Icons.route_outlined,
          onTap: onOpenServicos!,
        ),
      if (onOpenViagens != null)
        _ActionButton(
          label: 'Abrir viagens',
          icon: Icons.departure_board,
          onTap: onOpenViagens!,
        ),
      if (onOpenMotoristas != null)
        _ActionButton(
          label: 'Motoristas',
          icon: Icons.person_add_alt_1_outlined,
          onTap: onOpenMotoristas!,
        ),
      if (onOpenPassageiros != null)
        _ActionButton(
          label: 'Passageiros',
          icon: Icons.person_pin_circle_outlined,
          onTap: onOpenPassageiros!,
        ),
    ];

    return Column(
      children: [
        AdminPortalSurface(
          radius: 40,
          padding: const EdgeInsets.all(24),
          color: AdminPortalColors.navy,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text(
                'Atalhos',
                style: TextStyle(
                  color: AdminPortalColors.text,
                  fontSize: 28,
                  fontWeight: FontWeight.w800,
                ),
              ),
              const SizedBox(height: 20),
              Wrap(spacing: 16, runSpacing: 16, children: actions),
            ],
          ),
        ),
        const SizedBox(height: 24),
        AdminPortalSurface(
          radius: 36,
          padding: const EdgeInsets.all(24),
          color: AdminPortalColors.surfaceSoft,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text(
                'Resumo do dia',
                style: TextStyle(
                  color: AdminPortalColors.text,
                  fontSize: 24,
                  fontWeight: FontWeight.w800,
                ),
              ),
              const SizedBox(height: 18),
              _SummaryRow(
                label: 'Em andamento',
                value:
                    isLoading
                        ? '—'
                        : summary.viagensHoje.emAndamento.toString(),
                accent: AdminPortalColors.green,
              ),
              const SizedBox(height: 12),
              _SummaryRow(
                label: 'Concluídas',
                value:
                    isLoading ? '—' : summary.viagensHoje.concluidas.toString(),
                accent: AdminPortalColors.blue,
              ),
              const SizedBox(height: 12),
              _SummaryRow(
                label: 'Planejadas',
                value:
                    isLoading ? '—' : summary.viagensHoje.planejadas.toString(),
                accent: AdminPortalColors.orange,
              ),
              const SizedBox(height: 12),
              _SummaryRow(
                label: 'Serviços ativos',
                value:
                    isLoading
                        ? '—'
                        : summary.servicosRecorrentes.ativos.toString(),
                accent: AdminPortalColors.textMuted,
              ),
              const SizedBox(height: 12),
              _SummaryRow(
                label: 'Sem motorista fixo',
                value:
                    isLoading
                        ? '—'
                        : summary.servicosRecorrentes.semMotorista.toString(),
                accent: AdminPortalColors.orange,
              ),
            ],
          ),
        ),
      ],
    );
  }
}

class _ActionButton extends StatelessWidget {
  const _ActionButton({required this.label, required this.icon, this.onTap});

  final String label;
  final IconData icon;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: AdminPortalColors.surface.withValues(alpha: 0.34),
      borderRadius: BorderRadius.circular(26),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(26),
        child: SizedBox(
          width: 172,
          height: 136,
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Icon(icon, color: AdminPortalColors.textMuted),
                Text(
                  label,
                  maxLines: 2,
                  style: const TextStyle(
                    color: AdminPortalColors.text,
                    fontSize: 16,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
