import 'package:flutter/material.dart';

import '../../domain/trip_tracking_record.dart';
import '../trip_tracking_presenter.dart';
import 'passenger_trip_glass_sheet.dart';
import 'vanrides_glass_surface.dart';
import 'vanrides_map_tokens.dart';

class PassengerTripCollapsedHud extends StatelessWidget {
  const PassengerTripCollapsedHud({
    super.key,
    required this.trip,
    required this.onOpenTripDetails,
    required this.onToggleHud,
    this.hudToggleKey,
    this.detailsButtonKey,
  });

  final TripTrackingRecord trip;
  final VoidCallback onOpenTripDetails;
  final VoidCallback onToggleHud;
  final Key? hudToggleKey;
  final Key? detailsButtonKey;

  @override
  Widget build(BuildContext context) {
    final display = PassengerTripGlassDisplay.from(trip);

    return VanRidesGlassSurface.sheet(
      semanticLabel: 'Resumo compacto da viagem',
      padding: const EdgeInsets.fromLTRB(14, 10, 12, 10),
      borderRadius: 24,
      child: Row(
        children: [
          Expanded(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  display.primary,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.titleSmall?.copyWith(
                    color: VanRidesMapTokens.textPrimary,
                    fontWeight: FontWeight.w900,
                  ),
                ),
                const SizedBox(height: 3),
                Text(
                  display.vanDistanceLabel,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.labelMedium?.copyWith(
                    color: VanRidesMapTokens.blueTracking,
                    fontWeight: FontWeight.w800,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(width: 8),
          _CompactSignalPill(signal: display.signal),
          const SizedBox(width: 8),
          _CompactDetailsButton(
            buttonKey: detailsButtonKey,
            onPressed: onOpenTripDetails,
          ),
          const SizedBox(width: 4),
          _CompactHudToggleButton(
            buttonKey: hudToggleKey,
            onPressed: onToggleHud,
          ),
        ],
      ),
    );
  }
}

class _CompactSignalPill extends StatelessWidget {
  const _CompactSignalPill({required this.signal});

  final TripTrackingPresentation signal;

  @override
  Widget build(BuildContext context) {
    final label = passengerTripSignalDisplayLabel(signal.label, compact: true);

    return Semantics(
      label: signal.label,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 8),
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
                fontWeight: FontWeight.w800,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _CompactHudToggleButton extends StatelessWidget {
  const _CompactHudToggleButton({required this.onPressed, this.buttonKey});

  final VoidCallback onPressed;
  final Key? buttonKey;

  @override
  Widget build(BuildContext context) {
    return IconButton(
      key: buttonKey,
      tooltip: 'Expandir resumo',
      onPressed: onPressed,
      visualDensity: VisualDensity.compact,
      constraints: const BoxConstraints(minWidth: 34, minHeight: 34),
      padding: EdgeInsets.zero,
      icon: const Icon(
        Icons.keyboard_arrow_up_rounded,
        color: VanRidesMapTokens.textSecondary,
        size: 24,
      ),
    );
  }
}

class _CompactDetailsButton extends StatelessWidget {
  const _CompactDetailsButton({required this.onPressed, this.buttonKey});

  final VoidCallback onPressed;
  final Key? buttonKey;

  @override
  Widget build(BuildContext context) {
    return OutlinedButton.icon(
      key: buttonKey,
      onPressed: onPressed,
      style: OutlinedButton.styleFrom(
        foregroundColor: VanRidesMapTokens.textPrimary,
        backgroundColor: VanRidesMapTokens.surfaceRaised.withValues(
          alpha: 0.66,
        ),
        side: BorderSide(
          color: VanRidesMapTokens.border.withValues(alpha: 0.34),
        ),
        minimumSize: const Size(0, 38),
        padding: const EdgeInsets.symmetric(horizontal: 9),
        tapTargetSize: MaterialTapTargetSize.shrinkWrap,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(15)),
        textStyle: Theme.of(
          context,
        ).textTheme.labelMedium?.copyWith(fontWeight: FontWeight.w800),
      ),
      icon: const Icon(
        Icons.receipt_long_rounded,
        size: 16,
        color: VanRidesMapTokens.blueTracking,
      ),
      label: const Text('Detalhes', maxLines: 1, overflow: TextOverflow.fade),
    );
  }
}
