part of 'passenger_trip_details_screen.dart';

class _DetailsStateScaffold extends StatelessWidget {
  const _DetailsStateScaffold({required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: VanRidesMapTokens.backgroundDeep,
      body: SafeArea(
        child: Padding(padding: const EdgeInsets.all(18), child: child),
      ),
    );
  }
}

class _DetailsHeader extends StatelessWidget {
  const _DetailsHeader({required this.trip});

  final TripTrackingRecord trip;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        IconButton.filledTonal(
          tooltip: AppStrings.passageiro.voltar,
          onPressed: () => Navigator.of(context).pop(),
          icon: const Icon(Icons.arrow_back_rounded),
          style: IconButton.styleFrom(
            backgroundColor: VanRidesMapTokens.surfaceRaised.withValues(
              alpha: 0.64,
            ),
            foregroundColor: VanRidesMapTokens.textPrimary,
          ),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                AppStrings.passageiro.detalhesDaViagem,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: Theme.of(context).textTheme.titleLarge?.copyWith(
                  color: VanRidesMapTokens.textPrimary,
                  fontWeight: FontWeight.w900,
                ),
              ),
              const SizedBox(height: 2),
              Text(
                _operationSummary(trip),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                  color: VanRidesMapTokens.blueTracking,
                  fontWeight: FontWeight.w900,
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

class _LiquidPanel extends StatelessWidget {
  const _LiquidPanel({required this.child, required this.semanticLabel});

  final Widget child;
  final String semanticLabel;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      label: semanticLabel,
      container: true,
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: VanRidesMapTokens.surface.withValues(alpha: 0.72),
          borderRadius: BorderRadius.circular(28),
          border: Border.all(
            color: VanRidesMapTokens.border.withValues(alpha: 0.34),
          ),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.24),
              blurRadius: 28,
              offset: const Offset(0, 18),
            ),
          ],
        ),
        child: Padding(padding: const EdgeInsets.all(18), child: child),
      ),
    );
  }
}

class _SectionTitle extends StatelessWidget {
  const _SectionTitle(this.label);

  final String label;

  @override
  Widget build(BuildContext context) {
    return Text(
      label,
      style: Theme.of(context).textTheme.titleMedium?.copyWith(
        color: VanRidesMapTokens.textPrimary,
        fontWeight: FontWeight.w900,
      ),
    );
  }
}

class _SummaryGrid extends StatelessWidget {
  const _SummaryGrid({required this.items});

  final List<_SummaryItem> items;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final columns = constraints.maxWidth >= 520 ? 4 : 2;
        return Wrap(
          spacing: 10,
          runSpacing: 10,
          children: items
              .map(
                (item) => SizedBox(
                  width: (constraints.maxWidth - (columns - 1) * 10) / columns,
                  child: _SummaryTile(item: item),
                ),
              )
              .toList(growable: false),
        );
      },
    );
  }
}

class _SummaryItem {
  const _SummaryItem({
    required this.icon,
    required this.label,
    required this.value,
  });

  final IconData icon;
  final String label;
  final String value;
}

class _SummaryTile extends StatelessWidget {
  const _SummaryTile({required this.item});

  final _SummaryItem item;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: VanRidesMapTokens.surfaceRaised.withValues(alpha: 0.48),
        borderRadius: BorderRadius.circular(18),
        border: Border.all(
          color: VanRidesMapTokens.border.withValues(alpha: 0.28),
        ),
      ),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(12, 12, 12, 11),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(item.icon, color: VanRidesMapTokens.blueTracking, size: 20),
            const SizedBox(height: 10),
            Text(
              item.label,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: Theme.of(context).textTheme.labelMedium?.copyWith(
                color: VanRidesMapTokens.textMuted,
                fontWeight: FontWeight.w800,
              ),
            ),
            const SizedBox(height: 3),
            Text(
              item.value,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: Theme.of(context).textTheme.titleSmall?.copyWith(
                color: VanRidesMapTokens.textPrimary,
                fontWeight: FontWeight.w900,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _DetailRow extends StatelessWidget {
  const _DetailRow({
    required this.icon,
    required this.label,
    required this.title,
    required this.subtitle,
    this.trailing = '',
  });

  final IconData icon;
  final String label;
  final String title;
  final String subtitle;
  final String trailing;

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Container(
          width: 44,
          height: 44,
          decoration: BoxDecoration(
            color: VanRidesMapTokens.blueTracking.withValues(alpha: 0.13),
            borderRadius: BorderRadius.circular(16),
            border: Border.all(
              color: VanRidesMapTokens.blueTracking.withValues(alpha: 0.30),
            ),
          ),
          child: Icon(icon, color: VanRidesMapTokens.blueTracking, size: 23),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                label,
                style: Theme.of(context).textTheme.labelLarge?.copyWith(
                  color: VanRidesMapTokens.blueTracking,
                  fontWeight: FontWeight.w900,
                ),
              ),
              const SizedBox(height: 4),
              Text(
                title,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: Theme.of(context).textTheme.titleMedium?.copyWith(
                  color: VanRidesMapTokens.textPrimary,
                  fontWeight: FontWeight.w900,
                ),
              ),
              const SizedBox(height: 2),
              Text(
                subtitle,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                  color: VanRidesMapTokens.textSecondary,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ],
          ),
        ),
        if (trailing.isNotEmpty) ...[
          const SizedBox(width: 10),
          _Pill(trailing),
        ],
      ],
    );
  }
}

class _Pill extends StatelessWidget {
  const _Pill(this.label);

  final String label;

  @override
  Widget build(BuildContext context) {
    return Container(
      constraints: const BoxConstraints(maxWidth: 86),
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
      decoration: BoxDecoration(
        color: VanRidesMapTokens.surfaceRaised.withValues(alpha: 0.62),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: VanRidesMapTokens.border.withValues(alpha: 0.34),
        ),
      ),
      child: Text(
        label,
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
        style: Theme.of(context).textTheme.labelLarge?.copyWith(
          color: VanRidesMapTokens.textPrimary,
          fontWeight: FontWeight.w900,
        ),
      ),
    );
  }
}

class _StopGroup extends StatelessWidget {
  const _StopGroup({
    required this.title,
    required this.emptyText,
    required this.stops,
    this.highlighted = false,
  });

  final String title;
  final String emptyText;
  final List<TripRouteWaypointRecord> stops;
  final bool highlighted;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          title,
          style: Theme.of(context).textTheme.labelLarge?.copyWith(
            color: VanRidesMapTokens.blueTracking,
            fontWeight: FontWeight.w900,
          ),
        ),
        const SizedBox(height: 8),
        if (stops.isEmpty)
          _EmptyLine(
            icon: Icons.check_circle_rounded,
            title: emptyText,
            subtitle: '',
          )
        else
          ...stops.map(
            (stop) => Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: _StopLine(stop: stop, highlighted: highlighted),
            ),
          ),
      ],
    );
  }
}

class _StopLine extends StatelessWidget {
  const _StopLine({required this.stop, required this.highlighted});

  final TripRouteWaypointRecord stop;
  final bool highlighted;

  @override
  Widget build(BuildContext context) {
    final order = stop.ordem ?? 0;
    return DecoratedBox(
      decoration: BoxDecoration(
        color:
            highlighted
                ? VanRidesMapTokens.blueTracking.withValues(alpha: 0.16)
                : VanRidesMapTokens.surfaceRaised.withValues(alpha: 0.42),
        borderRadius: BorderRadius.circular(18),
        border: Border.all(
          color:
              highlighted
                  ? VanRidesMapTokens.blueTracking.withValues(alpha: 0.34)
                  : VanRidesMapTokens.border.withValues(alpha: 0.24),
        ),
      ),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(12, 10, 12, 10),
        child: Row(
          children: [
            Container(
              width: 30,
              height: 30,
              decoration: BoxDecoration(
                color: VanRidesMapTokens.blueTracking.withValues(alpha: 0.14),
                borderRadius: BorderRadius.circular(12),
              ),
              alignment: Alignment.center,
              child: Text(
                '$order',
                style: Theme.of(context).textTheme.labelLarge?.copyWith(
                  color: VanRidesMapTokens.textPrimary,
                  fontWeight: FontWeight.w900,
                ),
              ),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: Text(
                stop.nome,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: Theme.of(context).textTheme.titleSmall?.copyWith(
                  color: VanRidesMapTokens.textPrimary,
                  fontWeight: FontWeight.w900,
                ),
              ),
            ),
            if (stop.proximaParada) ...[
              const SizedBox(width: 8),
              const _Pill('Proxima'),
            ],
          ],
        ),
      ),
    );
  }
}

class _EmptyLine extends StatelessWidget {
  const _EmptyLine({
    required this.icon,
    required this.title,
    required this.subtitle,
  });

  final IconData icon;
  final String title;
  final String subtitle;

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Icon(icon, color: VanRidesMapTokens.textSecondary, size: 20),
        const SizedBox(width: 10),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                title,
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                  color: VanRidesMapTokens.textSecondary,
                  fontWeight: FontWeight.w800,
                ),
              ),
              if (subtitle.isNotEmpty) ...[
                const SizedBox(height: 2),
                Text(
                  subtitle,
                  style: Theme.of(context).textTheme.bodySmall?.copyWith(
                    color: VanRidesMapTokens.textMuted,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ],
            ],
          ),
        ),
      ],
    );
  }
}

class _StatePanel extends StatelessWidget {
  const _StatePanel({
    required this.icon,
    required this.title,
    required this.message,
  });

  final IconData icon;
  final String title;
  final String message;

  @override
  Widget build(BuildContext context) {
    return _LiquidPanel(
      semanticLabel: title,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, color: VanRidesMapTokens.blueTracking, size: 28),
          const SizedBox(height: 12),
          Text(
            title,
            style: Theme.of(context).textTheme.titleLarge?.copyWith(
              color: VanRidesMapTokens.textPrimary,
              fontWeight: FontWeight.w900,
            ),
          ),
          const SizedBox(height: 6),
          Text(
            message,
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
              color: VanRidesMapTokens.textSecondary,
              fontWeight: FontWeight.w700,
            ),
          ),
        ],
      ),
    );
  }
}

List<TripRouteWaypointRecord> _stopsBefore(
  List<TripRouteWaypointRecord> stops,
  TripTrackingPassengerStopRecord? passengerStop,
) {
  final order = passengerStop?.ordem;
  if (order == null) {
    return const [];
  }
  return stops.where((item) => (item.ordem ?? 0) < order).toList();
}

List<TripRouteWaypointRecord> _stopsAfter(
  List<TripRouteWaypointRecord> stops,
  TripTrackingPassengerStopRecord? passengerStop,
) {
  final order = passengerStop?.ordem;
  if (order == null) {
    return const [];
  }
  return stops.where((item) => (item.ordem ?? 0) > order).toList();
}

String _operationSummary(TripTrackingRecord trip) {
  final time = trip.horarioPartida;
  final operation = trip.tipoOperacaoLabel;
  if (time != null && time.isNotEmpty && operation.isNotEmpty) {
    return '$time • $operation';
  }
  if (time != null && time.isNotEmpty) {
    return time;
  }
  if (operation.isNotEmpty) {
    return operation;
  }
  return 'Horário a confirmar';
}

String _tripStatusLabel(TripTrackingStatus status) {
  return switch (status) {
    TripTrackingStatus.planned => 'Planejada',
    TripTrackingStatus.inProgress => 'Em andamento',
    TripTrackingStatus.completed => 'Encerrada',
    TripTrackingStatus.canceled => 'Cancelada',
    TripTrackingStatus.unknown => 'Indisponível',
  };
}

String _presenceLabel(TripTrackingPassengerStopRecord? stop) {
  if (stop == null) {
    return 'Não publicada';
  }
  return stop.confirmadoPresenca ? 'Confirmada' : 'Pendente';
}

String _stopHelper(
  TripTrackingRecord trip,
  TripTrackingPassengerStopRecord stop,
) {
  if (stop.proximaParada) {
    return AppStrings.passageiro.suaParadaEProxima;
  }
  final stopsBefore = trip.rotaGeografica?.paradasAntesDoPassageiro ?? 0;
  if (stopsBefore <= 0) {
    return AppStrings.passageiro.voceEoProximoDaFila;
  }
  return AppStrings.passageiro.paradasAntesDeVoce(stopsBefore);
}
