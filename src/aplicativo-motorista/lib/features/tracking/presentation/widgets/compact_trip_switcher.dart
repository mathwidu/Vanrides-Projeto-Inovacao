import 'package:flutter/material.dart';
import 'package:vanrides_frontend_dev/app/theme/vanrides_motion.dart';
import 'package:vanrides_frontend_dev/l10n/app_strings.dart';

import '../../domain/trip_tracking_record.dart';
import 'vanrides_map_tokens.dart';

typedef CompactTripKeyBuilder = Key Function(int tripId);

class CompactTripSwitcher extends StatelessWidget {
  const CompactTripSwitcher({
    super.key,
    required this.dayTrips,
    required this.selectedTripId,
    required this.onTripSelected,
    this.tripKeyBuilder,
    this.dark = true,
  });

  static const verticalListKey = ValueKey('passenger.trip-switcher.vertical');

  final List<TripTrackingRecord> dayTrips;
  final int? selectedTripId;
  final ValueChanged<TripTrackingRecord> onTripSelected;
  final CompactTripKeyBuilder? tripKeyBuilder;
  final bool dark;

  @override
  Widget build(BuildContext context) {
    if (dayTrips.length <= 1) {
      return const SizedBox.shrink();
    }

    if (dayTrips.length == 2) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const _TripCountLabel(count: 2),
          const SizedBox(height: 8),
          Row(
            children: [
              for (var index = 0; index < dayTrips.length; index++) ...[
                if (index > 0) const SizedBox(width: 8),
                Expanded(
                  child: _TripSwitchItem(trip: dayTrips[index], owner: this),
                ),
              ],
            ],
          ),
        ],
      );
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _TripCountLabel(count: dayTrips.length),
        const SizedBox(height: 8),
        Column(
          key: verticalListKey,
          children: [
            for (final trip in dayTrips) ...[
              _TripSwitchItem(trip: trip, owner: this, dense: true),
              if (trip != dayTrips.last) const SizedBox(height: 8),
            ],
          ],
        ),
      ],
    );
  }
}

class _TripCountLabel extends StatelessWidget {
  const _TripCountLabel({required this.count});

  final int count;

  @override
  Widget build(BuildContext context) {
    return Text(
      AppStrings.passageiro.viagensNoDia(count),
      style: Theme.of(context).textTheme.labelMedium?.copyWith(
        color: VanRidesMapTokens.textMuted,
        fontWeight: FontWeight.w800,
      ),
    );
  }
}

class _TripSwitchItem extends StatelessWidget {
  const _TripSwitchItem({
    required this.trip,
    required this.owner,
    this.dense = false,
  });

  final TripTrackingRecord trip;
  final CompactTripSwitcher owner;
  final bool dense;

  @override
  Widget build(BuildContext context) {
    final motion = VanRidesMotionPolicy.of(context);
    final selected = trip.id == owner.selectedTripId;
    final background =
        selected
            ? VanRidesMapTokens.blueTracking
            : VanRidesMapTokens.surfaceRaised.withValues(alpha: 0.64);
    final foreground = selected ? Colors.white : VanRidesMapTokens.textPrimary;
    final border =
        selected
            ? VanRidesMapTokens.blueTracking
            : VanRidesMapTokens.border.withValues(alpha: 0.36);

    return Material(
      color: Colors.transparent,
      child: InkWell(
        key: owner.tripKeyBuilder?.call(trip.id),
        borderRadius: BorderRadius.circular(dense ? 16 : 18),
        onTap: () => owner.onTripSelected(trip),
        child: AnimatedContainer(
          duration: motion.duration(VanRidesMotionTokens.buttonPress),
          curve: motion.curve(VanRidesMotionTokens.easeOutQuart),
          constraints: const BoxConstraints(minHeight: 44),
          padding: EdgeInsets.symmetric(
            horizontal: dense ? 12 : 14,
            vertical: dense ? 9 : 11,
          ),
          decoration: BoxDecoration(
            color: background,
            borderRadius: BorderRadius.circular(dense ? 16 : 18),
            border: Border.all(color: border),
          ),
          child: Row(
            children: [
              Icon(_tripIcon(trip.status), size: 17, color: foreground),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  compactTripLabel(trip, includeService: dense),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.labelLarge?.copyWith(
                    color: foreground,
                    fontWeight: FontWeight.w800,
                  ),
                ),
              ),
              if (dense) ...[
                const SizedBox(width: 8),
                _TripStatusDot(status: trip.status, selected: selected),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

class _TripStatusDot extends StatelessWidget {
  const _TripStatusDot({required this.status, required this.selected});

  final TripTrackingStatus status;
  final bool selected;

  @override
  Widget build(BuildContext context) {
    final color =
        selected
            ? Colors.white
            : switch (status) {
              TripTrackingStatus.inProgress => VanRidesMapTokens.greenAction,
              TripTrackingStatus.completed => VanRidesMapTokens.textMuted,
              TripTrackingStatus.canceled => VanRidesMapTokens.redDanger,
              _ => VanRidesMapTokens.amberWarning,
            };

    return Container(
      width: 9,
      height: 9,
      decoration: BoxDecoration(color: color, shape: BoxShape.circle),
    );
  }
}

String compactTripLabel(TripTrackingRecord trip, {bool includeService = true}) {
  final horario = trip.horarioPartida ?? '--:--';
  final tipo = trip.tipoOperacaoLabel;
  final nome = trip.nomeOperacional.trim();
  final suffix =
      includeService && nome.isNotEmpty
          ? tipo.isEmpty
              ? nome
              : '$tipo • $nome'
          : tipo;
  if (suffix.isEmpty) {
    return horario;
  }
  return '$horario • $suffix';
}

IconData _tripIcon(TripTrackingStatus status) {
  return switch (status) {
    TripTrackingStatus.inProgress => Icons.directions_bus_filled_rounded,
    TripTrackingStatus.completed => Icons.check_circle_outline_rounded,
    TripTrackingStatus.canceled => Icons.block_rounded,
    _ => Icons.schedule_outlined,
  };
}
