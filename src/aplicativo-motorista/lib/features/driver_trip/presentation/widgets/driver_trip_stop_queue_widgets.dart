import 'package:flutter/material.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/domain/driver_trip_record.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/presentation/widgets/driver_trip_stop_widgets.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/map_first_v2_components.dart';
import 'package:vanrides_frontend_dev/l10n/app_strings.dart';

class DriverStopsTab extends StatelessWidget {
  const DriverStopsTab({
    super.key,
    this.isLoading = false,
    this.errorMessage,
    this.trip,
    this.onRefresh,
  });

  final bool isLoading;
  final String? errorMessage;
  final DriverTripRecord? trip;
  final VoidCallback? onRefresh;

  @override
  Widget build(BuildContext context) {
    final trip = this.trip;
    if (isLoading || errorMessage != null || trip == null) {
      return SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(24, 24, 24, 120),
          child: MapFirstStateCard(
            icon: Icons.list_alt_outlined,
            title: AppStrings.motorista.semFilaParaMostrar,
            message:
                errorMessage ??
                AppStrings.motorista.semFilaParaMostrarDescricao,
            actionLabel:
                errorMessage != null ? AppStrings.motorista.atualizar : null,
            onAction: errorMessage != null ? onRefresh : null,
          ),
        ),
      );
    }

    final totalMinutes = trip.paradas.fold<int>(
      0,
      (sum, stop) => sum + (stop.etaMinutos ?? 0),
    );

    return SafeArea(
      child: ListView(
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 120),
        children: [
          Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      AppStrings.motorista.filaDeParadas,
                      style: Theme.of(context).textTheme.titleLarge?.copyWith(
                        color: MapFirstPalette.textStrong,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      trip.nomeOperacional,
                      style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                        color: MapFirstPalette.textMuted,
                      ),
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
                label: AppStrings.motorista.metricMinutes(totalMinutes),
                compact: true,
              ),
            ],
          ),
          const SizedBox(height: 20),
          DriverStopList(stops: trip.paradas, routeName: trip.nomeOperacional),
        ],
      ),
    );
  }
}

class DriverStopList extends StatelessWidget {
  const DriverStopList({
    super.key,
    required this.stops,
    required this.routeName,
  });

  final List<DriverTripStopRecord> stops;
  final String routeName;

  @override
  Widget build(BuildContext context) {
    if (stops.isEmpty) {
      return MapFirstStateCard(
        icon: Icons.list_alt_outlined,
        title: AppStrings.motorista.semParadasOperacionais,
        message: AppStrings.motorista.semParadasOperacionaisDescricao,
      );
    }

    final nextStops = stops.where((stop) => stop.proximaParada).toList();
    final pendingStops =
        stops
            .where(
              (stop) =>
                  !stop.proximaParada &&
                  _isPendingStop(stop) &&
                  stop.confirmadoPresenca,
            )
            .toList();
    final presencePendingStops =
        stops
            .where(
              (stop) =>
                  !stop.proximaParada &&
                  _isPendingStop(stop) &&
                  !stop.confirmadoPresenca,
            )
            .toList();
    final resolvedStops = stops.where(_isResolvedStop).toList();

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        DriverStopSection(
          title: AppStrings.motorista.proximaParada,
          description: AppStrings.motorista.proximaParadaDescricao,
          stops: nextStops,
          routeName: routeName,
          highlighted: true,
        ),
        DriverStopSection(
          title: AppStrings.motorista.pendentes,
          description: AppStrings.motorista.pendentesOperacionaisDescricao,
          stops: pendingStops,
          routeName: routeName,
        ),
        DriverStopSection(
          title: AppStrings.motorista.presencaAConfirmar,
          description: AppStrings.motorista.presencaAConfirmarDescricao,
          stops: presencePendingStops,
          routeName: routeName,
        ),
        DriverStopSection(
          title: AppStrings.motorista.resolvidas,
          description: AppStrings.motorista.resolvidasDescricao,
          stops: resolvedStops,
          routeName: routeName,
        ),
      ],
    );
  }
}

class DriverStopSection extends StatelessWidget {
  const DriverStopSection({
    super.key,
    required this.title,
    required this.description,
    required this.stops,
    required this.routeName,
    this.highlighted = false,
  });

  final String title;
  final String description;
  final List<DriverTripStopRecord> stops;
  final String routeName;
  final bool highlighted;

  @override
  Widget build(BuildContext context) {
    if (stops.isEmpty) {
      return const SizedBox.shrink();
    }

    return Padding(
      padding: const EdgeInsets.only(bottom: 18),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  title,
                  style: Theme.of(context).textTheme.titleMedium?.copyWith(
                    color:
                        highlighted
                            ? MapFirstPalette.blue
                            : MapFirstPalette.textStrong,
                    fontWeight: FontWeight.w900,
                  ),
                ),
              ),
              MapFirstGlassChip(
                icon: Icons.format_list_numbered_rounded,
                label: '${stops.length}',
                compact: true,
              ),
            ],
          ),
          const SizedBox(height: 4),
          Text(
            description,
            style: Theme.of(context).textTheme.bodySmall?.copyWith(
              color: MapFirstPalette.textMuted,
              fontWeight: FontWeight.w600,
            ),
          ),
          const SizedBox(height: 10),
          for (var index = 0; index < stops.length; index++) ...[
            DriverStopCard(
              stop: stops[index],
              routeName: routeName,
              highlighted: highlighted,
            ),
            if (index != stops.length - 1) const SizedBox(height: 12),
          ],
        ],
      ),
    );
  }
}

bool _isPendingStop(DriverTripStopRecord stop) =>
    stop.status == TripTrackingStopStatus.pending ||
    stop.status == TripTrackingStopStatus.unknown;

bool _isResolvedStop(DriverTripStopRecord stop) =>
    stop.status == TripTrackingStopStatus.completed ||
    stop.status == TripTrackingStopStatus.skipped ||
    stop.status == TripTrackingStopStatus.noShow;
