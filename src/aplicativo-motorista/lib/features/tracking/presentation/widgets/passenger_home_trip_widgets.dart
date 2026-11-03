import 'package:flutter/material.dart';
import 'package:vanrides_frontend_dev/l10n/app_strings.dart';
import 'package:vanrides_frontend_dev/services/websocket_service.dart';

import '../../data/trip_live_location_service.dart';
import '../../domain/trip_route_event.dart';
import '../../domain/trip_tracking_record.dart';
import '../map_first_v2_components.dart';
import '../trip_live_map_panel.dart';
import 'passenger_home_bottom_nav.dart';
import 'passenger_map_footer_sheet_scaffold.dart';
import 'passenger_map_stage_top_layer.dart';
import 'passenger_trip_footer_sheet_content.dart';
import 'passenger_trip_option_helpers.dart';
import 'vanrides_map_tokens.dart';

class PassengerHomeMapScene extends StatefulWidget {
  const PassengerHomeMapScene({
    super.key,
    required this.trip,
    required this.dayTrips,
    required this.selectedTripId,
    required this.effectiveMotoristaId,
    required this.email,
    required this.senha,
    required this.accessToken,
    required this.liveLocationGateway,
    required this.liveLocationWebSocketService,
    required this.mapRenderMode,
    required this.onRefresh,
    required this.onTripSelected,
    required this.onOpenTripDetails,
    required this.onOpenMap,
    required this.onOpenPresence,
    this.pendingPresenceCount = 0,
    this.operationalAlert,
    this.detailsButtonKey,
    this.presenceButtonKey,
    this.agendaKey,
    this.tripKeyBuilder,
    this.selectedNavIndex = 0,
    this.onDestinationSelected,
  });

  static const mapPadding = EdgeInsets.fromLTRB(12, 106, 12, 332);

  final TripTrackingRecord trip;
  final List<TripTrackingRecord> dayTrips;
  final int? selectedTripId;
  final int effectiveMotoristaId;
  final String email;
  final String senha;
  final String? accessToken;
  final TripLiveLocationGateway? liveLocationGateway;
  final WebSocketServiceBase? liveLocationWebSocketService;
  final TripLiveMapRenderMode mapRenderMode;
  final VoidCallback onRefresh;
  final ValueChanged<TripTrackingRecord> onTripSelected;
  final VoidCallback onOpenTripDetails;
  final VoidCallback onOpenMap;
  final VoidCallback onOpenPresence;
  final int pendingPresenceCount;
  final TripOperationalAlertRecord? operationalAlert;
  final Key? detailsButtonKey;
  final Key? presenceButtonKey;
  final Key? agendaKey;
  final PassengerTripKeyBuilder? tripKeyBuilder;
  final int selectedNavIndex;
  final ValueChanged<int>? onDestinationSelected;

  @override
  State<PassengerHomeMapScene> createState() => _PassengerHomeMapSceneState();
}

class _PassengerHomeMapSceneState extends State<PassengerHomeMapScene> {
  final ValueNotifier<TripLiveMapFocusMode> _focusMode =
      ValueNotifier<TripLiveMapFocusMode>(
        TripLiveMapFocusMode.passengerJourney,
      );

  @override
  void dispose() {
    _focusMode.dispose();
    super.dispose();
  }

  void _focusVan() {
    _focusMode.value = TripLiveMapFocusMode.vehicleOnly;
  }

  void _focusPassenger() {
    _focusMode.value = TripLiveMapFocusMode.passengerOnly;
  }

  void _focusJourney() {
    _focusMode.value = TripLiveMapFocusMode.passengerJourney;
  }

  @override
  Widget build(BuildContext context) {
    return ValueListenableBuilder<TripLiveMapFocusMode>(
      valueListenable: _focusMode,
      builder: (context, focusMode, _) {
        return PassengerMapFooterSheetScaffold(
          topBar: PassengerMapStageTopLayer(
            dayTrips: widget.dayTrips,
            selectedTripId: widget.selectedTripId,
            focusMode: focusMode,
            onTripSelected: widget.onTripSelected,
            onRefresh: widget.onRefresh,
            onFocusJourney: _focusJourney,
            onFocusVan: _focusVan,
            onFocusPassenger: _focusPassenger,
            agendaKey: widget.agendaKey,
            tripKeyBuilder: widget.tripKeyBuilder,
          ),
          map: TripLiveMapPanel(
            motoristaId: widget.effectiveMotoristaId,
            email: widget.email,
            senha: widget.senha,
            accessToken: widget.accessToken,
            height: MediaQuery.sizeOf(context).height,
            gateway: widget.liveLocationGateway,
            webSocketService: widget.liveLocationWebSocketService,
            renderMode: widget.mapRenderMode,
            routeGeometry: widget.trip.rotaGeografica,
            trackingState: widget.trip.trackingState,
            focusMode: focusMode,
            showControls: false,
            mapPadding: PassengerHomeMapScene.mapPadding,
          ),
          footer: PassengerHomeBottomNavContent(
            selectedIndex: widget.selectedNavIndex,
            onDestinationSelected: widget.onDestinationSelected ?? (_) {},
          ),
          sheetBuilder: (context, scrollController, detent, onDetentSelected) {
            return PassengerTripFooterSheetContent(
              trip: widget.trip,
              dayTrips: widget.dayTrips,
              selectedTripId: widget.selectedTripId,
              pendingPresenceCount: widget.pendingPresenceCount,
              operationalAlert: widget.operationalAlert,
              detent: detent,
              scrollController: scrollController,
              onDetentSelected: onDetentSelected,
              onTripSelected: widget.onTripSelected,
              onRefresh: widget.onRefresh,
              onOpenTripDetails: widget.onOpenTripDetails,
              onOpenPresence: widget.onOpenPresence,
              onFocusVan: _focusVan,
              onFocusPassenger: _focusPassenger,
              detailsButtonKey: widget.detailsButtonKey,
              presenceButtonKey: widget.presenceButtonKey,
              tripKeyBuilder: widget.tripKeyBuilder,
            );
          },
        );
      },
    );
  }
}

class PassengerDayAgendaSelector extends StatelessWidget {
  const PassengerDayAgendaSelector({
    super.key,
    required this.dayTrips,
    required this.selectedTripId,
    required this.onTripSelected,
    this.agendaKey,
    this.tripKeyBuilder,
    this.dark = false,
  });

  final List<TripTrackingRecord> dayTrips;
  final int? selectedTripId;
  final ValueChanged<TripTrackingRecord> onTripSelected;
  final Key? agendaKey;
  final PassengerTripKeyBuilder? tripKeyBuilder;
  final bool dark;

  @override
  Widget build(BuildContext context) {
    final labelColor =
        dark ? VanRidesMapTokens.textPrimary : MapFirstPalette.textStrong;
    final helperColor =
        dark ? VanRidesMapTokens.textMuted : MapFirstPalette.textMuted;
    final chipBackground =
        dark ? VanRidesMapTokens.surfaceRaised : Colors.white;
    final selectedColor =
        dark ? VanRidesMapTokens.blueTracking : MapFirstPalette.brandBlue;
    final unselectedText =
        dark ? VanRidesMapTokens.textSecondary : MapFirstPalette.textStrong;
    final unselectedBorder =
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
                AppStrings.passageiro.agendaDeHoje,
                style: Theme.of(context).textTheme.labelLarge?.copyWith(
                  color: labelColor,
                  fontWeight: FontWeight.w800,
                ),
              ),
            ),
            Text(
              AppStrings.passageiro.viagensNoDia(dayTrips.length),
              style: Theme.of(context).textTheme.bodySmall?.copyWith(
                color: helperColor,
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
                  key: tripKeyBuilder?.call(trip.id),
                  selected: trip.id == selectedTripId,
                  avatar: Icon(
                    passengerTripStatusIcon(trip.status),
                    size: 16,
                    color:
                        trip.id == selectedTripId
                            ? Colors.white
                            : selectedColor,
                  ),
                  label: Text(passengerTripOptionLabel(trip)),
                  labelStyle: Theme.of(context).textTheme.labelLarge?.copyWith(
                    color:
                        trip.id == selectedTripId
                            ? Colors.white
                            : unselectedText,
                    fontWeight: FontWeight.w800,
                  ),
                  backgroundColor: chipBackground,
                  selectedColor: selectedColor,
                  side: BorderSide(
                    color:
                        trip.id == selectedTripId
                            ? selectedColor
                            : unselectedBorder,
                  ),
                  showCheckmark: false,
                  onSelected: (_) => onTripSelected(trip),
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

class PassengerOperationTypeBadge extends StatelessWidget {
  const PassengerOperationTypeBadge({super.key, required this.label});

  final String label;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: 0.82),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: MapFirstPalette.border),
      ),
      child: Text(
        label,
        style: Theme.of(context).textTheme.labelLarge?.copyWith(
          color: MapFirstPalette.blue,
          fontWeight: FontWeight.w800,
        ),
      ),
    );
  }
}
