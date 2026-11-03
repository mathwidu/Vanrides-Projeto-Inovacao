import 'package:flutter/material.dart';

import '../../domain/admin_viagem_manifest_preview_record.dart';
import 'admin_portal_layout.dart';

class AdminViagensManifestPreviewDialog extends StatelessWidget {
  const AdminViagensManifestPreviewDialog({
    super.key,
    required this.preview,
    required this.allowSync,
  });

  final AdminViagemManifestPreviewRecord preview;
  final bool allowSync;

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      backgroundColor: AdminPortalColors.navy,
      surfaceTintColor: Colors.white,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(32)),
      titlePadding: const EdgeInsets.fromLTRB(28, 28, 28, 0),
      contentPadding: const EdgeInsets.fromLTRB(28, 20, 28, 8),
      actionsPadding: const EdgeInsets.fromLTRB(28, 8, 28, 24),
      title: Text(
        preview.semMudancas
            ? 'Lista já está atualizada'
            : 'Revisar atualização da lista',
        style: const TextStyle(
          color: Colors.white,
          fontSize: 26,
          fontWeight: FontWeight.w900,
        ),
      ),
      content: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 560),
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                preview.semMudancas
                    ? 'A viagem ${preview.servicoNome} já reflete os passageiros operacionais do serviço.'
                    : 'Confira o que muda em ${preview.servicoNome} antes de aplicar no dia.',
                style: const TextStyle(
                  color: AdminPortalColors.textMuted,
                  fontSize: 16,
                  height: 1.35,
                ),
              ),
              const SizedBox(height: 20),
              Wrap(
                spacing: 10,
                runSpacing: 10,
                children: [
                  _ManifestPreviewMetric(
                    label: 'Depois',
                    value: '${preview.paradasAposSincronizacao} paradas',
                    icon: Icons.route_rounded,
                  ),
                  _ManifestPreviewMetric(
                    label: 'Entram',
                    value: '${preview.adicionados}',
                    icon: Icons.person_add_alt_1_rounded,
                  ),
                  _ManifestPreviewMetric(
                    label: 'Saem',
                    value: '${preview.removidos}',
                    icon: Icons.person_remove_alt_1_rounded,
                  ),
                ],
              ),
              if (!preview.semMudancas) ...[
                const SizedBox(height: 22),
                _ManifestPreviewPeopleSection(
                  title: 'Entram na lista',
                  emptyLabel: 'Nenhum passageiro novo.',
                  passengers: preview.passageirosAdicionados,
                  accent: AdminPortalColors.green,
                ),
                const SizedBox(height: 14),
                _ManifestPreviewPeopleSection(
                  title: 'Saem da lista',
                  emptyLabel: 'Nenhum passageiro será removido.',
                  passengers: preview.passageirosRemovidos,
                  accent: AdminPortalColors.orange,
                ),
              ],
            ],
          ),
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(false),
          child: Text(allowSync ? 'Voltar' : 'Fechar'),
        ),
        if (allowSync)
          FilledButton.icon(
            onPressed: () => Navigator.of(context).pop(true),
            icon: const Icon(Icons.sync_rounded, size: 18),
            label: const Text('Aplicar atualização'),
          ),
      ],
    );
  }
}

class _ManifestPreviewMetric extends StatelessWidget {
  const _ManifestPreviewMetric({
    required this.label,
    required this.value,
    required this.icon,
  });

  final String label;
  final String value;
  final IconData icon;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: Colors.white.withValues(alpha: 0.1)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, color: AdminPortalColors.blue, size: 18),
          const SizedBox(width: 10),
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                label,
                style: const TextStyle(
                  color: AdminPortalColors.textMuted,
                  fontSize: 12,
                  fontWeight: FontWeight.w700,
                ),
              ),
              Text(
                value,
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 16,
                  fontWeight: FontWeight.w900,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _ManifestPreviewPeopleSection extends StatelessWidget {
  const _ManifestPreviewPeopleSection({
    required this.title,
    required this.emptyLabel,
    required this.passengers,
    required this.accent,
  });

  final String title;
  final String emptyLabel;
  final List<AdminViagemManifestPassengerRecord> passengers;
  final Color accent;

  @override
  Widget build(BuildContext context) {
    final visiblePassengers = passengers.take(3).toList(growable: false);
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: 0.06),
        borderRadius: BorderRadius.circular(22),
        border: Border.all(color: accent.withValues(alpha: 0.35)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            title,
            style: TextStyle(
              color: accent,
              fontSize: 14,
              fontWeight: FontWeight.w900,
            ),
          ),
          const SizedBox(height: 10),
          if (visiblePassengers.isEmpty)
            Text(
              emptyLabel,
              style: const TextStyle(
                color: AdminPortalColors.textMuted,
                fontSize: 14,
              ),
            )
          else
            ...visiblePassengers.map(
              (passenger) => Padding(
                padding: const EdgeInsets.only(bottom: 8),
                child: Row(
                  children: [
                    Expanded(
                      child: Text(
                        passenger.nome,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 15,
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                    ),
                    if (passenger.ordemNova != null)
                      Text(
                        '#${passenger.ordemNova}',
                        style: const TextStyle(
                          color: AdminPortalColors.textMuted,
                          fontSize: 13,
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                  ],
                ),
              ),
            ),
          if (passengers.length > visiblePassengers.length)
            Text(
              '+${passengers.length - visiblePassengers.length} passageiros',
              style: const TextStyle(
                color: AdminPortalColors.textMuted,
                fontSize: 13,
                fontWeight: FontWeight.w800,
              ),
            ),
        ],
      ),
    );
  }
}
