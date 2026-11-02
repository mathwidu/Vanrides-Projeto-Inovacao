import 'package:flutter/material.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/domain/driver_trip_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/map_first_v2_components.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/widgets/vanrides_map_tokens.dart';
import 'package:vanrides_frontend_dev/l10n/app_strings.dart';

typedef DriverTripKeyBuilder = Key Function(int tripId);

class DriverHomeDayAgendaSelector extends StatelessWidget {
  const DriverHomeDayAgendaSelector({
    super.key,
    required this.agendaKey,
    required this.dayTrips,
    required this.selectedTripId,
    required this.tripKeyBuilder,
    required this.onSelectTrip,
    this.dark = false,
  });

  final Key agendaKey;
  final List<DriverTripRecord> dayTrips;
  final int? selectedTripId;
  final DriverTripKeyBuilder tripKeyBuilder;
  final ValueChanged<DriverTripRecord> onSelectTrip;
  final bool dark;

  @override
  Widget build(BuildContext context) {
    final primaryText =
        dark ? VanRidesMapTokens.textPrimary : MapFirstPalette.textStrong;
    final mutedText =
        dark ? VanRidesMapTokens.textMuted : MapFirstPalette.textMuted;
    final chipBackground =
        dark ? VanRidesMapTokens.surfaceRaised : Colors.white;
    final selectedColor =
        dark ? VanRidesMapTokens.blueTracking : MapFirstPalette.brandBlue;
    final unselectedIcon =
        dark ? VanRidesMapTokens.blueTracking : MapFirstPalette.brandBlue;
    final borderColor =
        dark
            ? VanRidesMapTokens.border.withValues(alpha: 0.42)
            : MapFirstPalette.border;

    return Column(
      key: agendaKey,
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Expanded(
              child: Text(
                AppStrings.motorista.agendaDeHoje,
                style: Theme.of(context).textTheme.labelLarge?.copyWith(
                  color: primaryText,
                  fontWeight: FontWeight.w800,
                ),
              ),
            ),
            Text(
              AppStrings.motorista.viagensNoDia(dayTrips.length),
              style: Theme.of(context).textTheme.bodySmall?.copyWith(
                color: mutedText,
                fontWeight: FontWeight.w700,
              ),
            ),
          ],
        ),
        const SizedBox(height: 8),
        SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          child: Row(
            children: [
              for (final trip in dayTrips) ...[
                ChoiceChip(
                  key: tripKeyBuilder(trip.id),
                  selected: trip.id == selectedTripId,
                  avatar: Icon(
                    _driverTripStatusIcon(trip.status),
                    size: 16,
                    color:
                        trip.id == selectedTripId
                            ? Colors.white
                            : unselectedIcon,
                  ),
                  label: Text(_driverTripOptionLabel(trip)),
                  labelStyle: Theme.of(context).textTheme.labelLarge?.copyWith(
                    color:
                        trip.id == selectedTripId ? Colors.white : primaryText,
                    fontWeight: FontWeight.w800,
                  ),
                  backgroundColor: chipBackground,
                  selectedColor: selectedColor,
                  side: BorderSide(
                    color:
                        trip.id == selectedTripId ? selectedColor : borderColor,
                  ),
                  showCheckmark: false,
                  onSelected: (_) => onSelectTrip(trip),
                ),
                const SizedBox(width: 8),
              ],
            ],
          ),
        ),
      ],
    );
  }
}

String _driverTripOptionLabel(DriverTripRecord trip) {
  final horario = trip.horarioPartida ?? '--:--';
  final tipo = trip.tipoOperacaoLabel;
  final nome = trip.nomeOperacional.trim();
  final suffix = tipo.isEmpty ? nome : '$tipo • $nome';
  return '$horario • $suffix';
}

IconData _driverTripStatusIcon(TripTrackingStatus status) {
  return switch (status) {
    TripTrackingStatus.inProgress => Icons.directions_bus_filled_rounded,
    TripTrackingStatus.completed => Icons.check_circle_outline_rounded,
    TripTrackingStatus.canceled => Icons.block_rounded,
    _ => Icons.schedule_outlined,
  };
}
