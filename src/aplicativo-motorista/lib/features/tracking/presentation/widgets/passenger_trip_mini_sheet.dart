import 'package:flutter/material.dart';

import '../../domain/trip_tracking_record.dart';
import '../trip_tracking_presenter.dart';
import 'passenger_trip_glass_sheet.dart';
import 'vanrides_glass_surface.dart';
import 'vanrides_map_tokens.dart';

class PassengerTripMiniSheet extends StatelessWidget {
  const PassengerTripMiniSheet({
    super.key,
    required this.trip,
    required this.pendingPresenceCount,
    required this.onToggleSheet,
  });

  final TripTrackingRecord trip;
  final int pendingPresenceCount;
  final VoidCallback onToggleSheet;

  @override
  Widget build(BuildContext context) {
    final display = PassengerTripGlassDisplay.from(trip);

    return VanRidesGlassSurface.sheet(
      semanticLabel: 'Resumo da viagem recolhido',
      padding: const EdgeInsets.fromLTRB(14, 10, 8, 10),
      borderRadius: 24,
      child: Row(
        children: [
          Expanded(
            child: Text(
              display.primary,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: Theme.of(context).textTheme.titleSmall?.copyWith(
                color: VanRidesMapTokens.textPrimary,
                fontWeight: FontWeight.w900,
              ),
            ),
          ),
          const SizedBox(width: 8),
          _MiniSignalPill(signal: display.signal),
          if (pendingPresenceCount > 0) ...[
            const SizedBox(width: 8),
            _MiniPresenceBadge(count: pendingPresenceCount),
          ],
          const SizedBox(width: 2),
          IconButton(
            key: const Key('passenger.trip.sheet.chevron'),
            tooltip: 'Expandir resumo',
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
      ),
    );
  }
}

class _MiniSignalPill extends StatelessWidget {
  const _MiniSignalPill({required this.signal});

  final TripTrackingPresentation signal;

  @override
  Widget build(BuildContext context) {
    final label = passengerTripSignalDisplayLabel(signal.label, compact: true);

    return Semantics(
      label: signal.label,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 7),
        decoration: BoxDecoration(
          color: signal.color.withValues(alpha: 0.14),
          borderRadius: BorderRadius.circular(15),
          border: Border.all(color: signal.color.withValues(alpha: 0.30)),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(signal.icon, color: signal.color, size: 15),
            const SizedBox(width: 5),
            Text(
              label,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: Theme.of(context).textTheme.labelSmall?.copyWith(
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

class _MiniPresenceBadge extends StatelessWidget {
  const _MiniPresenceBadge({required this.count});

  final int count;

  @override
  Widget build(BuildContext context) {
    final label = _presenceLabel(count);
    return Semantics(
      label: label,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 7),
        decoration: BoxDecoration(
          color: VanRidesMapTokens.amberWarning.withValues(alpha: 0.14),
          borderRadius: BorderRadius.circular(999),
          border: Border.all(
            color: VanRidesMapTokens.amberWarning.withValues(alpha: 0.30),
          ),
        ),
        child: Text(
          '$count',
          style: Theme.of(context).textTheme.labelMedium?.copyWith(
            color: VanRidesMapTokens.textPrimary,
            fontWeight: FontWeight.w900,
          ),
        ),
      ),
    );
  }
}

String _presenceLabel(int count) {
  return count == 1 ? '1 presença pendente' : '$count presenças pendentes';
}
