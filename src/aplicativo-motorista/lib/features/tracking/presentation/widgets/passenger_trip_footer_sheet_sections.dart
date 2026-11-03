import 'package:flutter/material.dart';

import '../../domain/trip_route_event.dart';
import '../../domain/trip_tracking_record.dart';
import 'compact_trip_switcher.dart';
import 'passenger_trip_footer_sheet_controls.dart';
import 'passenger_trip_footer_sheet_model.dart';
import 'passenger_trip_readiness_line.dart';
import 'passenger_trip_option_helpers.dart';
import 'vanrides_map_tokens.dart';

class PassengerTripPersistentSummary extends StatelessWidget {
  const PassengerTripPersistentSummary({
    super.key,
    required this.model,
    required this.onRefresh,
  });

  final PassengerTripFooterSheetModel model;
  final VoidCallback onRefresh;

  @override
  Widget build(BuildContext context) {
    final showSignalAction = model.signal.actionLabel != 'Atualizar';

    return KeyedSubtree(
      key: const Key('passenger.trip.sheet.resting'),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(18, 0, 18, 12),
        child: KeyedSubtree(
          key: const Key('passenger.trip.sheet.summary'),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              PassengerTripTodayLine(model: model),
              const SizedBox(height: 12),
              PassengerTripStatusLine(model: model),
              if (model.readiness != null) ...[
                const SizedBox(height: 10),
                PassengerTripReadinessLine(readiness: model.readiness!),
              ],
              if (model.operationalAlert != null) ...[
                const SizedBox(height: 12),
                PassengerTripOperationalAlertLine(
                  alert: model.operationalAlert!,
                ),
              ],
              if (showSignalAction) ...[
                const SizedBox(height: 10),
                _PassengerTripSignalActionButton(
                  label: model.signal.actionLabel,
                  onTap: onRefresh,
                ),
              ],
              const SizedBox(height: 14),
              PassengerTripStopLine(model: model),
            ],
          ),
        ),
      ),
    );
  }
}

class PassengerTripTodayLine extends StatelessWidget {
  const PassengerTripTodayLine({super.key, required this.model});

  final PassengerTripFooterSheetModel model;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      label: '${model.todayTitle}. ${model.todaySubtitle}',
      container: true,
      child: Container(
        padding: const EdgeInsets.fromLTRB(12, 10, 12, 10),
        decoration: BoxDecoration(
          color: VanRidesMapTokens.surfaceRaised.withValues(alpha: 0.58),
          borderRadius: BorderRadius.circular(18),
          border: Border.all(
            color: VanRidesMapTokens.blueTracking.withValues(alpha: 0.24),
          ),
        ),
        child: Row(
          children: [
            Container(
              width: 32,
              height: 32,
              decoration: BoxDecoration(
                color: VanRidesMapTokens.blueTracking.withValues(alpha: 0.15),
                borderRadius: BorderRadius.circular(14),
              ),
              child: const Icon(
                Icons.today_outlined,
                color: VanRidesMapTokens.blueTracking,
                size: 17,
              ),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    model.todayTitle,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: Theme.of(context).textTheme.labelLarge?.copyWith(
                      color: VanRidesMapTokens.textPrimary,
                      fontWeight: FontWeight.w900,
                    ),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    model.todaySubtitle,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: Theme.of(context).textTheme.bodySmall?.copyWith(
                      color: VanRidesMapTokens.textSecondary,
                      fontWeight: FontWeight.w700,
                    ),
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

class _PassengerTripSignalActionButton extends StatelessWidget {
  const _PassengerTripSignalActionButton({
    required this.label,
    required this.onTap,
  });

  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return OutlinedButton.icon(
      key: const Key('passenger.trip.signal.refreshAction'),
      onPressed: onTap,
      style: OutlinedButton.styleFrom(
        foregroundColor: VanRidesMapTokens.textPrimary,
        backgroundColor: VanRidesMapTokens.surfaceRaised.withValues(
          alpha: 0.68,
        ),
        side: BorderSide(
          color: VanRidesMapTokens.border.withValues(alpha: 0.34),
        ),
        minimumSize: const Size(0, 38),
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
        visualDensity: VisualDensity.compact,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        textStyle: Theme.of(
          context,
        ).textTheme.labelLarge?.copyWith(fontWeight: FontWeight.w800),
      ),
      icon: const Icon(Icons.refresh_rounded, size: 17),
      label: Text(label, maxLines: 1, overflow: TextOverflow.ellipsis),
    );
  }
}

class PassengerTripOperationalAlertLine extends StatelessWidget {
  const PassengerTripOperationalAlertLine({super.key, required this.alert});

  final TripOperationalAlertRecord alert;

  @override
  Widget build(BuildContext context) {
    final color = _alertColor(alert);
    return Semantics(
      label: '${alert.titulo}. ${alert.mensagem}',
      child: Container(
        key: const Key('passenger.trip.operationalAlert'),
        padding: const EdgeInsets.fromLTRB(12, 10, 12, 10),
        decoration: BoxDecoration(
          color: color.withValues(alpha: 0.14),
          borderRadius: BorderRadius.circular(18),
          border: Border.all(color: color.withValues(alpha: 0.32)),
        ),
        child: Row(
          children: [
            Icon(_alertIcon(alert), color: color, size: 19),
            const SizedBox(width: 10),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    alert.titulo,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: Theme.of(context).textTheme.labelLarge?.copyWith(
                      color: VanRidesMapTokens.textPrimary,
                      fontWeight: FontWeight.w900,
                    ),
                  ),
                  if (alert.mensagem.isNotEmpty) ...[
                    const SizedBox(height: 2),
                    Text(
                      alert.mensagem,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.bodySmall?.copyWith(
                        color: VanRidesMapTokens.textSecondary,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ],
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

IconData _alertIcon(TripOperationalAlertRecord alert) {
  return switch (alert.tipo) {
    TripOperationalAlertType.driverApproaching => Icons.near_me_rounded,
    TripOperationalAlertType.nextPassenger => Icons.person_pin_circle_rounded,
    TripOperationalAlertType.stopCompleted => Icons.check_circle_rounded,
    TripOperationalAlertType.unknown => Icons.notifications_active_rounded,
  };
}

Color _alertColor(TripOperationalAlertRecord alert) {
  return switch (alert.severidade) {
    TripOperationalAlertSeverity.urgent => VanRidesMapTokens.blueTracking,
    TripOperationalAlertSeverity.success => VanRidesMapTokens.greenAction,
    TripOperationalAlertSeverity.warning => VanRidesMapTokens.amberWarning,
    TripOperationalAlertSeverity.info ||
    TripOperationalAlertSeverity.unknown => VanRidesMapTokens.blueTracking,
  };
}

class PassengerTripExpandedContent extends StatelessWidget {
  const PassengerTripExpandedContent({
    super.key,
    required this.model,
    required this.trip,
    required this.dayTrips,
    required this.selectedTripId,
    required this.pendingPresenceCount,
    required this.onTripSelected,
    required this.onRefresh,
    required this.onOpenTripDetails,
    required this.onOpenPresence,
    required this.onFocusVan,
    required this.onFocusPassenger,
    this.detailsButtonKey,
    this.presenceButtonKey,
    this.tripKeyBuilder,
  });

  final PassengerTripFooterSheetModel model;
  final TripTrackingRecord trip;
  final List<TripTrackingRecord> dayTrips;
  final int? selectedTripId;
  final int pendingPresenceCount;
  final ValueChanged<TripTrackingRecord> onTripSelected;
  final VoidCallback onRefresh;
  final VoidCallback onOpenTripDetails;
  final VoidCallback onOpenPresence;
  final VoidCallback onFocusVan;
  final VoidCallback onFocusPassenger;
  final Key? detailsButtonKey;
  final Key? presenceButtonKey;
  final PassengerTripKeyBuilder? tripKeyBuilder;

  @override
  Widget build(BuildContext context) {
    return KeyedSubtree(
      key: const Key('passenger.trip.sheet.expanded'),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(18, 4, 18, 18),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (dayTrips.length > 1) ...[
              const PassengerTripSectionLabel('Hoje'),
              const SizedBox(height: 8),
              CompactTripSwitcher(
                dayTrips: dayTrips,
                selectedTripId: selectedTripId,
                onTripSelected: onTripSelected,
                tripKeyBuilder: tripKeyBuilder,
              ),
              const SizedBox(height: 16),
            ],
            const PassengerTripSectionLabel('Van e motorista'),
            const SizedBox(height: 8),
            PassengerTripInfoRow(
              icon: Icons.directions_bus_filled_rounded,
              title: model.serviceName,
              subtitle: model.driverName,
            ),
            if (pendingPresenceCount > 0) ...[
              const SizedBox(height: 14),
              PassengerTripPresenceAction(
                key: presenceButtonKey,
                label: model.presenceActionLabel,
                onTap: onOpenPresence,
              ),
            ],
            const SizedBox(height: 14),
            Row(
              children: [
                Expanded(
                  child: PassengerTripSheetActionButton(
                    key: const Key('passenger.trip.action.focusVan'),
                    icon: Icons.directions_bus_filled_rounded,
                    label: 'Ver van',
                    onTap: onFocusVan,
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: PassengerTripSheetActionButton(
                    key: const Key('passenger.trip.action.focusPassenger'),
                    icon: Icons.person_pin_circle_rounded,
                    label: 'Minha parada',
                    onTap: onFocusPassenger,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 10),
            Row(
              children: [
                Expanded(
                  child: PassengerTripSheetActionButton(
                    key: detailsButtonKey,
                    icon: Icons.receipt_long_rounded,
                    label: 'Detalhes',
                    onTap: onOpenTripDetails,
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: PassengerTripSheetActionButton(
                    icon: Icons.refresh_rounded,
                    label: model.signal.actionLabel,
                    onTap: onRefresh,
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
