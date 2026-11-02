import 'package:flutter/material.dart';

import '../../domain/trip_tracking_record.dart';
import '../trip_tracking_presenter.dart';
import 'compact_trip_switcher.dart';
import 'passenger_trip_glass_actions.dart';
import 'passenger_trip_glass_sheet.dart';
import 'passenger_trip_option_helpers.dart';
import 'vanrides_glass_surface.dart';
import 'vanrides_map_tokens.dart';

class PassengerTripExpandedSheet extends StatelessWidget {
  const PassengerTripExpandedSheet({
    super.key,
    required this.trip,
    required this.dayTrips,
    required this.selectedTripId,
    required this.pendingPresenceCount,
    required this.onTripSelected,
    required this.onRefresh,
    required this.onOpenTripDetails,
    required this.onOpenPresence,
    required this.onToggleSheet,
    this.detailsButtonKey,
    this.presenceButtonKey,
    this.tripKeyBuilder,
  });

  final TripTrackingRecord trip;
  final List<TripTrackingRecord> dayTrips;
  final int? selectedTripId;
  final int pendingPresenceCount;
  final ValueChanged<TripTrackingRecord> onTripSelected;
  final VoidCallback onRefresh;
  final VoidCallback onOpenTripDetails;
  final VoidCallback onOpenPresence;
  final VoidCallback onToggleSheet;
  final Key? detailsButtonKey;
  final Key? presenceButtonKey;
  final PassengerTripKeyBuilder? tripKeyBuilder;

  @override
  Widget build(BuildContext context) {
    final display = PassengerTripGlassDisplay.from(trip);

    return VanRidesGlassSurface.sheet(
      semanticLabel: 'Detalhes da viagem expandidos',
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 18),
      borderRadius: 30,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _ExpandedHeader(display: display, onToggleSheet: onToggleSheet),
          if (dayTrips.length > 1) ...[
            const SizedBox(height: 14),
            CompactTripSwitcher(
              dayTrips: dayTrips,
              selectedTripId: selectedTripId,
              onTripSelected: onTripSelected,
              tripKeyBuilder: tripKeyBuilder,
            ),
          ],
          if (pendingPresenceCount > 0) ...[
            const SizedBox(height: 14),
            _ExpandedPresenceAction(
              buttonKey: presenceButtonKey,
              count: pendingPresenceCount,
              onTap: onOpenPresence,
            ),
          ],
          const SizedBox(height: 16),
          _StopDetail(display: display),
          const SizedBox(height: 14),
          _DriverDetail(display: display),
          const SizedBox(height: 14),
          PassengerTripGlassActions(
            detailsButtonKey: detailsButtonKey,
            presenceButtonKey: null,
            onRefresh: onRefresh,
            onOpenTripDetails: onOpenTripDetails,
            onOpenMap: () {},
            onOpenPresence: onOpenPresence,
          ),
        ],
      ),
    );
  }
}

class _ExpandedHeader extends StatelessWidget {
  const _ExpandedHeader({required this.display, required this.onToggleSheet});

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
                style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                  color: VanRidesMapTokens.textPrimary,
                  fontWeight: FontWeight.w900,
                  height: 1.02,
                ),
              ),
              const SizedBox(height: 7),
              Text(
                display.vanDistanceLabel,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: Theme.of(context).textTheme.titleSmall?.copyWith(
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
          tooltip: 'Reduzir detalhes da viagem',
          visualDensity: VisualDensity.compact,
          constraints: const BoxConstraints(minWidth: 34, minHeight: 34),
          padding: EdgeInsets.zero,
          onPressed: onToggleSheet,
          icon: const Icon(
            Icons.keyboard_arrow_down_rounded,
            color: VanRidesMapTokens.textSecondary,
            size: 24,
          ),
        ),
      ],
    );
  }
}

class _SignalPill extends StatelessWidget {
  const _SignalPill({required this.signal});

  final TripTrackingPresentation signal;

  @override
  Widget build(BuildContext context) {
    final label = passengerTripSignalDisplayLabel(signal.label, compact: false);

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

class _ExpandedPresenceAction extends StatelessWidget {
  const _ExpandedPresenceAction({
    required this.count,
    required this.onTap,
    this.buttonKey,
  });

  final int count;
  final VoidCallback onTap;
  final Key? buttonKey;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: double.infinity,
      child: OutlinedButton.icon(
        key: buttonKey,
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
          minimumSize: const Size.fromHeight(42),
          padding: const EdgeInsets.symmetric(horizontal: 12),
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

class _StopDetail extends StatelessWidget {
  const _StopDetail({required this.display});

  final PassengerTripGlassDisplay display;

  @override
  Widget build(BuildContext context) {
    return _DetailBlock(
      icon: Icons.person_pin_circle_rounded,
      label: 'Minha parada',
      title: display.myStopTitle,
      helper: '${display.stopHelper} • ${display.serviceName}',
      trailingLabel: display.operationLabel,
    );
  }
}

class _DriverDetail extends StatelessWidget {
  const _DriverDetail({required this.display});

  final PassengerTripGlassDisplay display;

  @override
  Widget build(BuildContext context) {
    return _DetailBlock(
      icon: Icons.directions_bus_filled_rounded,
      label: 'Van',
      title: display.serviceName,
      helper: display.driverName,
    );
  }
}

class _DetailBlock extends StatelessWidget {
  const _DetailBlock({
    required this.icon,
    required this.label,
    required this.title,
    required this.helper,
    this.trailingLabel = '',
  });

  final IconData icon;
  final String label;
  final String title;
  final String helper;
  final String trailingLabel;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: VanRidesMapTokens.surfaceRaised.withValues(alpha: 0.42),
        borderRadius: BorderRadius.circular(22),
        border: Border.all(
          color: VanRidesMapTokens.border.withValues(alpha: 0.28),
        ),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
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
            child: Icon(icon, color: VanRidesMapTokens.blueTracking, size: 22),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
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
                const SizedBox(height: 3),
                Text(
                  helper,
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
          if (trailingLabel.isNotEmpty) ...[
            const SizedBox(width: 8),
            _OperationPill(label: trailingLabel),
          ],
        ],
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
      child: Text(
        label,
        style: Theme.of(context).textTheme.labelLarge?.copyWith(
          color: VanRidesMapTokens.textPrimary,
          fontWeight: FontWeight.w900,
        ),
      ),
    );
  }
}

String _presenceLabel(int count) {
  return count == 1 ? '1 presença pendente' : '$count presenças pendentes';
}
