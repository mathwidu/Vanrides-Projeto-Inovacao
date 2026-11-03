import 'package:flutter/material.dart';

import '../../domain/admin_passageiro_record.dart';
import '../../domain/admin_responsavel_record.dart';
import 'admin_person_detail_components.dart';
import 'admin_portal_layout.dart';

class AdminPassageiroResponsaveisSection extends StatelessWidget {
  const AdminPassageiroResponsaveisSection({
    super.key,
    required this.passageiro,
    this.onManage,
    this.initiallyExpanded = false,
  });

  final AdminPassageiroRecord? passageiro;
  final Future<void> Function()? onManage;
  final bool initiallyExpanded;

  @override
  Widget build(BuildContext context) {
    final selected = passageiro;
    final responsaveis =
        selected?.responsaveis ?? const <AdminResponsavelRecord>[];
    final hasSelection = selected != null;

    return AdminDetailDisclosureSection(
      title: 'Responsáveis vinculados',
      subtitle: _guardianSectionCopy(
        hasSelection: hasSelection,
        count: responsaveis.length,
      ),
      icon: Icons.supervisor_account_outlined,
      accent: AdminPortalColors.warningText,
      initiallyExpanded: initiallyExpanded,
      trailing: AdminPortalSecondaryButton(
        label: 'Gerenciar responsáveis',
        icon: Icons.manage_accounts_outlined,
        onPressed:
            hasSelection && onManage != null
                ? () {
                  onManage!();
                }
                : null,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (!hasSelection)
            const _PassengerGuardianEmptyMessage(
              label: 'Selecione um passageiro',
              body:
                  'Os responsáveis autorizados aparecem aqui depois da seleção.',
            )
          else if (responsaveis.isEmpty)
            const _PassengerGuardianEmptyMessage(
              label: 'Sem responsáveis vinculados',
              body:
                  'Adicione quem pode acompanhar localização, presença e comunicados deste passageiro.',
            )
          else
            Wrap(
              spacing: 10,
              runSpacing: 10,
              children: responsaveis
                  .map(
                    (responsavel) =>
                        _PassengerGuardianChip(responsavel: responsavel),
                  )
                  .toList(growable: false),
            ),
        ],
      ),
    );
  }
}

class _PassengerGuardianChip extends StatelessWidget {
  const _PassengerGuardianChip({required this.responsavel});

  final AdminResponsavelRecord responsavel;

  @override
  Widget build(BuildContext context) {
    return Container(
      constraints: const BoxConstraints(minWidth: 220, maxWidth: 340),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AdminPortalColors.statusSurface,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: AdminPortalColors.outline),
      ),
      child: Row(
        children: [
          const Icon(
            Icons.verified_user_outlined,
            color: AdminPortalColors.warningText,
            size: 20,
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  responsavel.nome,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    color: AdminPortalColors.text,
                    fontSize: 14,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const SizedBox(height: 3),
                Text(
                  _valueOrFallback(responsavel.email, 'Contato não informado'),
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    color: AdminPortalColors.textMuted,
                    fontSize: 12,
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

class _PassengerGuardianEmptyMessage extends StatelessWidget {
  const _PassengerGuardianEmptyMessage({
    required this.label,
    required this.body,
  });

  final String label;
  final String body;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AdminPortalColors.canvas.withValues(alpha: 0.54),
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: AdminPortalColors.outline),
      ),
      child: Row(
        children: [
          const Icon(Icons.info_outline, color: AdminPortalColors.textMuted),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  label,
                  style: const TextStyle(
                    color: AdminPortalColors.text,
                    fontSize: 14,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const SizedBox(height: 3),
                Text(
                  body,
                  style: const TextStyle(
                    color: AdminPortalColors.textMuted,
                    fontSize: 12,
                    height: 1.35,
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

String _guardianSectionCopy({required bool hasSelection, required int count}) {
  if (!hasSelection) {
    return 'Escolha um passageiro para revisar quem acompanha a rotina.';
  }
  if (count == 0) {
    return 'Nenhum responsável acompanha este passageiro pelo app.';
  }
  return count == 1
      ? '1 responsável pode acompanhar presença, rota e comunicados.'
      : '$count responsáveis podem acompanhar presença, rota e comunicados.';
}

String _valueOrFallback(String? value, String fallback) {
  final trimmed = value?.trim() ?? '';
  return trimmed.isEmpty ? fallback : trimmed;
}
