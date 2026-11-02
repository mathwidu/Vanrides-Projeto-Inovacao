import 'package:flutter/material.dart';

import 'admin_portal_layout.dart';

class AdminViagensDayStatusPanel extends StatelessWidget {
  const AdminViagensDayStatusPanel({
    super.key,
    required this.viagensCount,
    required this.inProgressCount,
    required this.completedCount,
    required this.reviewCount,
    required this.isGeneratingAutomatic,
    required this.onGenerateAutomatic,
    required this.onCreateManual,
  });

  final int viagensCount;
  final int inProgressCount;
  final int completedCount;
  final int reviewCount;
  final bool isGeneratingAutomatic;
  final VoidCallback onGenerateAutomatic;
  final VoidCallback onCreateManual;

  @override
  Widget build(BuildContext context) {
    final hasTrips = viagensCount > 0;
    final title =
        !hasTrips
            ? 'Dia ainda não montado'
            : reviewCount > 0
            ? _countLabel(
              reviewCount,
              'lista para revisar',
              'listas para revisar',
            )
            : 'Dia pronto para acompanhar';
    final helper =
        !hasTrips
            ? 'Monte o dia para trazer as viagens recorrentes antes de abrir uma exceção.'
            : reviewCount > 0
            ? 'Abra as viagens marcadas e sincronize a lista. O resto do dia já pode ser acompanhado.'
            : 'Acompanhe execução, motorista e paradas sem recriar viagens.';
    final primaryLabel =
        isGeneratingAutomatic
            ? (hasTrips ? 'Atualizando...' : 'Montando...')
            : (hasTrips ? 'Atualizar dia' : 'Montar dia');

    return AdminPortalSurface(
      radius: 36,
      padding: const EdgeInsets.all(24),
      child: LayoutBuilder(
        builder: (context, constraints) {
          final compact = constraints.maxWidth < 920;
          final copy = Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const AdminPortalTag(
                label: 'Estado do dia',
                background: Color(0x1F4DB8FF),
                foreground: AdminPortalColors.blue,
              ),
              const SizedBox(height: 12),
              Text(
                title,
                style: const TextStyle(
                  color: AdminPortalColors.text,
                  fontSize: 24,
                  fontWeight: FontWeight.w800,
                  height: 1.15,
                ),
              ),
              const SizedBox(height: 8),
              Text(
                helper,
                style: const TextStyle(
                  color: AdminPortalColors.textMuted,
                  fontSize: 13,
                  height: 1.4,
                ),
              ),
              const SizedBox(height: 16),
              Wrap(
                spacing: 10,
                runSpacing: 10,
                children: [
                  _AdminViagensStatusPill(
                    icon: Icons.calendar_view_day_rounded,
                    label: _countLabel(viagensCount, 'montada', 'montadas'),
                  ),
                  _AdminViagensStatusPill(
                    icon: Icons.near_me_outlined,
                    label: _countLabel(
                      inProgressCount,
                      'em operação',
                      'em operação',
                    ),
                  ),
                  _AdminViagensStatusPill(
                    icon: Icons.check_circle_outline_rounded,
                    label: _countLabel(
                      completedCount,
                      'concluída',
                      'concluídas',
                    ),
                  ),
                ],
              ),
            ],
          );
          final actions = Wrap(
            spacing: 12,
            runSpacing: 12,
            alignment: compact ? WrapAlignment.start : WrapAlignment.end,
            children: [
              AdminPortalPrimaryButton(
                label: primaryLabel,
                icon: Icons.auto_awesome_motion_outlined,
                onPressed: isGeneratingAutomatic ? null : onGenerateAutomatic,
              ),
              AdminPortalSecondaryButton(
                label: 'Exceção do dia',
                icon: Icons.add_circle_outline,
                onPressed: isGeneratingAutomatic ? null : onCreateManual,
              ),
            ],
          );

          if (compact) {
            return Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [copy, const SizedBox(height: 18), actions],
            );
          }

          return Row(
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              Expanded(child: copy),
              const SizedBox(width: 24),
              actions,
            ],
          );
        },
      ),
    );
  }
}

class _AdminViagensStatusPill extends StatelessWidget {
  const _AdminViagensStatusPill({required this.icon, required this.label});

  final IconData icon;
  final String label;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: AdminPortalColors.surfaceSoft.withValues(alpha: 0.74),
        borderRadius: BorderRadius.circular(999),
        border: Border.all(
          color: AdminPortalColors.glassHighlight.withValues(alpha: 0.62),
        ),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 16, color: AdminPortalColors.blue),
          const SizedBox(width: 8),
          Text(
            label,
            style: const TextStyle(
              color: AdminPortalColors.text,
              fontSize: 13,
              fontWeight: FontWeight.w800,
            ),
          ),
        ],
      ),
    );
  }
}

String _countLabel(int count, String singular, String plural) {
  return '$count ${count == 1 ? singular : plural}';
}
