import 'package:flutter/material.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/domain/driver_trip_record.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/presentation/widgets/driver_home_base_widgets.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/map_first_v2_components.dart';
import 'package:vanrides_frontend_dev/l10n/app_strings.dart';

class DriverHomePlanningTopBar extends StatelessWidget {
  const DriverHomePlanningTopBar({
    super.key,
    required this.trip,
    required this.dateLabel,
    required this.refreshKey,
    required this.onRefresh,
  });

  final DriverTripRecord trip;
  final String dateLabel;
  final Key refreshKey;
  final VoidCallback onRefresh;

  @override
  Widget build(BuildContext context) {
    final totalMinutes = trip.paradas.fold<int>(
      0,
      (sum, stop) => sum + (stop.etaMinutos ?? 0),
    );
    final tipoOperacaoLabel = trip.tipoOperacaoLabel;
    return Row(
      children: [
        Expanded(
          child: MapFirstSurfaceCard(
            padding: const EdgeInsets.fromLTRB(18, 16, 18, 16),
            color: Colors.white.withValues(alpha: 0.92),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Wrap(
                  spacing: 8,
                  runSpacing: 6,
                  crossAxisAlignment: WrapCrossAlignment.center,
                  children: [
                    Text(
                      trip.nomeOperacional,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.titleLarge?.copyWith(
                        color: MapFirstPalette.textStrong,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    if (tipoOperacaoLabel.isNotEmpty)
                      DriverOperationTypeBadge(label: tipoOperacaoLabel),
                  ],
                ),
                const SizedBox(height: 6),
                Text(
                  dateLabel,
                  style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                    color: MapFirstPalette.textMuted,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ],
            ),
          ),
        ),
        const SizedBox(width: 10),
        MapFirstGlassChip(
          icon: Icons.people_alt_outlined,
          label: '${trip.totalParadas}',
          compact: true,
        ),
        const SizedBox(width: 8),
        MapFirstGlassChip(
          icon: Icons.schedule_outlined,
          label: AppStrings.motorista.metricMinutes(totalMinutes),
          compact: true,
        ),
        const SizedBox(width: 8),
        MapFirstIconButton(
          key: refreshKey,
          icon: Icons.refresh_rounded,
          tooltip: AppStrings.motorista.atualizar,
          onPressed: onRefresh,
        ),
      ],
    );
  }
}

class DriverHomeActiveTopBar extends StatelessWidget {
  const DriverHomeActiveTopBar({
    super.key,
    required this.remainingStops,
    required this.refreshKey,
    required this.onRefresh,
  });

  final int remainingStops;
  final Key refreshKey;
  final VoidCallback onRefresh;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        MapFirstGlassChip(
          icon: Icons.directions_bus_filled_rounded,
          label: AppStrings.motorista.emRota,
          color: MapFirstPalette.green,
        ),
        const SizedBox(width: 8),
        Expanded(
          child: Align(
            alignment: Alignment.centerRight,
            child: MapFirstGlassChip(
              icon: Icons.flag_outlined,
              label: AppStrings.motorista.remainingStops(remainingStops),
              compact: true,
            ),
          ),
        ),
        const SizedBox(width: 8),
        MapFirstIconButton(
          key: refreshKey,
          icon: Icons.refresh_rounded,
          tooltip: AppStrings.motorista.atualizar,
          onPressed: onRefresh,
        ),
      ],
    );
  }
}
