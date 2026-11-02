import 'package:flutter/material.dart';

import '../../domain/admin_passageiro_record.dart';
import '../../domain/admin_responsavel_record.dart';
import 'admin_portal_layout.dart';

class AdminVinculosInlineEmptyState extends StatelessWidget {
  const AdminVinculosInlineEmptyState({
    super.key,
    required this.title,
    required this.body,
  });

  final String title;
  final String body;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: AdminPortalColors.canvas,
        borderRadius: BorderRadius.circular(24),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            title,
            style: const TextStyle(
              color: AdminPortalColors.text,
              fontSize: 16,
              fontWeight: FontWeight.w800,
            ),
          ),
          const SizedBox(height: 8),
          Text(
            body,
            style: const TextStyle(
              color: AdminPortalColors.textMuted,
              fontSize: 14,
              height: 1.5,
            ),
          ),
        ],
      ),
    );
  }
}

class AdminVinculosMiniMetricCard extends StatelessWidget {
  const AdminVinculosMiniMetricCard({
    super.key,
    required this.label,
    required this.value,
  });

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 128,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AdminPortalColors.canvas,
        borderRadius: BorderRadius.circular(22),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            label.toUpperCase(),
            style: const TextStyle(
              color: AdminPortalColors.textMuted,
              fontSize: 11,
              fontWeight: FontWeight.w800,
              letterSpacing: 0.8,
            ),
          ),
          const SizedBox(height: 10),
          Text(
            value,
            style: const TextStyle(
              color: AdminPortalColors.text,
              fontSize: 28,
              fontWeight: FontWeight.w800,
            ),
          ),
        ],
      ),
    );
  }
}

class AdminVinculosModeButton extends StatelessWidget {
  const AdminVinculosModeButton({
    super.key,
    required this.label,
    this.active = false,
  });

  final String label;
  final bool active;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 14),
      decoration: BoxDecoration(
        color:
            active
                ? AdminPortalColors.blueSoft
                : AdminPortalColors.statusSurface,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(
          color:
              active
                  ? AdminPortalColors.blue.withValues(alpha: 0.34)
                  : AdminPortalColors.outline,
        ),
      ),
      alignment: Alignment.center,
      child: Text(
        label,
        style: TextStyle(
          color: active ? AdminPortalColors.blue : AdminPortalColors.textMuted,
          fontSize: 16,
          fontWeight: active ? FontWeight.w700 : FontWeight.w500,
        ),
      ),
    );
  }
}

class AdminVinculosDashedHintCard extends StatelessWidget {
  const AdminVinculosDashedHintCard({
    super.key,
    required this.icon,
    required this.title,
  });

  final IconData icon;
  final String title;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 42),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(32),
        border: Border.all(
          color: AdminPortalColors.outline,
          style: BorderStyle.solid,
        ),
      ),
      child: Column(
        children: [
          Icon(icon, color: AdminPortalColors.textMuted, size: 28),
          const SizedBox(height: 14),
          Text(
            title,
            textAlign: TextAlign.center,
            style: const TextStyle(
              color: AdminPortalColors.textMuted,
              fontSize: 14,
            ),
          ),
        ],
      ),
    );
  }
}

class AdminVinculosCreationPanel extends StatelessWidget {
  const AdminVinculosCreationPanel({
    super.key,
    required this.passageiros,
    required this.responsaveis,
    required this.onCreateLink,
  });

  final List<AdminPassageiroRecord> passageiros;
  final List<AdminResponsavelRecord> responsaveis;
  final Future<void> Function() onCreateLink;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        AdminPortalSurface(
          radius: 48,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Row(
                children: [
                  Expanded(
                    child: Text(
                      'Ajuste técnico responsável-passageiro',
                      style: TextStyle(
                        color: AdminPortalColors.text,
                        fontSize: 20,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                  ),
                  Icon(
                    Icons.rule_folder_outlined,
                    color: AdminPortalColors.blue,
                  ),
                ],
              ),
              const SizedBox(height: 24),
              const Text(
                'ESCOPO DO AJUSTE',
                style: adminVinculosTableHeaderStyle,
              ),
              const SizedBox(height: 12),
              const Row(
                children: [
                  Expanded(
                    child: AdminVinculosModeButton(
                      label: 'Custódia',
                      active: true,
                    ),
                  ),
                  SizedBox(width: 12),
                  Expanded(child: AdminVinculosModeButton(label: 'Exceção')),
                ],
              ),
              const SizedBox(height: 20),
              AdminVinculosDashedHintCard(
                icon: Icons.drag_indicator,
                title:
                    passageiros.isEmpty
                        ? 'Cadastre passageiros'
                        : 'Revise a ficha do passageiro',
              ),
              const SizedBox(height: 18),
              Center(
                child: Container(
                  width: 28,
                  height: 28,
                  decoration: const BoxDecoration(
                    color: AdminPortalColors.blue,
                    shape: BoxShape.circle,
                  ),
                  child: const Icon(Icons.link, size: 14, color: Colors.white),
                ),
              ),
              const SizedBox(height: 18),
              AdminVinculosDashedHintCard(
                icon: Icons.person_search_outlined,
                title:
                    responsaveis.isEmpty
                        ? 'Cadastre responsáveis'
                        : 'Revise a ficha do responsável',
              ),
              const SizedBox(height: 20),
              SizedBox(
                width: double.infinity,
                child: AdminPortalPrimaryButton(
                  label: 'Registrar ajuste técnico',
                  onPressed: onCreateLink,
                ),
              ),
              const SizedBox(height: 12),
              const Text(
                'Use esta ação quando a ficha de Passageiros ou Responsáveis não resolver a associação. Para cadastros comuns, volte às fichas das pessoas.',
                style: TextStyle(
                  color: AdminPortalColors.textMuted,
                  fontSize: 12,
                  height: 1.5,
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 24),
        AdminPortalSurface(
          radius: 40,
          color: AdminPortalColors.surfaceSoft,
          child: Row(
            children: [
              Container(
                width: 42,
                height: 42,
                decoration: const BoxDecoration(
                  color: AdminPortalColors.surface,
                  shape: BoxShape.circle,
                ),
                child: const Icon(
                  Icons.hub_outlined,
                  color: AdminPortalColors.blue,
                ),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      'Cadastros disponíveis',
                      style: TextStyle(
                        color: AdminPortalColors.textMuted,
                        fontSize: 14,
                      ),
                    ),
                    Text(
                      '${responsaveis.length + passageiros.length}',
                      style: const TextStyle(
                        color: AdminPortalColors.text,
                        fontSize: 22,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

class AdminVinculosErrorState extends StatelessWidget {
  const AdminVinculosErrorState({
    super.key,
    required this.message,
    required this.onRetry,
  });

  final String message;
  final Future<void> Function() onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const Icon(Icons.error_outline, size: 48),
          const SizedBox(height: 12),
          Text(message, textAlign: TextAlign.center),
          const SizedBox(height: 16),
          FilledButton.icon(
            onPressed: onRetry,
            icon: const Icon(Icons.refresh),
            label: const Text('Tentar novamente'),
          ),
        ],
      ),
    );
  }
}

const adminVinculosTableHeaderStyle = TextStyle(
  color: AdminPortalColors.textMuted,
  fontSize: 12,
  fontWeight: FontWeight.w700,
  letterSpacing: 0.8,
);
