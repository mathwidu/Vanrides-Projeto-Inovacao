import 'dart:async';

import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/domain/driver_trip_record.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/presentation/widgets/driver_home_base_widgets.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/presentation/widgets/driver_home_trip_agenda_widgets.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/presentation/widgets/driver_home_trip_bar_widgets.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/presentation/widgets/driver_home_trip_sheet_widgets.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/presentation/widgets/driver_trip_map_widgets.dart';
import 'package:vanrides_frontend_dev/features/navigation/data/driver_navigation_route_plan_contract.dart';
import 'package:vanrides_frontend_dev/features/navigation/domain/driver_navigation_action_queue.dart';
import 'package:vanrides_frontend_dev/features/tracking/data/trip_live_location_service.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_live_location_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/trip_live_map_panel.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/trip_map_scene_shell.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/widgets/vanrides_map_tokens.dart';
import 'package:vanrides_frontend_dev/services/websocket_service.dart';

class DriverHomeTripTab extends StatelessWidget {
  const DriverHomeTripTab({
    super.key,
    required this.isLoading,
    required this.errorMessage,
    required this.trip,
    required this.motoristaId,
    required this.email,
    required this.senha,
    this.accessToken,
    this.liveLocationGateway,
    this.liveLocationWebSocketService,
    required this.mapRenderMode,
    this.driverLiveLocation,
    required this.sharingLocation,
    required this.editingRoute,
    required this.dayTrips,
    required this.selectedTripId,
    required this.editableStops,
    required this.savingRoute,
    required this.openingNavigation,
    required this.runningLifecycleAction,
    this.navigationActionQueueSummary,
    this.routePlanResponse,
    required this.refreshKey,
    required this.agendaKey,
    required this.tripKeyBuilder,
    required this.startTripKey,
    required this.detailsKey,
    required this.mapKey,
    required this.editRouteKey,
    required this.saveRouteKey,
    required this.cancelRouteKey,
    required this.completeStopKey,
    required this.navigationKey,
    required this.skipStopKey,
    required this.noShowKey,
    required this.endTripKey,
    required this.onRefresh,
    required this.onSelectTrip,
    required this.onReorderStops,
    required this.onSaveRoute,
    required this.onCancelRoute,
    required this.onStartTrip,
    required this.onOpenStops,
    required this.onOpenMap,
    required this.onEditRoute,
    required this.onCompleteStop,
    required this.onOpenNavigation,
    required this.onSkipStop,
    required this.onNoShowStop,
    this.onRetryQueuedActions,
    required this.onEndTrip,
  });

  final bool isLoading;
  final String? errorMessage;
  final DriverTripRecord? trip;
  final int motoristaId;
  final String email;
  final String senha;
  final String? accessToken;
  final TripLiveLocationGateway? liveLocationGateway;
  final WebSocketServiceBase? liveLocationWebSocketService;
  final TripLiveMapRenderMode mapRenderMode;
  final TripLiveLocationRecord? driverLiveLocation;
  final bool sharingLocation;
  final bool editingRoute;
  final List<DriverTripRecord> dayTrips;
  final int? selectedTripId;
  final List<DriverTripStopRecord> editableStops;
  final bool savingRoute;
  final bool openingNavigation;
  final bool runningLifecycleAction;
  final DriverNavigationActionQueueSummary? navigationActionQueueSummary;
  final DriverNavigationRoutePlanResponse? routePlanResponse;
  final Key refreshKey;
  final Key agendaKey;
  final DriverTripKeyBuilder tripKeyBuilder;
  final Key startTripKey;
  final Key detailsKey;
  final Key mapKey;
  final Key editRouteKey;
  final Key saveRouteKey;
  final Key cancelRouteKey;
  final Key completeStopKey;
  final Key navigationKey;
  final Key skipStopKey;
  final Key noShowKey;
  final Key endTripKey;
  final Future<void> Function() onRefresh;
  final ValueChanged<DriverTripRecord> onSelectTrip;
  final ReorderCallback onReorderStops;
  final VoidCallback onSaveRoute;
  final VoidCallback onCancelRoute;
  final VoidCallback onStartTrip;
  final VoidCallback onOpenStops;
  final VoidCallback onOpenMap;
  final VoidCallback onEditRoute;
  final VoidCallback onCompleteStop;
  final VoidCallback onOpenNavigation;
  final VoidCallback onSkipStop;
  final VoidCallback onNoShowStop;
  final VoidCallback? onRetryQueuedActions;
  final VoidCallback onEndTrip;

  @override
  Widget build(BuildContext context) {
    final trip = this.trip;
    if (isLoading || errorMessage != null || trip == null) {
      return DriverHomeUnavailableTripState(
        isLoading: isLoading,
        errorMessage: errorMessage,
        refreshKey: refreshKey,
        onRefresh: onRefresh,
      );
    }

    final planningMode = trip.status != TripTrackingStatus.inProgress;
    final dateLabel = DateFormat(
      'dd/MM/yyyy',
      'pt_BR',
    ).format(trip.dataOperacao);
    final nextStop = trip.proximaParada;
    final hasDayAgendaSelector = dayTrips.length > 1;
    final remainingStops =
        trip.paradas
            .where((stop) => stop.status == TripTrackingStopStatus.pending)
            .length;

    return TripMapSceneShell(
      topBar:
          planningMode
              ? DriverHomePlanningTopBar(
                trip: trip,
                dateLabel: dateLabel,
                refreshKey: refreshKey,
                onRefresh: _triggerRefresh,
              )
              : DriverHomeActiveTopBar(
                remainingStops: remainingStops,
                refreshKey: refreshKey,
                onRefresh: _triggerRefresh,
              ),
      map: DriverMapHero(
        trip: trip,
        motoristaId: motoristaId,
        email: email,
        senha: senha,
        accessToken: accessToken,
        liveLocationGateway: liveLocationGateway,
        liveLocationWebSocketService: liveLocationWebSocketService,
        mapRenderMode: mapRenderMode,
        height: MediaQuery.sizeOf(context).height,
        seedLocation: driverLiveLocation,
        sharingLocation: sharingLocation,
        focusMode:
            trip.status == TripTrackingStatus.inProgress
                ? TripLiveMapFocusMode.nextStopOnly
                : TripLiveMapFocusMode.fullJourney,
      ),
      floatingAction: null,
      sheetMaxHeightFactor:
          editingRoute
              ? 0.72
              : planningMode
              ? hasDayAgendaSelector
                  ? 0.38
                  : 0.27
              : hasDayAgendaSelector
              ? 0.46
              : 0.4,
      sheetColor:
          editingRoute
              ? Colors.white
              : VanRidesMapTokens.surface.withValues(alpha: 0.98),
      sheetBorderRadius: 28,
      sheetPadding: const EdgeInsets.fromLTRB(18, 12, 18, 18),
      sheetBottomInset: 16,
      sheetChild:
          editingRoute
              ? DriverHomeRouteEditingSheet(
                trip: trip,
                editableStops: editableStops,
                savingRoute: savingRoute,
                saveRouteKey: saveRouteKey,
                cancelRouteKey: cancelRouteKey,
                onReorder: onReorderStops,
                onSaveRoute: onSaveRoute,
                onCancelRoute: onCancelRoute,
              )
              : planningMode
              ? DriverHomePlanningSheet(
                trip: trip,
                nextStop: nextStop,
                dayTrips: dayTrips,
                selectedTripId: selectedTripId,
                agendaKey: agendaKey,
                tripKeyBuilder: tripKeyBuilder,
                startTripKey: startTripKey,
                detailsKey: detailsKey,
                mapKey: mapKey,
                editRouteKey: editRouteKey,
                runningLifecycleAction: runningLifecycleAction,
                onSelectTrip: onSelectTrip,
                onStartTrip: onStartTrip,
                onOpenStops: onOpenStops,
                onOpenMap: onOpenMap,
                onEditRoute: onEditRoute,
              )
              : DriverHomeActiveSheet(
                trip: trip,
                nextStop: nextStop,
                dayTrips: dayTrips,
                selectedTripId: selectedTripId,
                agendaKey: agendaKey,
                tripKeyBuilder: tripKeyBuilder,
                completeStopKey: completeStopKey,
                navigationKey: navigationKey,
                skipStopKey: skipStopKey,
                noShowKey: noShowKey,
                endTripKey: endTripKey,
                sharingLocation: sharingLocation,
                openingNavigation: openingNavigation,
                runningLifecycleAction: runningLifecycleAction,
                navigationActionQueueSummary: navigationActionQueueSummary,
                routePlanResponse: routePlanResponse,
                onSelectTrip: onSelectTrip,
                onCompleteStop: onCompleteStop,
                onOpenNavigation: onOpenNavigation,
                onSkipStop: onSkipStop,
                onNoShowStop: onNoShowStop,
                onRetryQueuedActions: onRetryQueuedActions,
                onEndTrip: onEndTrip,
              ),
    );
  }

  void _triggerRefresh() {
    unawaited(onRefresh());
  }
}
