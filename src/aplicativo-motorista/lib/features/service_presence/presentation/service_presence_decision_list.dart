import 'package:flutter/material.dart';

import '../domain/passenger_available_service_record.dart';
import 'service_presence_empty_state.dart';

class ServicePresenceDecisionList extends StatelessWidget {
  const ServicePresenceDecisionList({
    super.key,
    required this.services,
    required this.processing,
    required this.onConfirm,
    required this.onAbsent,
    required this.onClear,
    this.title = 'Presença de hoje',
    this.emptyTitle = 'Nenhum horário para decidir agora',
    this.emptyDescription =
        'Quando houver serviço liberado para hoje, a decisão aparece aqui.',
    this.emptyActionLabel,
    this.onEmptyAction,
  });

  final List<PassengerAvailableServiceRecord> services;
  final bool processing;
  final ValueChanged<PassengerAvailableServiceRecord> onConfirm;
  final ValueChanged<PassengerAvailableServiceRecord> onAbsent;
  final ValueChanged<PassengerAvailableServiceRecord> onClear;
  final String title;
  final String emptyTitle;
  final String emptyDescription;
  final String? emptyActionLabel;
  final VoidCallback? onEmptyAction;

  @override
  Widget build(BuildContext context) {
    if (services.isEmpty) {
      return ServicePresenceEmptyState(
        title: title,
        emptyTitle: emptyTitle,
        emptyDescription: emptyDescription,
        actionLabel: emptyActionLabel,
        onAction: onEmptyAction,
      );
    }

    final summary = _ServicePresenceSummary.from(services);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _PresenceSummaryHeader(title: title, summary: summary),
        const SizedBox(height: 12),
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: _summaryPills(context, summary),
        ),
        const SizedBox(height: 14),
        for (final service in services) ...[
          _ServicePresenceCard(
            service: service,
            processing: processing,
            onConfirm: () => onConfirm(service),
            onAbsent: () => onAbsent(service),
            onClear: service.presencaId == null ? null : () => onClear(service),
          ),
          const SizedBox(height: 10),
        ],
      ],
    );
  }

  List<Widget> _summaryPills(
    BuildContext context,
    _ServicePresenceSummary summary,
  ) {
    final colorScheme = Theme.of(context).colorScheme;
    return [
      _StatusPill(
        label: summary.totalLabel,
        foreground: colorScheme.primary,
        background: colorScheme.primary.withValues(alpha: 0.12),
      ),
      if (summary.confirmed > 0)
        _StatusPill(
          label: summary.confirmedLabel,
          foreground: Colors.green.shade600,
          background: Colors.green.shade600.withValues(alpha: 0.12),
        ),
      if (summary.absent > 0)
        _StatusPill(
          label: summary.absentLabel,
          foreground: Colors.red.shade500,
          background: Colors.red.shade500.withValues(alpha: 0.12),
        ),
      if (summary.pending > 0)
        _StatusPill(
          label: summary.pendingLabel,
          foreground: colorScheme.onSurfaceVariant,
          background: colorScheme.surfaceContainerHigh,
        ),
    ];
  }
}

class _ServicePresenceCard extends StatelessWidget {
  const _ServicePresenceCard({
    required this.service,
    required this.processing,
    required this.onConfirm,
    required this.onAbsent,
    required this.onClear,
  });

  final PassengerAvailableServiceRecord service;
  final bool processing;
  final VoidCallback onConfirm;
  final VoidCallback onAbsent;
  final VoidCallback? onClear;

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final decision = _ServicePresenceDecision.from(service, colorScheme);
    final destination = service.destinoFinalNome?.trim();
    final driver = service.motoristaPadrao?.nome.trim();

    return Semantics(
      container: true,
      explicitChildNodes: true,
      label: _semanticsLabel(decision, destination),
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: colorScheme.surfaceContainerHighest.withValues(alpha: 0.42),
          borderRadius: BorderRadius.circular(18),
          border: Border.all(
            color: decision.color.withValues(
              alpha: service.hasDecision ? 0.32 : 0.16,
            ),
          ),
        ),
        child: Padding(
          padding: const EdgeInsets.fromLTRB(14, 14, 14, 12),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              LayoutBuilder(
                builder: (context, constraints) {
                  final compact = constraints.maxWidth < 320;
                  final titleBlock = Expanded(
                    child: _ServicePresenceTitleBlock(
                      operationLabel: _operationLabel(service),
                      serviceName: service.nome,
                    ),
                  );
                  final status = _ServiceStatusActions(
                    label: decision.label,
                    color: decision.color,
                    clearKey: ValueKey(
                      'service-presence.clear.${service.servicoId}',
                    ),
                    onClear: service.hasDecision ? onClear : null,
                    processing: processing,
                  );

                  if (compact) {
                    return Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            _ServiceIcon(
                              color: decision.color,
                              icon: decision.icon,
                            ),
                            const SizedBox(width: 12),
                            titleBlock,
                          ],
                        ),
                        const SizedBox(height: 10),
                        status,
                      ],
                    );
                  }

                  return Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      _ServiceIcon(color: decision.color, icon: decision.icon),
                      const SizedBox(width: 12),
                      titleBlock,
                      const SizedBox(width: 10),
                      status,
                    ],
                  );
                },
              ),
              const SizedBox(height: 12),
              Text(
                [
                  if (destination?.isNotEmpty == true) destination!,
                  if (driver?.isNotEmpty == true) driver! else 'A definir',
                ].join(' • '),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                  color: colorScheme.onSurfaceVariant,
                  height: 1.32,
                ),
              ),
              if (!service.hasDecision) ...[
                const SizedBox(height: 12),
                Row(
                  children: [
                    Expanded(
                      child: _DecisionButton(
                        key: ValueKey(
                          'service-presence.confirm.${service.servicoId}',
                        ),
                        label: 'Vou',
                        icon: Icons.check_rounded,
                        selected: false,
                        color: Colors.green.shade600,
                        onPressed: processing ? null : onConfirm,
                      ),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: _DecisionButton(
                        key: ValueKey(
                          'service-presence.absent.${service.servicoId}',
                        ),
                        label: 'Não vou',
                        icon: Icons.close_rounded,
                        selected: false,
                        color: Colors.red.shade500,
                        onPressed: processing ? null : onAbsent,
                      ),
                    ),
                  ],
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }

  String _operationLabel(PassengerAvailableServiceRecord service) {
    final horario = service.horarioPartida?.trim();
    if (horario == null || horario.isEmpty) {
      return service.tipoOperacaoLabel;
    }
    return '$horario • ${service.tipoOperacaoLabel}';
  }

  String _semanticsLabel(
    _ServicePresenceDecision decision,
    String? destination,
  ) {
    final details = [
      _operationLabel(
        service,
      ).replaceAll('•', ' ').replaceAll(RegExp(r'\s+'), ' '),
      service.nome,
      decision.label,
      if (destination?.isNotEmpty == true) destination!,
    ];
    return details.join(', ');
  }
}

class _PresenceSummaryHeader extends StatelessWidget {
  const _PresenceSummaryHeader({required this.title, required this.summary});

  final String title;
  final _ServicePresenceSummary summary;

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _ServiceIcon(
          color: colorScheme.primary,
          icon: Icons.fact_check_rounded,
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                title,
                style: Theme.of(context).textTheme.titleLarge?.copyWith(
                  color: colorScheme.onSurface,
                  fontSize: 26,
                  height: 1.05,
                  fontWeight: FontWeight.w900,
                ),
              ),
              const SizedBox(height: 4),
              Text(
                summary.description,
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                  color: colorScheme.onSurfaceVariant,
                  height: 1.35,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

class _ServicePresenceTitleBlock extends StatelessWidget {
  const _ServicePresenceTitleBlock({
    required this.operationLabel,
    required this.serviceName,
  });

  final String operationLabel;
  final String serviceName;

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          operationLabel,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: Theme.of(context).textTheme.titleMedium?.copyWith(
            color: colorScheme.onSurface,
            fontWeight: FontWeight.w800,
          ),
        ),
        const SizedBox(height: 3),
        Text(
          serviceName,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: Theme.of(
            context,
          ).textTheme.bodyMedium?.copyWith(color: colorScheme.onSurfaceVariant),
        ),
      ],
    );
  }
}

class _ServiceStatusActions extends StatelessWidget {
  const _ServiceStatusActions({
    required this.label,
    required this.color,
    required this.clearKey,
    required this.onClear,
    required this.processing,
  });

  final String label;
  final Color color;
  final Key clearKey;
  final VoidCallback? onClear;
  final bool processing;

  @override
  Widget build(BuildContext context) {
    return Wrap(
      spacing: 6,
      runSpacing: 4,
      crossAxisAlignment: WrapCrossAlignment.center,
      children: [
        _StatusPill(
          label: label,
          foreground: color,
          background: color.withValues(alpha: 0.12),
        ),
        if (onClear != null)
          TextButton.icon(
            key: clearKey,
            onPressed: processing ? null : onClear,
            icon: const Icon(Icons.edit_rounded, size: 16),
            label: const Text('Alterar'),
            style: TextButton.styleFrom(
              minimumSize: const Size(0, 32),
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
              tapTargetSize: MaterialTapTargetSize.shrinkWrap,
              visualDensity: VisualDensity.compact,
              textStyle: Theme.of(
                context,
              ).textTheme.labelMedium?.copyWith(fontWeight: FontWeight.w800),
            ),
          ),
      ],
    );
  }
}

class _DecisionButton extends StatelessWidget {
  const _DecisionButton({
    super.key,
    required this.label,
    required this.icon,
    required this.selected,
    required this.color,
    required this.onPressed,
  });

  final String label;
  final IconData icon;
  final bool selected;
  final Color color;
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    return OutlinedButton.icon(
      onPressed: onPressed,
      style: OutlinedButton.styleFrom(
        foregroundColor: selected ? Colors.white : color,
        backgroundColor: selected ? color : color.withValues(alpha: 0.07),
        disabledForegroundColor:
            selected ? Colors.white : color.withValues(alpha: 0.42),
        disabledBackgroundColor:
            selected ? color : color.withValues(alpha: 0.04),
        side: BorderSide(color: color.withValues(alpha: selected ? 0 : 0.22)),
        minimumSize: const Size.fromHeight(44),
        padding: const EdgeInsets.symmetric(horizontal: 10),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
        textStyle: Theme.of(
          context,
        ).textTheme.labelLarge?.copyWith(fontWeight: FontWeight.w800),
      ),
      icon: Icon(icon, size: 18),
      label: Text(label, maxLines: 1, overflow: TextOverflow.ellipsis),
    );
  }
}

class _ServiceIcon extends StatelessWidget {
  const _ServiceIcon({required this.color, required this.icon});

  final Color color;
  final IconData icon;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 42,
      height: 42,
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.13),
        borderRadius: BorderRadius.circular(14),
      ),
      alignment: Alignment.center,
      child: Icon(icon, color: color, size: 22),
    );
  }
}

class _StatusPill extends StatelessWidget {
  const _StatusPill({
    required this.label,
    required this.foreground,
    required this.background,
  });

  final String label;
  final Color foreground;
  final Color background;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 5),
      decoration: BoxDecoration(
        color: background,
        borderRadius: BorderRadius.circular(999),
      ),
      child: Text(
        label,
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
        style: TextStyle(
          color: foreground,
          fontSize: 12,
          fontWeight: FontWeight.w800,
        ),
      ),
    );
  }
}

class _ServicePresenceDecision {
  const _ServicePresenceDecision({
    required this.label,
    required this.icon,
    required this.color,
  });

  final String label;
  final IconData icon;
  final Color color;

  factory _ServicePresenceDecision.from(
    PassengerAvailableServiceRecord service,
    ColorScheme colorScheme,
  ) {
    if (service.isConfirmed) {
      return _ServicePresenceDecision(
        label: 'Vou',
        icon: Icons.check_circle_rounded,
        color: Colors.green.shade600,
      );
    }
    if (service.isAbsent) {
      return _ServicePresenceDecision(
        label: 'Não vou',
        icon: Icons.cancel_rounded,
        color: Colors.red.shade500,
      );
    }
    return _ServicePresenceDecision(
      label: 'Pendente',
      icon: Icons.help_outline_rounded,
      color: colorScheme.primary,
    );
  }
}

class _ServicePresenceSummary {
  const _ServicePresenceSummary({
    required this.total,
    required this.confirmed,
    required this.absent,
    required this.pending,
  });

  final int total;
  final int confirmed;
  final int absent;
  final int pending;

  String get description =>
      pending == 0
          ? 'Tudo decidido para hoje.'
          : 'Toque em Vou ou Não vou em cada horário.';

  String get totalLabel => '$total ${total == 1 ? 'horário' : 'horários'}';
  String get confirmedLabel =>
      '$confirmed ${confirmed == 1 ? 'confirmado' : 'confirmados'}';
  String get absentLabel => '$absent ${absent == 1 ? 'ausência' : 'ausências'}';
  String get pendingLabel =>
      '$pending ${pending == 1 ? 'pendente' : 'pendentes'}';

  factory _ServicePresenceSummary.from(
    List<PassengerAvailableServiceRecord> services,
  ) {
    final confirmed = services.where((service) => service.isConfirmed).length;
    final absent = services.where((service) => service.isAbsent).length;
    return _ServicePresenceSummary(
      total: services.length,
      confirmed: confirmed,
      absent: absent,
      pending: services.length - confirmed - absent,
    );
  }
}
