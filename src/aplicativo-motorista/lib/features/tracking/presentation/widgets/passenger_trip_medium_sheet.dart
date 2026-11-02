import 'package:flutter/material.dart';

import '../../domain/trip_tracking_record.dart';
import '../trip_tracking_presenter.dart';
import 'passenger_trip_glass_sheet.dart';
import 'vanrides_glass_surface.dart';
import 'vanrides_map_tokens.dart';

class PassengerTripMediumSheet extends StatelessWidget {
  const PassengerTripMediumSheet({
    super.key,
    required this.trip,
    required this.pendingPresenceCount,
    required this.onOpenPresence,
    required this.onToggleSheet,
  });

  final TripTrackingRecord trip;
  final int pendingPresenceCount;
  final VoidCallback onOpenPresence;
  final VoidCallback onToggleSheet;

  @override
  Widget build(BuildContext context) {
    final display = PassengerTripGlassDisplay.from(trip);

    return VanRidesGlassSurface.sheet(
      semanticLabel: 'Resumo da viagem',
      padding: const EdgeInsets.fromLTRB(16, 12, 12, 14),
      borderRadius: 28,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _MediumStatusRow(display: display, onToggleSheet: onToggleSheet),
          const SizedBox(height: 12),
          _MediumStopRow(display: display),
          if (pendingPresenceCount > 0) ...[
            const SizedBox(height: 12),
            _PresenceInlineAction(
              count: pendingPresenceCount,
              onTap: onOpenPresence,
            ),
          ],
        ],
      ),
    );
  }
}

class _MediumStatusRow extends StatelessWidget {
  const _MediumStatusRow({required this.display, required this.onToggleSheet});

  final PassengerTripGlassDisplay display;
  final VoidCallback onToggleSheet;

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                display.primary,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: Theme.of(context).textTheme.titleLarge?.copyWith(
                  color: VanRidesMapTokens.textPrimary,
                  fontWeight: FontWeight.w900,
                  height: 1.03,
                ),
              ),
              const SizedBox(height: 5),
              Text(
                display.vanDistanceLabel,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: Theme.of(context).textTheme.labelLarge?.copyWith(
                  color: VanRidesMapTokens.blueTracking,
                  fontWeight: FontWeight.w900,
                ),
              ),
            ],
          ),
        ),
        const SizedBox(width: 10),
        _SignalPill(signal: display.signal),
        IconButton(
          key: const Key('passenger.trip.sheet.chevron'),
          tooltip: 'Abrir detalhes da viagem',
          visualDensity: VisualDensity.compact,
          constraints: const BoxConstraints(minWidth: 34, minHeight: 34),
          padding: EdgeInsets.zero,
          onPressed: onToggleSheet,
          icon: const Icon(
            Icons.keyboard_arrow_up_rounded,
            color: VanRidesMapTokens.textSecondary,
            size: 24,
          ),
        ),
      ],
    );
  }
}

class _MediumStopRow extends StatelessWidget {
  const _MediumStopRow({required this.display});

  final PassengerTripGlassDisplay display;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Container(
          width: 42,
          height: 42,
          decoration: BoxDecoration(
            color: VanRidesMapTokens.blueTracking.withValues(alpha: 0.14),
            borderRadius: BorderRadius.circular(16),
            border: Border.all(
              color: VanRidesMapTokens.blueTracking.withValues(alpha: 0.30),
            ),
          ),
          child: const Icon(
            Icons.person_pin_circle_rounded,
            color: VanRidesMapTokens.blueTracking,
            size: 22,
          ),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                'Minha parada',
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: Theme.of(context).textTheme.labelLarge?.copyWith(
                  color: VanRidesMapTokens.blueTracking,
                  fontWeight: FontWeight.w900,
                ),
              ),
              const SizedBox(height: 3),
              Text(
                display.myStopTitle,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: Theme.of(context).textTheme.titleSmall?.copyWith(
                  color: VanRidesMapTokens.textPrimary,
                  fontWeight: FontWeight.w900,
                ),
              ),
              const SizedBox(height: 2),
              Text(
                '${display.stopHelper} • ${display.serviceName} • ${display.driverName}',
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: Theme.of(context).textTheme.bodySmall?.copyWith(
                  color: VanRidesMapTokens.textSecondary,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ],
          ),
        ),
        if (display.operationLabel.isNotEmpty) ...[
          const SizedBox(width: 8),
          _OperationPill(label: display.operationLabel),
        ],
      ],
    );
  }
}

class _SignalPill extends StatelessWidget {
  const _SignalPill({required this.signal});

  final TripTrackingPresentation signal;

  @override
  Widget build(BuildContext context) {
    final label = passengerTripSignalDisplayLabel(signal.label, compact: true);

    return Semantics(
      label: signal.label,
      child: Container(
        constraints: const BoxConstraints(minHeight: 34),
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
        decoration: BoxDecoration(
          color: signal.color.withValues(alpha: 0.14),
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: signal.color.withValues(alpha: 0.32)),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(signal.icon, color: signal.color, size: 16),
            const SizedBox(width: 6),
            Text(
              label,
              style: Theme.of(context).textTheme.labelMedium?.copyWith(
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

class _OperationPill extends StatelessWidget {
  const _OperationPill({required this.label});

  final String label;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
      decoration: BoxDecoration(
        color: VanRidesMapTokens.surfaceRaised.withValues(alpha: 0.66),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: VanRidesMapTokens.border.withValues(alpha: 0.34),
        ),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(
            Icons.route_rounded,
            color: VanRidesMapTokens.textSecondary,
            size: 16,
          ),
          const SizedBox(width: 6),
          Text(
            label,
            style: Theme.of(context).textTheme.labelLarge?.copyWith(
              color: VanRidesMapTokens.textPrimary,
              fontWeight: FontWeight.w900,
            ),
          ),
        ],
      ),
    );
  }
}

class _PresenceInlineAction extends StatelessWidget {
  const _PresenceInlineAction({required this.count, required this.onTap});

  final int count;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: double.infinity,
      child: OutlinedButton.icon(
        key: const Key('passenger.trip.sheet.presenceInline'),
        onPressed: onTap,
        icon: const Icon(Icons.fact_check_rounded, size: 18),
        label: Text(_presenceLabel(count)),
        style: OutlinedButton.styleFrom(
          foregroundColor: VanRidesMapTokens.textPrimary,
          backgroundColor: VanRidesMapTokens.amberWarning.withValues(
            alpha: 0.10,
          ),
          side: BorderSide(
            color: VanRidesMapTokens.amberWarning.withValues(alpha: 0.30),
          ),
          minimumSize: const Size.fromHeight(40),
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 9),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(18),
          ),
          textStyle: Theme.of(
            context,
          ).textTheme.labelLarge?.copyWith(fontWeight: FontWeight.w900),
        ),
      ),
    );
  }
}

String _presenceLabel(int count) {
  return count == 1 ? '1 presença pendente' : '$count presenças pendentes';
}
