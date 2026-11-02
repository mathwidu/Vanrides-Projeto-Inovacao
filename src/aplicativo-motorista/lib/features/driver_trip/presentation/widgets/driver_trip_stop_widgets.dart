import 'package:flutter/material.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/domain/driver_trip_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/map_first_v2_components.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/trip_tracking_presenter.dart';
import 'package:vanrides_frontend_dev/l10n/app_strings.dart';

enum DriverStopFilter { all, pending, confirmed }

class DriverStopFilterBar extends StatelessWidget {
  const DriverStopFilterBar({
    super.key,
    required this.selected,
    required this.onSelected,
  });

  final DriverStopFilter selected;
  final ValueChanged<DriverStopFilter> onSelected;

  @override
  Widget build(BuildContext context) {
    return SegmentedButton<DriverStopFilter>(
      segments: [
        ButtonSegment<DriverStopFilter>(
          value: DriverStopFilter.all,
          label: Text(AppStrings.motorista.todos),
          icon: const Icon(Icons.format_list_bulleted),
        ),
        ButtonSegment<DriverStopFilter>(
          value: DriverStopFilter.pending,
          label: Text(AppStrings.motorista.pendentes),
          icon: const Icon(Icons.hourglass_empty_rounded),
        ),
        ButtonSegment<DriverStopFilter>(
          value: DriverStopFilter.confirmed,
          label: Text(AppStrings.motorista.confirmados),
          icon: const Icon(Icons.verified_outlined),
        ),
      ],
      selected: {DriverStopFilter.values[selected.index]},
      onSelectionChanged: (selection) {
        onSelected(selection.first);
      },
      style: ButtonStyle(
        backgroundColor: WidgetStateProperty.resolveWith((states) {
          if (states.contains(WidgetState.selected)) {
            return const Color(0xFF161B36);
          }
          return Colors.white;
        }),
        foregroundColor: WidgetStateProperty.resolveWith((states) {
          if (states.contains(WidgetState.selected)) {
            return Colors.white;
          }
          return const Color(0xFF161B36);
        }),
        side: const WidgetStatePropertyAll(
          BorderSide(color: Color(0xFF161B36), width: 1.2),
        ),
      ),
      showSelectedIcon: false,
    );
  }
}

class DriverStopCard extends StatelessWidget {
  const DriverStopCard({
    super.key,
    required this.stop,
    required this.routeName,
    this.highlighted = false,
  });

  final DriverTripStopRecord stop;
  final String routeName;
  final bool highlighted;

  @override
  Widget build(BuildContext context) {
    final statusVisual = _presentDriverStopVisual(stop, highlighted);
    final presenceColor =
        stop.confirmadoPresenca ? MapFirstPalette.green : MapFirstPalette.amber;

    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: MapFirstPalette.surfaceRaised.withValues(alpha: 0.96),
        borderRadius: BorderRadius.circular(24),
        border: Border.all(
          color:
              highlighted
                  ? MapFirstPalette.blue.withValues(alpha: 0.52)
                  : statusVisual.color.withValues(alpha: 0.18),
          width: highlighted ? 1.8 : 1,
        ),
        boxShadow: const [
          BoxShadow(
            color: Color(0x12161B36),
            blurRadius: 18,
            offset: Offset(0, 10),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              CircleAvatar(
                radius: 22,
                backgroundColor: statusVisual.color.withValues(alpha: 0.16),
                child: Icon(statusVisual.icon, color: statusVisual.color),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      stop.passageiroNome,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.titleLarge?.copyWith(
                        color: MapFirstPalette.textStrong,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      AppStrings.motorista.stopRouteCompactSummary(
                        stop.ordem,
                        routeName.length > 32
                            ? '${routeName.substring(0, 32)}...'
                            : routeName,
                      ),
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                        color: MapFirstPalette.textMuted,
                      ),
                    ),
                  ],
                ),
              ),
              if (stop.proximaParada)
                Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 12,
                    vertical: 8,
                  ),
                  decoration: BoxDecoration(
                    color: MapFirstPalette.blueSoft,
                    borderRadius: BorderRadius.circular(999),
                  ),
                  child: Text(
                    AppStrings.motorista.proxima,
                    style: Theme.of(context).textTheme.labelLarge?.copyWith(
                      color: MapFirstPalette.blue,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ),
            ],
          ),
          const SizedBox(height: 16),
          Wrap(
            spacing: 10,
            runSpacing: 10,
            children: [
              _StopMetaChip(
                icon: Icons.fact_check_outlined,
                label:
                    stop.confirmadoPresenca
                        ? AppStrings.motorista.presencaConfirmada
                        : AppStrings.motorista.presencaPendente,
                color: presenceColor,
              ),
              _StopMetaChip(
                icon: Icons.route_outlined,
                label: presentStopStatus(stop.status),
                color: statusVisual.color,
              ),
              _StopMetaChip(
                icon: Icons.schedule_outlined,
                label: presentEtaLabel(stop.etaMinutos),
                color: MapFirstPalette.blue,
              ),
              if (stop.menorDeIdade)
                _StopMetaChip(
                  icon: Icons.child_care_outlined,
                  label: AppStrings.motorista.menorDeIdade,
                  color: MapFirstPalette.red,
                ),
            ],
          ),
        ],
      ),
    );
  }
}

_DriverStopVisual _presentDriverStopVisual(
  DriverTripStopRecord stop,
  bool highlighted,
) {
  if (highlighted || stop.proximaParada) {
    return const _DriverStopVisual(
      icon: Icons.near_me_outlined,
      color: MapFirstPalette.blue,
    );
  }
  switch (stop.status) {
    case TripTrackingStopStatus.completed:
      return const _DriverStopVisual(
        icon: Icons.check_circle_outline_rounded,
        color: MapFirstPalette.green,
      );
    case TripTrackingStopStatus.skipped:
      return const _DriverStopVisual(
        icon: Icons.skip_next_outlined,
        color: MapFirstPalette.amber,
      );
    case TripTrackingStopStatus.noShow:
      return const _DriverStopVisual(
        icon: Icons.person_off_outlined,
        color: MapFirstPalette.red,
      );
    case TripTrackingStopStatus.pending:
    case TripTrackingStopStatus.unknown:
      return _DriverStopVisual(
        icon:
            stop.confirmadoPresenca
                ? Icons.radio_button_unchecked_rounded
                : Icons.help_outline_rounded,
        color:
            stop.confirmadoPresenca
                ? MapFirstPalette.textMuted
                : MapFirstPalette.amber,
      );
  }
}

class _DriverStopVisual {
  const _DriverStopVisual({required this.icon, required this.color});

  final IconData icon;
  final Color color;
}

class DriverEditableStopCard extends StatelessWidget {
  const DriverEditableStopCard({
    super.key,
    required this.stop,
    required this.routeName,
    required this.indexLabel,
  });

  final DriverTripStopRecord stop;
  final String routeName;
  final int indexLabel;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.transparent,
      child: Container(
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(24),
          boxShadow: const [
            BoxShadow(
              color: Color(0x12161B36),
              blurRadius: 18,
              offset: Offset(0, 10),
            ),
          ],
        ),
        child: ListTile(
          contentPadding: const EdgeInsets.symmetric(
            horizontal: 18,
            vertical: 8,
          ),
          leading: CircleAvatar(
            backgroundColor: const Color(0xFFEAF3FF),
            child: Text(
              '$indexLabel',
              style: Theme.of(context).textTheme.titleSmall?.copyWith(
                color: const Color(0xFF2D6CDF),
                fontWeight: FontWeight.w800,
              ),
            ),
          ),
          title: Text(
            stop.passageiroNome,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(context).textTheme.titleMedium?.copyWith(
              color: const Color(0xFF161B36),
              fontWeight: FontWeight.w800,
            ),
          ),
          subtitle: Padding(
            padding: const EdgeInsets.only(top: 6),
            child: Text(
              '$routeName • ${presentEtaLabel(stop.etaMinutos)}',
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: Theme.of(
                context,
              ).textTheme.bodyMedium?.copyWith(color: const Color(0xFF4D5367)),
            ),
          ),
          trailing: ReorderableDragStartListener(
            index: indexLabel - 1,
            child: const Icon(Icons.drag_handle_rounded),
          ),
        ),
      ),
    );
  }
}

class _StopMetaChip extends StatelessWidget {
  const _StopMetaChip({
    required this.icon,
    required this.label,
    required this.color,
  });

  final IconData icon;
  final String label;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.1),
        borderRadius: BorderRadius.circular(999),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 18, color: color),
          const SizedBox(width: 8),
          Text(
            label,
            style: Theme.of(context).textTheme.labelLarge?.copyWith(
              color: color,
              fontWeight: FontWeight.w700,
            ),
          ),
        ],
      ),
    );
  }
}
