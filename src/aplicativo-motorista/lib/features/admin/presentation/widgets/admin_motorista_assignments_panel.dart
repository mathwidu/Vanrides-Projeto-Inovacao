import 'package:flutter/material.dart';

import '../../domain/admin_servico_recorrente_record.dart';
import '../../domain/admin_viagem_record.dart';
import 'admin_portal_layout.dart';

class AdminMotoristaAssignmentsPanel extends StatelessWidget {
  const AdminMotoristaAssignmentsPanel({
    super.key,
    required this.servicos,
    required this.viagens,
    required this.loading,
    required this.errorMessage,
    required this.onRefresh,
  });

  final List<AdminServicoRecorrenteRecord> servicos;
  final List<AdminViagemRecord> viagens;
  final bool loading;
  final String? errorMessage;
  final VoidCallback? onRefresh;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AdminPortalColors.surfaceSoft,
        borderRadius: BorderRadius.circular(24),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Expanded(
                child: Text(
                  'Operação atribuída',
                  style: TextStyle(
                    color: AdminPortalColors.text,
                    fontSize: 16,
                    fontWeight: FontWeight.w800,
                  ),
                ),
              ),
              if (onRefresh != null)
                IconButton(
                  tooltip: 'Atualizar atribuições',
                  onPressed: loading ? null : onRefresh,
                  icon: const Icon(Icons.refresh_rounded),
                  color: AdminPortalColors.textMuted,
                ),
            ],
          ),
          const SizedBox(height: 10),
          if (loading)
            const LinearProgressIndicator(minHeight: 3)
          else if (errorMessage != null)
            Text(
              errorMessage!,
              style: const TextStyle(
                color: AdminPortalColors.orange,
                fontWeight: FontWeight.w700,
              ),
            )
          else ...[
            Wrap(
              spacing: 10,
              runSpacing: 10,
              children: [
                _AssignmentMetric(
                  label: _plural(
                    servicos.length,
                    'serviço padrão',
                    'serviços padrão',
                  ),
                  value: servicos.length.toString(),
                  icon: Icons.route_outlined,
                ),
                _AssignmentMetric(
                  label: _plural(viagens.length, 'viagem hoje', 'viagens hoje'),
                  value: viagens.length.toString(),
                  icon: Icons.departure_board_outlined,
                ),
              ],
            ),
            const SizedBox(height: 14),
            if (servicos.isEmpty && viagens.isEmpty)
              const Text(
                'Nenhum serviço padrão ou viagem de hoje atribuída. Use Serviços ou Viagens para escalar este motorista.',
                style: TextStyle(
                  color: AdminPortalColors.textMuted,
                  height: 1.35,
                ),
              )
            else ...[
              if (servicos.isNotEmpty)
                _AssignmentPreview(
                  title: 'Serviços padrão',
                  items: servicos
                      .take(3)
                      .map(
                        (servico) => _AssignmentPreviewItem(
                          title: servico.nome,
                          detail:
                              '${servico.tipoOperacaoLabel} • ${_textOrDash(servico.horarioPartida)}',
                        ),
                      )
                      .toList(growable: false),
                ),
              if (servicos.isNotEmpty && viagens.isNotEmpty)
                const SizedBox(height: 12),
              if (viagens.isNotEmpty)
                _AssignmentPreview(
                  title: 'Viagens hoje',
                  items: viagens
                      .take(3)
                      .map(
                        (viagem) => _AssignmentPreviewItem(
                          title: viagem.nomeOperacional,
                          detail:
                              '${viagem.status} • ${_textOrDash(viagem.horarioPartida)}',
                        ),
                      )
                      .toList(growable: false),
                ),
            ],
          ],
        ],
      ),
    );
  }
}

class _AssignmentMetric extends StatelessWidget {
  const _AssignmentMetric({
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
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      decoration: BoxDecoration(
        color: AdminPortalColors.blueSoft.withValues(alpha: 0.72),
        borderRadius: BorderRadius.circular(16),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, color: AdminPortalColors.blue, size: 18),
          const SizedBox(width: 8),
          Text(
            '$value $label',
            style: const TextStyle(
              color: AdminPortalColors.text,
              fontWeight: FontWeight.w900,
            ),
          ),
        ],
      ),
    );
  }
}

class _AssignmentPreview extends StatelessWidget {
  const _AssignmentPreview({required this.title, required this.items});

  final String title;
  final List<_AssignmentPreviewItem> items;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          title.toUpperCase(),
          style: const TextStyle(
            color: AdminPortalColors.textMuted,
            fontSize: 11,
            fontWeight: FontWeight.w800,
            letterSpacing: 1,
          ),
        ),
        const SizedBox(height: 8),
        for (final item in items)
          Padding(
            padding: const EdgeInsets.only(bottom: 8),
            child: Row(
              children: [
                Expanded(
                  child: Text(
                    item.title,
                    style: const TextStyle(
                      color: AdminPortalColors.text,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                ),
                const SizedBox(width: 10),
                Text(
                  item.detail,
                  style: const TextStyle(
                    color: AdminPortalColors.textMuted,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ],
            ),
          ),
      ],
    );
  }
}

class _AssignmentPreviewItem {
  const _AssignmentPreviewItem({required this.title, required this.detail});

  final String title;
  final String detail;
}

String _plural(int count, String singular, String plural) {
  return count == 1 ? singular : plural;
}

String _textOrDash(String? value) {
  final trimmed = value?.trim() ?? '';
  return trimmed.isEmpty ? '--:--' : trimmed;
}
