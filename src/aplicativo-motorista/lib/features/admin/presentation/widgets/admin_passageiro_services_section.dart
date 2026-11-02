import 'package:flutter/material.dart';

import '../../domain/admin_passageiro_record.dart';
import '../../domain/admin_servico_recorrente_record.dart';
import 'admin_person_detail_components.dart';
import 'admin_portal_layout.dart';

class AdminPassageiroServicosSection extends StatelessWidget {
  const AdminPassageiroServicosSection({
    super.key,
    required this.passageiro,
    required this.servicos,
    this.onEdit,
    this.initiallyExpanded = false,
  });

  final AdminPassageiroRecord? passageiro;
  final List<AdminServicoRecorrenteRecord> servicos;
  final Future<void> Function()? onEdit;
  final bool initiallyExpanded;

  @override
  Widget build(BuildContext context) {
    final selected = passageiro;
    final enabledServicos =
        selected == null
            ? const <AdminServicoRecorrenteRecord>[]
            : servicos
                .where((servico) => servico.passageiroIds.contains(selected.id))
                .toList(growable: false);
    final hasSelection = selected != null;
    final hasServicos = servicos.isNotEmpty;

    return AdminDetailDisclosureSection(
      title: 'Serviços do passageiro',
      subtitle: _serviceSectionCopy(
        hasSelection: hasSelection,
        hasServicos: hasServicos,
        enabledCount: enabledServicos.length,
      ),
      icon: Icons.route_outlined,
      initiallyExpanded: initiallyExpanded,
      trailing: AdminPortalSecondaryButton(
        label: 'Editar serviços',
        icon: Icons.tune_rounded,
        onPressed:
            hasSelection && hasServicos && onEdit != null
                ? () {
                  onEdit!();
                }
                : null,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (!hasSelection)
            const _PassengerServicesEmptyMessage(
              label: 'Selecione um passageiro',
              body:
                  'A liberação de ida, volta e turnos aparece aqui depois da seleção.',
            )
          else if (!hasServicos)
            const _PassengerServicesEmptyMessage(
              label: 'Nenhum serviço recorrente',
              body:
                  'Crie serviços antes de liberar viagens para este passageiro.',
            )
          else if (enabledServicos.isEmpty)
            const _PassengerServicesEmptyMessage(
              label: 'Sem serviços liberados',
              body:
                  'O passageiro ainda não entra em viagens recorrentes. Use editar serviços para liberar os horários corretos.',
            )
          else
            Wrap(
              spacing: 10,
              runSpacing: 10,
              children: enabledServicos
                  .map((servico) => _PassengerServiceChip(servico: servico))
                  .toList(growable: false),
            ),
          if (hasSelection && hasServicos) ...[
            const SizedBox(height: 18),
            const Text(
              'Mudanças valem para as próximas montagens do dia. Se a viagem de hoje já existir, use “Atualizar lista” no detalhe da viagem.',
              style: TextStyle(
                color: AdminPortalColors.textFaint,
                fontSize: 12,
                height: 1.45,
              ),
            ),
          ],
        ],
      ),
    );
  }
}

class _PassengerServiceChip extends StatelessWidget {
  const _PassengerServiceChip({required this.servico});

  final AdminServicoRecorrenteRecord servico;

  @override
  Widget build(BuildContext context) {
    final subtitle = _serviceLabel(servico);

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
          Icon(
            servico.tipoOperacao.toUpperCase() == 'VOLTA'
                ? Icons.keyboard_return_rounded
                : Icons.route_outlined,
            color: AdminPortalColors.blue,
            size: 20,
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  servico.nome,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    color: AdminPortalColors.text,
                    fontSize: 14,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const SizedBox(height: 3),
                Text(
                  subtitle,
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

class _PassengerServicesEmptyMessage extends StatelessWidget {
  const _PassengerServicesEmptyMessage({
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

String _serviceSectionCopy({
  required bool hasSelection,
  required bool hasServicos,
  required int enabledCount,
}) {
  if (!hasSelection) {
    return 'Escolha um passageiro para revisar as linhas liberadas.';
  }
  if (!hasServicos) {
    return 'A empresa ainda não possui serviços recorrentes cadastrados.';
  }
  if (enabledCount == 0) {
    return 'Nenhuma linha recorrente está liberada para este passageiro.';
  }
  return enabledCount == 1
      ? '1 serviço recorrente liberado para presença e viagens.'
      : '$enabledCount serviços recorrentes liberados para presença e viagens.';
}

String _serviceLabel(AdminServicoRecorrenteRecord servico) {
  final horario = servico.horarioPartida?.trim();
  final tipo = servico.tipoOperacaoLabel;
  if (horario == null || horario.isEmpty) {
    return '$tipo • ${servico.politicaPresencaLabel}';
  }
  return '$horario • $tipo • ${servico.politicaPresencaLabel}';
}

extension on AdminServicoRecorrenteRecord {
  String get politicaPresencaLabel {
    return switch (politicaPresenca.toUpperCase()) {
      'EXIGE_CONFIRMACAO' => 'confirma presença',
      _ => 'entra por padrão',
    };
  }
}
