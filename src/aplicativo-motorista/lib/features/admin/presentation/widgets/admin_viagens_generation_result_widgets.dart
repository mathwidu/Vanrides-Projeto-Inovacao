import 'package:flutter/material.dart';

import '../../domain/admin_viagem_bulk_create_result.dart';
import 'admin_portal_layout.dart';

class AdminViagensGenerationResultPanel extends StatelessWidget {
  const AdminViagensGenerationResultPanel({
    super.key,
    required this.result,
    this.onOpenTrip,
  });

  static const openFirstTripButtonKey = Key(
    'admin.viagens.generationResult.openFirstTrip',
  );

  final AdminViagemBulkCreateResult result;
  final ValueChanged<int>? onOpenTrip;

  @override
  Widget build(BuildContext context) {
    final hasReview =
        result.servicosIgnorados.isNotEmpty ||
        result.servicosBloqueados.isNotEmpty ||
        result.bloqueadas > 0;
    final accent =
        hasReview ? AdminPortalColors.warningText : AdminPortalColors.green;
    final title =
        hasReview ? 'Dia montado com revisão' : 'Dia operacional montado';
    final subtitle =
        'Resultado: ${_createdTripsLabel(result.criadas)}, '
        '${_updatedTripsLabel(result.atualizadas)}, '
        '${result.existentes} já existiam, '
        '${_blockedTripsLabel(result.bloqueadas)}, '
        '${_ignoredTripsLabel(result.ignoradas)}.';

    return AdminPortalSurface(
      radius: 32,
      color: AdminPortalColors.elevatedSurface,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Icon(
                hasReview
                    ? Icons.warning_amber_rounded
                    : Icons.check_circle_outline_rounded,
                color: accent,
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      title,
                      style: TextStyle(
                        color: accent,
                        fontSize: 18,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    const SizedBox(height: 6),
                    Text(
                      subtitle,
                      style: const TextStyle(
                        color: AdminPortalColors.textMuted,
                        fontSize: 13,
                        height: 1.5,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
          if (hasReview) ...[
            const SizedBox(height: 18),
            if (result.servicosBloqueados.isNotEmpty) ...[
              const _AdminViagensResultSectionLabel(
                label: 'Bloqueadas para proteger a operação',
              ),
              ...result.servicosBloqueados
                  .take(4)
                  .map(
                    (item) => Padding(
                      padding: const EdgeInsets.only(top: 10),
                      child: _AdminViagensReviewServiceLine(
                        item: item,
                        tone: _AdminViagensReviewTone.blocked,
                      ),
                    ),
                  ),
            ],
            if (result.servicosIgnorados.isNotEmpty) ...[
              if (result.servicosBloqueados.isNotEmpty)
                const SizedBox(height: 16),
              const _AdminViagensResultSectionLabel(
                label: 'Serviços que precisam de revisão',
              ),
              ...result.servicosIgnorados
                  .take(4)
                  .map(
                    (item) => Padding(
                      padding: const EdgeInsets.only(top: 10),
                      child: _AdminViagensReviewServiceLine(
                        item: item,
                        tone: _AdminViagensReviewTone.ignored,
                      ),
                    ),
                  ),
            ],
          ],
          if (onOpenTrip != null && result.viagens.isNotEmpty) ...[
            const SizedBox(height: 18),
            Align(
              alignment: Alignment.centerRight,
              child: AdminPortalPrimaryButton(
                key: openFirstTripButtonKey,
                label:
                    result.viagens.length == 1
                        ? 'Abrir viagem atualizada'
                        : 'Abrir primeira viagem',
                icon: Icons.visibility_outlined,
                onPressed: () => onOpenTrip!(result.viagens.first.id),
              ),
            ),
          ],
        ],
      ),
    );
  }
}

String adminViagensBulkGenerationMessage(AdminViagemBulkCreateResult result) {
  final base =
      'Dia montado: ${_createdTripsLabel(result.criadas)}, '
      '${_updatedTripsLabel(result.atualizadas)}, '
      '${result.existentes} já existiam, '
      '${_blockedTripsLabel(result.bloqueadas)}, '
      '${_ignoredTripsLabel(result.ignoradas)}.';
  final revisoesOperacionais = [
    ...result.servicosBloqueados,
    ...result.servicosIgnorados,
  ];
  if (revisoesOperacionais.isEmpty) {
    return base;
  }

  final revisoes = revisoesOperacionais
      .take(3)
      .map((item) => '${item.nomeOperacional} - ${item.motivoLabel}')
      .join('; ');
  final restantes = revisoesOperacionais.length - 3;
  final sufixo = restantes > 0 ? ' e mais $restantes.' : '.';
  return '$base Revise: $revisoes$sufixo';
}

String _createdTripsLabel(int count) {
  if (count == 1) {
    return '1 criada';
  }
  return '$count criadas';
}

String _updatedTripsLabel(int count) {
  if (count == 1) {
    return '1 atualizada';
  }
  return '$count atualizadas';
}

String _blockedTripsLabel(int count) {
  if (count == 1) {
    return '1 bloqueada';
  }
  return '$count bloqueadas';
}

String _ignoredTripsLabel(int count) {
  if (count == 1) {
    return '1 ignorada';
  }
  return '$count ignoradas';
}

class _AdminViagensResultSectionLabel extends StatelessWidget {
  const _AdminViagensResultSectionLabel({required this.label});

  final String label;

  @override
  Widget build(BuildContext context) {
    return Text(
      label,
      style: const TextStyle(
        color: AdminPortalColors.text,
        fontSize: 13,
        fontWeight: FontWeight.w800,
      ),
    );
  }
}

enum _AdminViagensReviewTone { blocked, ignored }

class _AdminViagensReviewServiceLine extends StatelessWidget {
  const _AdminViagensReviewServiceLine({
    required this.item,
    required this.tone,
  });

  final AdminViagemIgnoradaRecord item;
  final _AdminViagensReviewTone tone;

  @override
  Widget build(BuildContext context) {
    final accent =
        tone == _AdminViagensReviewTone.blocked
            ? AdminPortalColors.warningText
            : AdminPortalColors.blue;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      decoration: BoxDecoration(
        color: AdminPortalColors.statusSurface,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: accent.withValues(alpha: 0.22)),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  item.nomeOperacional,
                  style: const TextStyle(
                    color: AdminPortalColors.text,
                    fontSize: 14,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                if (item.mensagem.trim().isNotEmpty) ...[
                  const SizedBox(height: 4),
                  Text(
                    _adminViagensHumanizeOperationalMessage(item.mensagem),
                    style: const TextStyle(
                      color: AdminPortalColors.textMuted,
                      fontSize: 12,
                      height: 1.4,
                    ),
                  ),
                ],
              ],
            ),
          ),
          const SizedBox(width: 12),
          AdminPortalTag(
            label: item.motivoLabel,
            background: accent.withValues(alpha: 0.14),
            foreground: accent,
          ),
        ],
      ),
    );
  }
}

String _adminViagensHumanizeOperationalMessage(String message) {
  return message
      .trim()
      .replaceAll('Manifesto operacional', 'Lista da viagem')
      .replaceAll('manifesto operacional', 'lista da viagem')
      .replaceAll('Sincronize o manifesto', 'Atualize a lista')
      .replaceAll('sincronize o manifesto', 'atualize a lista')
      .replaceAll('sincronizacao do manifesto', 'atualizacao da lista')
      .replaceAll('sincronização do manifesto', 'atualização da lista');
}
