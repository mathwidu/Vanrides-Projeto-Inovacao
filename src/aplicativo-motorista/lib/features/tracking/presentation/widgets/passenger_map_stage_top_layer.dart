import 'package:flutter/material.dart';
import 'package:vanrides_frontend_dev/l10n/app_strings.dart';

import '../../domain/trip_tracking_record.dart';
import '../trip_live_map_state.dart';
import 'passenger_trip_option_helpers.dart';
import 'vanrides_map_tokens.dart';

class PassengerMapStageTopLayer extends StatelessWidget {
  const PassengerMapStageTopLayer({
    super.key,
    required this.dayTrips,
    required this.selectedTripId,
    required this.focusMode,
    required this.onTripSelected,
    required this.onRefresh,
    required this.onFocusJourney,
    required this.onFocusVan,
    required this.onFocusPassenger,
    this.agendaKey,
    this.tripKeyBuilder,
  });

  final List<TripTrackingRecord> dayTrips;
  final int? selectedTripId;
  final TripLiveMapFocusMode focusMode;
  final ValueChanged<TripTrackingRecord> onTripSelected;
  final VoidCallback onRefresh;
  final VoidCallback onFocusJourney;
  final VoidCallback onFocusVan;
  final VoidCallback onFocusPassenger;
  final Key? agendaKey;
  final PassengerTripKeyBuilder? tripKeyBuilder;

  @override
  Widget build(BuildContext context) {
    return Column(
      key: agendaKey,
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Expanded(
              child: _PassengerStageTripChips(
                dayTrips: dayTrips,
                selectedTripId: selectedTripId,
                onTripSelected: onTripSelected,
                tripKeyBuilder: tripKeyBuilder,
              ),
            ),
            const SizedBox(width: 10),
            _PassengerStageFocusCluster(
              focusMode: focusMode,
              onFocusJourney: onFocusJourney,
              onFocusVan: onFocusVan,
              onFocusPassenger: onFocusPassenger,
              onRefresh: onRefresh,
            ),
          ],
        ),
      ],
    );
  }
}

class _PassengerStageTripChips extends StatelessWidget {
  const _PassengerStageTripChips({
    required this.dayTrips,
    required this.selectedTripId,
    required this.onTripSelected,
    this.tripKeyBuilder,
  });

  final List<TripTrackingRecord> dayTrips;
  final int? selectedTripId;
  final ValueChanged<TripTrackingRecord> onTripSelected;
  final PassengerTripKeyBuilder? tripKeyBuilder;

  @override
  Widget build(BuildContext context) {
    final trips = dayTrips.isEmpty ? const <TripTrackingRecord>[] : dayTrips;
    if (trips.isEmpty) {
      return const SizedBox.shrink();
    }

    if (trips.length <= 2) {
      return Row(
        children: [
          for (var index = 0; index < trips.length; index++) ...[
            if (index > 0) const SizedBox(width: 8),
            Expanded(
              child: _StageTripChip(
                key: tripKeyBuilder?.call(trips[index].id),
                trip: trips[index],
                selected: trips[index].id == selectedTripId,
                onTap: () => onTripSelected(trips[index]),
              ),
            ),
          ],
        ],
      );
    }

    return Wrap(
      spacing: 8,
      runSpacing: 8,
      children: [
        for (final trip in trips)
          ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 210),
            child: _StageTripChip(
              key: tripKeyBuilder?.call(trip.id),
              trip: trip,
              selected: trip.id == selectedTripId,
              onTap: () => onTripSelected(trip),
            ),
          ),
      ],
    );
  }
}

class _StageTripChip extends StatelessWidget {
  const _StageTripChip({
    super.key,
    required this.trip,
    required this.selected,
    required this.onTap,
  });

  final TripTrackingRecord trip;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.transparent,
      child: InkWell(
        borderRadius: BorderRadius.circular(20),
        onTap: onTap,
        child: Semantics(
          label: passengerTripOptionLabel(trip),
          button: true,
          selected: selected,
          child: _StageGlassPill(
            icon: passengerTripStatusIcon(trip.status),
            label: passengerTripCompactOptionLabel(trip),
            selected: selected,
          ),
        ),
      ),
    );
  }
}

class _PassengerStageFocusCluster extends StatelessWidget {
  const _PassengerStageFocusCluster({
    required this.focusMode,
    required this.onFocusJourney,
    required this.onFocusVan,
    required this.onFocusPassenger,
    required this.onRefresh,
  });

  final TripLiveMapFocusMode focusMode;
  final VoidCallback onFocusJourney;
  final VoidCallback onFocusVan;
  final VoidCallback onFocusPassenger;
  final VoidCallback onRefresh;

  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        _StageFocusButton(
          icon: Icons.directions_bus_filled_rounded,
          label: 'Van',
          selected: focusMode == TripLiveMapFocusMode.vehicleOnly,
          onTap: onFocusVan,
        ),
        const SizedBox(height: 8),
        _StageFocusButton(
          icon: Icons.person_pin_circle_rounded,
          label: 'Voce',
          selected: focusMode == TripLiveMapFocusMode.passengerOnly,
          onTap: onFocusPassenger,
        ),
        const SizedBox(height: 8),
        _StageFocusButton(
          icon: Icons.refresh_rounded,
          label: '',
          tooltip: AppStrings.passageiro.atualizarStatus,
          selected: focusMode == TripLiveMapFocusMode.passengerJourney,
          onTap: () {
            onFocusJourney();
            onRefresh();
          },
        ),
      ],
    );
  }
}

class _StageFocusButton extends StatelessWidget {
  const _StageFocusButton({
    required this.icon,
    required this.label,
    required this.selected,
    required this.onTap,
    this.tooltip,
  });

  final IconData icon;
  final String label;
  final bool selected;
  final VoidCallback onTap;
  final String? tooltip;

  @override
  Widget build(BuildContext context) {
    final foreground =
        selected
            ? VanRidesMapTokens.backgroundDeep
            : VanRidesMapTokens.textPrimary;
    final background =
        selected
            ? VanRidesMapTokens.blueTracking
            : VanRidesMapTokens.surface.withValues(alpha: 0.62);
    final effectiveTooltip = tooltip ?? label;
    final content = Material(
      color: Colors.transparent,
      child: InkWell(
        borderRadius: BorderRadius.circular(18),
        onTap: onTap,
        child: Semantics(
          label: effectiveTooltip,
          button: true,
          selected: selected,
          child: Container(
            constraints: const BoxConstraints(minWidth: 52, minHeight: 48),
            padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 8),
            decoration: BoxDecoration(
              color: background,
              borderRadius: BorderRadius.circular(18),
              border: Border.all(
                color:
                    selected
                        ? VanRidesMapTokens.blueTracking
                        : VanRidesMapTokens.border.withValues(alpha: 0.38),
              ),
              boxShadow: const [
                BoxShadow(
                  color: Color(0x55070C12),
                  blurRadius: 18,
                  offset: Offset(0, 10),
                ),
              ],
            ),
            child: Icon(icon, color: foreground, size: 20),
          ),
        ),
      ),
    );

    return Tooltip(message: effectiveTooltip, child: content);
  }
}

class _StageGlassPill extends StatelessWidget {
  const _StageGlassPill({
    required this.icon,
    required this.label,
    this.selected = false,
  });

  final IconData icon;
  final String label;
  final bool selected;

  @override
  Widget build(BuildContext context) {
    final background =
        selected
            ? VanRidesMapTokens.blueTracking
            : VanRidesMapTokens.surface.withValues(alpha: 0.62);
    final foreground =
        selected
            ? VanRidesMapTokens.backgroundDeep
            : VanRidesMapTokens.textPrimary;
    return Container(
      constraints: const BoxConstraints(minHeight: 40),
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 9),
      decoration: BoxDecoration(
        color: background,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(
          color:
              selected
                  ? VanRidesMapTokens.blueTracking
                  : VanRidesMapTokens.border.withValues(alpha: 0.34),
        ),
        boxShadow: const [
          BoxShadow(
            color: Color(0x44070C12),
            blurRadius: 18,
            offset: Offset(0, 10),
          ),
        ],
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 16, color: foreground),
          const SizedBox(width: 7),
          Flexible(
            child: Text(
              label,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: Theme.of(context).textTheme.labelLarge?.copyWith(
                color: foreground,
                fontWeight: FontWeight.w900,
                letterSpacing: 0,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
