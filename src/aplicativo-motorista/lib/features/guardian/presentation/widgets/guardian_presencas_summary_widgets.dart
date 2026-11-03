import 'package:flutter/material.dart';

import '../../../../app/widgets/vanrides_liquid_surface.dart';
import '../../../../l10n/app_strings.dart';
import '../../../service_presence/domain/passenger_available_service_record.dart';
import '../../../service_presence/presentation/service_presence_decision_list.dart';
import '../../../tracking/presentation/map_first_v2_components.dart';
import '../../domain/guardian_linked_passageiro_record.dart';

class GuardianPresenceMetricPill extends StatelessWidget {
  const GuardianPresenceMetricPill({
    super.key,
    required this.title,
    required this.value,
    required this.icon,
    required this.color,
  });

  final String title;
  final String value;
  final IconData icon;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return VanRidesLiquidSurface.action(
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 18, color: color),
          const SizedBox(width: 8),
          Text(
            value,
            style: Theme.of(context).textTheme.labelLarge?.copyWith(
              color: MapFirstPalette.textStrong,
              fontWeight: FontWeight.w900,
            ),
          ),
          const SizedBox(width: 6),
          Text(
            title,
            style: Theme.of(context).textTheme.labelSmall?.copyWith(
              color: MapFirstPalette.textMuted,
              fontWeight: FontWeight.w700,
            ),
          ),
        ],
      ),
    );
  }
}

class GuardianPresenceStatusBadge extends StatelessWidget {
  const GuardianPresenceStatusBadge({
    super.key,
    required this.label,
    required this.confirmed,
  });

  final String label;
  final bool confirmed;

  @override
  Widget build(BuildContext context) {
    final color = confirmed ? MapFirstPalette.green : MapFirstPalette.amber;

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 11, vertical: 8),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.13),
        borderRadius: BorderRadius.circular(999),
        border: Border.all(color: color.withValues(alpha: 0.24)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(
            confirmed
                ? Icons.check_circle_outline_rounded
                : Icons.schedule_outlined,
            size: 17,
            color: color,
          ),
          const SizedBox(width: 7),
          ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 220),
            child: Text(
              label,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: Theme.of(context).textTheme.labelLarge?.copyWith(
                color: MapFirstPalette.textStrong,
                fontWeight: FontWeight.w800,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class GuardianPresenceSelectedPassengerCard extends StatelessWidget {
  const GuardianPresenceSelectedPassengerCard({
    super.key,
    required this.passageiro,
    required this.driverLabel,
    required this.ageLabel,
  });

  final GuardianLinkedPassageiroRecord passageiro;
  final String driverLabel;
  final String ageLabel;

  @override
  Widget build(BuildContext context) {
    final compactDriverLabel = _compactDriverLabel(driverLabel);

    return VanRidesLiquidSurface.card(
      semanticLabel: 'Passageiro selecionado',
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Icon(
                Icons.person_pin_circle_rounded,
                color: MapFirstPalette.brandBlue,
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      passageiro.nome,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.titleMedium?.copyWith(
                        color: MapFirstPalette.textStrong,
                        fontWeight: FontWeight.w900,
                      ),
                    ),
                    const SizedBox(height: 8),
                    _AgeBadge(label: ageLabel),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 14),
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Icon(
                Icons.directions_bus_rounded,
                size: 18,
                color: MapFirstPalette.textMuted,
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Text(
                  compactDriverLabel,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                    color: MapFirstPalette.textMuted,
                    fontWeight: FontWeight.w700,
                    height: 1.25,
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  String _compactDriverLabel(String label) {
    final parts = label.split(':');
    if (parts.length > 1 && parts.last.trim().isNotEmpty) {
      return 'Motorista: ${parts.last.trim()}';
    }
    return label;
  }
}

class GuardianPresenceServiceArea extends StatelessWidget {
  const GuardianPresenceServiceArea({
    super.key,
    required this.passageiro,
    required this.driverLabel,
    required this.ageLabel,
    required this.services,
    required this.loading,
    required this.processing,
    required this.onConfirm,
    required this.onAbsent,
    required this.onClear,
    required this.onRefresh,
  });

  final GuardianLinkedPassageiroRecord passageiro;
  final String driverLabel;
  final String ageLabel;
  final List<PassengerAvailableServiceRecord> services;
  final bool loading;
  final bool processing;
  final ValueChanged<PassengerAvailableServiceRecord> onConfirm;
  final ValueChanged<PassengerAvailableServiceRecord> onAbsent;
  final ValueChanged<PassengerAvailableServiceRecord> onClear;
  final VoidCallback onRefresh;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        GuardianPresenceServiceAreaCard(
          key: const ValueKey('guardian.presencas.serviceArea'),
          passageiro: passageiro,
          driverLabel: driverLabel,
          ageLabel: ageLabel,
          services: services,
        ),
        const SizedBox(height: 20),
        if (loading)
          const Padding(
            padding: EdgeInsets.symmetric(vertical: 24),
            child: Center(child: CircularProgressIndicator()),
          )
        else if (services.isNotEmpty)
          ServicePresenceDecisionList(
            services: services,
            processing: processing,
            onConfirm: onConfirm,
            onAbsent: onAbsent,
            onClear: onClear,
            title: AppStrings.guardian.servicosDeHoje,
          )
        else
          Align(
            alignment: Alignment.centerLeft,
            child: TextButton.icon(
              onPressed: onRefresh,
              icon: const Icon(Icons.refresh_rounded),
              label: const Text('Atualizar horários'),
            ),
          ),
        const SizedBox(height: 20),
      ],
    );
  }
}

class GuardianPresenceServiceAreaCard extends StatelessWidget {
  const GuardianPresenceServiceAreaCard({
    super.key,
    required this.passageiro,
    required this.driverLabel,
    required this.ageLabel,
    required this.services,
  });

  final GuardianLinkedPassageiroRecord passageiro;
  final String driverLabel;
  final String ageLabel;
  final List<PassengerAvailableServiceRecord> services;

  @override
  Widget build(BuildContext context) {
    final total = services.length;
    final pending = services.where((service) => !service.hasDecision).length;
    final title = _title(total: total, pending: pending);
    final description = _description(total: total, pending: pending);
    final statusColor =
        total == 0
            ? MapFirstPalette.brandBlue
            : pending == 0
            ? MapFirstPalette.green
            : MapFirstPalette.amber;

    return VanRidesLiquidSurface.card(
      semanticLabel: 'Presença de hoje de ${passageiro.nome}',
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 14),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              MapFirstAvatar(
                label: passageiro.nome,
                size: 48,
                backgroundColor: statusColor,
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      title,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.titleMedium?.copyWith(
                        color: MapFirstPalette.textStrong,
                        fontWeight: FontWeight.w900,
                        height: 1.12,
                      ),
                    ),
                    const SizedBox(height: 7),
                    Text(
                      description,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                        color: MapFirstPalette.textMuted,
                        fontWeight: FontWeight.w600,
                        height: 1.32,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 14),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              _GuardianPresenceContextPill(
                icon: Icons.person_pin_circle_rounded,
                label: passageiro.nome,
                color: MapFirstPalette.brandBlue,
              ),
              _GuardianPresenceContextPill(
                icon: Icons.schedule_rounded,
                label: AppStrings.guardian.horariosLiberadosHoje(total),
                color: statusColor,
              ),
              if (pending > 0)
                _GuardianPresenceContextPill(
                  icon: Icons.help_outline_rounded,
                  label: AppStrings.guardian.horariosPendentesHoje(pending),
                  color: MapFirstPalette.amber,
                ),
              _GuardianPresenceContextPill(
                icon: Icons.family_restroom_rounded,
                label: ageLabel,
                color: MapFirstPalette.purple,
              ),
            ],
          ),
          const SizedBox(height: 12),
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Icon(
                Icons.directions_bus_rounded,
                size: 18,
                color: MapFirstPalette.textMuted,
              ),
              const SizedBox(width: 9),
              Expanded(
                child: Text(
                  _compactDriverLabel(driverLabel),
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.bodySmall?.copyWith(
                    color: MapFirstPalette.textMuted,
                    fontWeight: FontWeight.w700,
                    height: 1.25,
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  String _title({required int total, required int pending}) {
    if (total == 0) {
      return AppStrings.guardian.nenhumHorarioParaDecidirHoje;
    }
    if (pending == 0) {
      return AppStrings.guardian.presencaTudoDecididoHoje(passageiro.nome);
    }
    return AppStrings.guardian.presencaPrecisaDecidirHoje(
      passageiro.nome,
      pending,
    );
  }

  String _description({required int total, required int pending}) {
    if (total == 0) {
      return AppStrings.guardian.nenhumHorarioParaDecidirHojeDescricao(
        passageiro.nome,
      );
    }
    if (pending == 0) {
      return AppStrings.guardian.decisoesDeHojeRegistradas;
    }
    return AppStrings.guardian.reviseHorariosLiberados;
  }

  String _compactDriverLabel(String label) {
    final parts = label.split(':');
    if (parts.length > 1 && parts.last.trim().isNotEmpty) {
      return 'Motorista: ${parts.last.trim()}';
    }
    return label;
  }
}

class _GuardianPresenceContextPill extends StatelessWidget {
  const _GuardianPresenceContextPill({
    required this.icon,
    required this.label,
    required this.color,
  });

  final IconData icon;
  final String label;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.11),
        borderRadius: BorderRadius.circular(999),
        border: Border.all(color: color.withValues(alpha: 0.18)),
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 7),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, size: 16, color: color),
            const SizedBox(width: 6),
            Flexible(
              child: Text(
                label,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: Theme.of(context).textTheme.labelMedium?.copyWith(
                  color: MapFirstPalette.textStrong,
                  fontWeight: FontWeight.w800,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _AgeBadge extends StatelessWidget {
  const _AgeBadge({required this.label});

  final String label;

  @override
  Widget build(BuildContext context) {
    return Align(
      alignment: Alignment.centerLeft,
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: MapFirstPalette.brandBlue.withValues(alpha: 0.14),
          borderRadius: BorderRadius.circular(999),
          border: Border.all(
            color: MapFirstPalette.brandBlue.withValues(alpha: 0.22),
          ),
        ),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
          child: Text(
            label,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(context).textTheme.labelMedium?.copyWith(
              color: MapFirstPalette.brandBlue,
              fontWeight: FontWeight.w900,
            ),
          ),
        ),
      ),
    );
  }
}
