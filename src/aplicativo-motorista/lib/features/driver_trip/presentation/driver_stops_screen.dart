import 'package:flutter/material.dart';

import '../../tracking/presentation/map_first_v2_components.dart';
import '../../../l10n/app_strings.dart';
import '../domain/driver_trip_record.dart';
import 'widgets/driver_trip_stop_queue_widgets.dart';

class DriverStopsScreen extends StatelessWidget {
  const DriverStopsScreen({super.key, required this.trip});

  final DriverTripRecord trip;

  @override
  Widget build(BuildContext context) {
    final totalMinutes = trip.paradas.fold<int>(
      0,
      (sum, stop) => sum + (stop.etaMinutos ?? 0),
    );

    return Scaffold(
      backgroundColor: MapFirstPalette.background,
      body: SafeArea(
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 16, 16, 12),
              child: Row(
                children: [
                  MapFirstIconButton(
                    icon: Icons.arrow_back_rounded,
                    tooltip: 'Voltar',
                    onPressed: () => Navigator.of(context).pop(),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          AppStrings.motorista.filaDeParadas,
                          style: Theme.of(
                            context,
                          ).textTheme.titleLarge?.copyWith(
                            color: MapFirstPalette.textStrong,
                            fontWeight: FontWeight.w800,
                          ),
                        ),
                        const SizedBox(height: 2),
                        Text(
                          trip.nomeOperacional,
                          style: Theme.of(context).textTheme.bodyMedium
                              ?.copyWith(color: MapFirstPalette.textMuted),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(width: 12),
                  MapFirstGlassChip(
                    icon: Icons.people_alt_outlined,
                    label: '${trip.totalParadas}',
                    compact: true,
                  ),
                  const SizedBox(width: 8),
                  MapFirstGlassChip(
                    icon: Icons.schedule_outlined,
                    label: '${totalMinutes}min',
                    compact: true,
                  ),
                  if (trip.horarioPartida != null) ...[
                    const SizedBox(width: 8),
                    MapFirstGlassChip(
                      icon: Icons.access_time_rounded,
                      label: trip.horarioPartida!,
                      compact: true,
                    ),
                  ],
                ],
              ),
            ),
            Expanded(
              child: ListView(
                padding: const EdgeInsets.fromLTRB(16, 8, 16, 28),
                children: [
                  DriverStopList(
                    stops: trip.paradas,
                    routeName: trip.nomeOperacional,
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
