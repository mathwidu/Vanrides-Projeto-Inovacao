import 'package:flutter/material.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/domain/driver_trip_record.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/presentation/widgets/driver_trip_action_widgets.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/presentation/widgets/driver_trip_overview_widgets.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/presentation/widgets/driver_trip_stop_queue_widgets.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/presentation/widgets/driver_trip_stop_widgets.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_record.dart';
import 'package:vanrides_frontend_dev/l10n/app_strings.dart';

class DriverTripDetailPanel extends StatelessWidget {
  const DriverTripDetailPanel({
    super.key,
    required this.trip,
    required this.editingRoute,
    required this.editableStops,
    required this.filteredStops,
    required this.stopFilter,
    required this.onStopFilterSelected,
    required this.actionKeys,
    required this.openingNavigation,
    required this.runningLifecycleAction,
    required this.onOpenMaps,
    required this.onOpenPresence,
    required this.onEditRoute,
    required this.onSkipStop,
    required this.onNoShowStop,
    required this.onEndTrip,
    this.initialTabIndex = 0,
  });

  final DriverTripRecord trip;
  final bool editingRoute;
  final List<DriverTripStopRecord> editableStops;
  final List<DriverTripStopRecord> filteredStops;
  final DriverStopFilter stopFilter;
  final ValueChanged<DriverStopFilter> onStopFilterSelected;
  final DriverTripDetailActionKeys actionKeys;
  final bool openingNavigation;
  final bool runningLifecycleAction;
  final VoidCallback? onOpenMaps;
  final VoidCallback? onOpenPresence;
  final VoidCallback? onEditRoute;
  final VoidCallback? onSkipStop;
  final VoidCallback? onNoShowStop;
  final VoidCallback? onEndTrip;
  final int initialTabIndex;

  @override
  Widget build(BuildContext context) {
    final planningMode = trip.status != TripTrackingStatus.inProgress;
    final hasNextStop = trip.proximaParada != null;
    final canOpenMaps =
        trip.rotaGeografica?.navegacaoPreferencial?.hasUrl ?? false;

    return DefaultTabController(
      length: 2,
      initialIndex: initialTabIndex,
      child: FractionallySizedBox(
        heightFactor: 0.78,
        child: Container(
          decoration: const BoxDecoration(
            color: Color(0xFFF7F4EC),
            borderRadius: BorderRadius.vertical(top: Radius.circular(32)),
          ),
          child: SafeArea(
            top: false,
            child: Padding(
              padding: const EdgeInsets.fromLTRB(20, 14, 20, 20),
              child: Column(
                children: [
                  Container(
                    width: 54,
                    height: 6,
                    decoration: BoxDecoration(
                      color: const Color(0xFFD9DDE8),
                      borderRadius: BorderRadius.circular(99),
                    ),
                  ),
                  const SizedBox(height: 16),
                  TabBar(
                    tabs: [
                      Tab(text: AppStrings.motorista.fila),
                      Tab(text: AppStrings.motorista.visaoGeral),
                    ],
                    labelColor: const Color(0xFF161B36),
                    unselectedLabelColor: const Color(0xFF6A7288),
                    indicatorColor: const Color(0xFF161B36),
                  ),
                  const SizedBox(height: 16),
                  Expanded(
                    child: TabBarView(
                      children: [
                        _DriverTripDetailStopTab(
                          trip: trip,
                          editingRoute: editingRoute,
                          editableStops: editableStops,
                          filteredStops: filteredStops,
                          stopFilter: stopFilter,
                          onStopFilterSelected: onStopFilterSelected,
                        ),
                        _DriverTripDetailOverviewTab(
                          trip: trip,
                          planningMode: planningMode,
                          canOpenMaps: canOpenMaps,
                          openingNavigation: openingNavigation,
                          runningLifecycleAction: runningLifecycleAction,
                          hasNextStop: hasNextStop,
                          actionKeys: actionKeys,
                          onOpenMaps: onOpenMaps,
                          onOpenPresence: onOpenPresence,
                          onEditRoute: onEditRoute,
                          onSkipStop: onSkipStop,
                          onNoShowStop: onNoShowStop,
                          onEndTrip: onEndTrip,
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _DriverTripDetailStopTab extends StatelessWidget {
  const _DriverTripDetailStopTab({
    required this.trip,
    required this.editingRoute,
    required this.editableStops,
    required this.filteredStops,
    required this.stopFilter,
    required this.onStopFilterSelected,
  });

  final DriverTripRecord trip;
  final bool editingRoute;
  final List<DriverTripStopRecord> editableStops;
  final List<DriverTripStopRecord> filteredStops;
  final DriverStopFilter stopFilter;
  final ValueChanged<DriverStopFilter> onStopFilterSelected;

  @override
  Widget build(BuildContext context) {
    return ListView(
      children: [
        if (!editingRoute) ...[
          DriverStopFilterBar(
            selected: stopFilter,
            onSelected: onStopFilterSelected,
          ),
          const SizedBox(height: 16),
        ],
        DriverStopList(
          stops: editingRoute ? editableStops : filteredStops,
          routeName: trip.nomeOperacional,
        ),
      ],
    );
  }
}

class _DriverTripDetailOverviewTab extends StatelessWidget {
  const _DriverTripDetailOverviewTab({
    required this.trip,
    required this.planningMode,
    required this.canOpenMaps,
    required this.openingNavigation,
    required this.runningLifecycleAction,
    required this.hasNextStop,
    required this.actionKeys,
    required this.onOpenMaps,
    required this.onOpenPresence,
    required this.onEditRoute,
    required this.onSkipStop,
    required this.onNoShowStop,
    required this.onEndTrip,
  });

  final DriverTripRecord trip;
  final bool planningMode;
  final bool canOpenMaps;
  final bool openingNavigation;
  final bool runningLifecycleAction;
  final bool hasNextStop;
  final DriverTripDetailActionKeys actionKeys;
  final VoidCallback? onOpenMaps;
  final VoidCallback? onOpenPresence;
  final VoidCallback? onEditRoute;
  final VoidCallback? onSkipStop;
  final VoidCallback? onNoShowStop;
  final VoidCallback? onEndTrip;

  @override
  Widget build(BuildContext context) {
    return ListView(
      children: [
        DriverGuidedRouteCard(trip: trip),
        const SizedBox(height: 16),
        DriverDetailActionSection(
          actionKeys: actionKeys,
          planningMode: planningMode,
          canOpenMaps: canOpenMaps,
          openingNavigation: openingNavigation,
          runningLifecycleAction: runningLifecycleAction,
          hasNextStop: hasNextStop,
          onOpenMaps: onOpenMaps,
          onOpenPresence: onOpenPresence,
          onEditRoute: onEditRoute,
          onSkipStop: onSkipStop,
          onNoShowStop: onNoShowStop,
          onEndTrip: onEndTrip,
        ),
        const SizedBox(height: 16),
        DriverOperationalAlertCard(
          title:
              planningMode
                  ? AppStrings.motorista.visaoGeralDaCorrida
                  : AppStrings.motorista.corridaEmAndamento,
          description:
              planningMode
                  ? AppStrings.motorista.visaoGeralDaCorridaDescricao
                  : AppStrings.motorista.corridaEmAndamentoDescricao,
          color: const Color(0xFF2D6CDF),
        ),
      ],
    );
  }
}
