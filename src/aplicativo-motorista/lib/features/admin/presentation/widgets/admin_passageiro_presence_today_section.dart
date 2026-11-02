import 'package:flutter/material.dart';

import '../../../service_presence/domain/passenger_available_service_record.dart';
import '../../domain/admin_passageiro_record.dart';
import 'admin_person_detail_components.dart';
import 'admin_portal_layout.dart';

class AdminPassageiroPresenceTodaySection extends StatelessWidget {
  const AdminPassageiroPresenceTodaySection({
    super.key,
    required this.passageiro,
    required this.services,
    required this.isLoading,
    this.errorMessage,
    this.onReviewTrips,
  });

  final AdminPassageiroRecord? passageiro;
  final List<PassengerAvailableServiceRecord> services;
  final bool isLoading;
  final String? errorMessage;
  final VoidCallback? onReviewTrips;

  @override
  Widget build(BuildContext context) {
    final hasSelection = passageiro != null;

    return AdminDetailDisclosureSection(
      title: 'Presença de hoje',
      subtitle: _subtitle(
        hasSelection: hasSelection,
        isLoading: isLoading,
        services: services,
        errorMessage: errorMessage,
      ),
      icon: Icons.checklist_rtl_outlined,
      accent: AdminPortalColors.blue,
      initiallyExpanded: true,
      trailing: AdminPortalSecondaryButton(
        label: 'Ver viagens de hoje',
        icon: Icons.route_outlined,
        onPressed: hasSelection && onReviewTrips != null ? onReviewTrips : null,
      ),
      child: _content(hasSelection),
    );
  }

  Widget _content(bool hasSelection) {
    if (!hasSelection) {
      return const _PresenceTodayMessage(
        label: 'Selecione um passageiro',
        body: 'As decisões de presença do dia aparecem aqui.',
      );
    }

    if (isLoading) {
      return const _PresenceTodayMessage(
        label: 'Carregando decisões',
        body: 'Buscando serviços e presença do passageiro para hoje.',
        loading: true,
      );
    }

    final error = errorMessage?.trim();
    if (error != null && error.isNotEmpty) {
      return _PresenceTodayMessage(label: 'Presença indisponível', body: error);
    }

    if (services.isEmpty) {
      return const _PresenceTodayMessage(
        label: 'Sem serviços hoje',
        body:
            'Revise as viagens de hoje ou os serviços liberados antes de montar a lista.',
      );
    }

    final confirmed = services.where((service) => service.isConfirmed).length;
    final absent = services.where((service) => service.isAbsent).length;
    final pending = services.where((service) => !service.hasDecision).length;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Wrap(
          spacing: 10,
          runSpacing: 10,
          children: [
            _PresenceSummaryPill(
              icon: Icons.check_circle_outline_rounded,
              label: _confirmedLabel(confirmed),
              color: AdminPortalColors.green,
            ),
            if (absent > 0)
              _PresenceSummaryPill(
                icon: Icons.do_not_disturb_on_outlined,
                label: _absentLabel(absent),
                color: AdminPortalColors.orange,
              ),
            _PresenceSummaryPill(
              icon: Icons.help_outline_rounded,
              label: _pendingLabel(pending),
              color: AdminPortalColors.blue,
            ),
          ],
        ),
        const SizedBox(height: 14),
        Wrap(
          spacing: 10,
          runSpacing: 10,
          children: services
              .map((service) => _PresenceServiceChip(service: service))
              .toList(growable: false),
        ),
        const SizedBox(height: 14),
        const Text(
          'Serviços definem as próximas montagens. Para mexer no dia já montado, revise a viagem e sincronize a lista.',
          style: TextStyle(
            color: AdminPortalColors.textFaint,
            fontSize: 12,
            height: 1.45,
          ),
        ),
      ],
    );
  }
}

class _PresenceTodayMessage extends StatelessWidget {
  const _PresenceTodayMessage({
    required this.label,
    required this.body,
    this.loading = false,
  });

  final String label;
  final String body;
  final bool loading;

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
          if (loading)
            const SizedBox(
              width: 18,
              height: 18,
              child: CircularProgressIndicator(strokeWidth: 2),
            )
          else
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

class _PresenceSummaryPill extends StatelessWidget {
  const _PresenceSummaryPill({
    required this.icon,
    required this.label,
    required this.color,
  });

  final IconData icon;
  final String label;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.14),
        borderRadius: BorderRadius.circular(999),
        border: Border.all(color: color.withValues(alpha: 0.34)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 16, color: color),
          const SizedBox(width: 8),
          Text(
            label,
            style: const TextStyle(
              color: AdminPortalColors.text,
              fontSize: 12,
              fontWeight: FontWeight.w800,
            ),
          ),
        ],
      ),
    );
  }
}

class _PresenceServiceChip extends StatelessWidget {
  const _PresenceServiceChip({required this.service});

  final PassengerAvailableServiceRecord service;

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
          Icon(_statusIcon(service), color: _statusColor(service), size: 20),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  service.nome,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    color: AdminPortalColors.text,
                    fontSize: 14,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const SizedBox(height: 3),
                Text(
                  _serviceSubtitle(service),
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

String _subtitle({
  required bool hasSelection,
  required bool isLoading,
  required List<PassengerAvailableServiceRecord> services,
  required String? errorMessage,
}) {
  if (!hasSelection) {
    return 'Escolha um passageiro para ver as decisões do dia.';
  }
  if (isLoading) {
    return 'Buscando presença por serviço para hoje.';
  }
  if ((errorMessage ?? '').trim().isNotEmpty) {
    return 'Não foi possível carregar a presença agora.';
  }
  if (services.isEmpty) {
    return 'Nenhum serviço disponível para hoje.';
  }
  final decided = services.where((service) => service.hasDecision).length;
  return '$decided de ${services.length} serviços com decisão registrada.';
}

String _serviceSubtitle(PassengerAvailableServiceRecord service) {
  final horario = service.horarioPartida?.trim();
  final prefix =
      horario == null || horario.isEmpty
          ? service.tipoOperacaoLabel
          : '$horario • ${service.tipoOperacaoLabel}';
  return '$prefix • ${service.statusLabel}';
}

String _confirmedLabel(int count) => '$count ${count == 1 ? 'vou' : 'vão'}';

String _absentLabel(int count) =>
    '$count ${count == 1 ? 'não vai' : 'não vão'}';

String _pendingLabel(int count) => '$count sem decisão';

IconData _statusIcon(PassengerAvailableServiceRecord service) {
  if (service.isConfirmed) return Icons.check_circle_outline_rounded;
  if (service.isAbsent) return Icons.do_not_disturb_on_outlined;
  return Icons.help_outline_rounded;
}

Color _statusColor(PassengerAvailableServiceRecord service) {
  if (service.isConfirmed) return AdminPortalColors.green;
  if (service.isAbsent) return AdminPortalColors.orange;
  return AdminPortalColors.blue;
}
