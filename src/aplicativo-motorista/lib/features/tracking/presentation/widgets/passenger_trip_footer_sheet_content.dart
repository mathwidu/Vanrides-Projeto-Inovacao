import 'package:flutter/material.dart';

import '../../domain/trip_route_event.dart';
import '../../domain/trip_tracking_record.dart';
import 'passenger_trip_footer_sheet_controls.dart';
import 'passenger_trip_footer_sheet_model.dart';
import 'passenger_trip_footer_sheet_sections.dart';
import 'passenger_trip_option_helpers.dart';
import 'passenger_trip_sheet_detent.dart';

class PassengerTripFooterSheetContent extends StatelessWidget {
  const PassengerTripFooterSheetContent({
    super.key,
    required this.trip,
    required this.dayTrips,
    required this.selectedTripId,
    required this.pendingPresenceCount,
    this.operationalAlert,
    required this.detent,
    required this.scrollController,
    required this.onDetentSelected,
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

  final TripTrackingRecord trip;
  final List<TripTrackingRecord> dayTrips;
  final int? selectedTripId;
  final int pendingPresenceCount;
  final TripOperationalAlertRecord? operationalAlert;
  final PassengerTripSheetDetent detent;
  final ScrollController scrollController;
  final ValueChanged<PassengerTripSheetDetent> onDetentSelected;
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
    final model = PassengerTripFooterSheetModel.from(
      trip,
      dayTripCount: dayTrips.length,
      pendingPresenceCount: pendingPresenceCount,
      operationalAlert: operationalAlert,
    );

    return Semantics(
      label: detent.semanticLabel,
      container: true,
      child: CustomScrollView(
        key: const Key('passenger.trip.sheet'),
        controller: scrollController,
        physics: const ClampingScrollPhysics(),
        slivers: [
          SliverToBoxAdapter(
            child: PassengerTripSheetHandle(
              detent: detent,
              onDetentSelected: onDetentSelected,
            ),
          ),
          SliverToBoxAdapter(
            child: PassengerTripPersistentSummary(
              model: model,
              onRefresh: onRefresh,
            ),
          ),
          SliverToBoxAdapter(child: _expandedContentFor(model)),
        ],
      ),
    );
  }

  Widget _expandedContentFor(PassengerTripFooterSheetModel model) {
    return PassengerTripExpandedContent(
      model: model,
      trip: trip,
      dayTrips: dayTrips,
      selectedTripId: selectedTripId,
      pendingPresenceCount: pendingPresenceCount,
      onTripSelected: onTripSelected,
      onRefresh: onRefresh,
      onOpenTripDetails: onOpenTripDetails,
      onOpenPresence: onOpenPresence,
      onFocusVan: _focusVanFromSheet,
      onFocusPassenger: _focusPassengerFromSheet,
      detailsButtonKey: detailsButtonKey,
      presenceButtonKey: presenceButtonKey,
      tripKeyBuilder: tripKeyBuilder,
    );
  }

  void _focusVanFromSheet() {
    onDetentSelected(PassengerTripSheetDetent.peek);
    onFocusVan();
  }

  void _focusPassengerFromSheet() {
    onDetentSelected(PassengerTripSheetDetent.peek);
    onFocusPassenger();
  }
}
