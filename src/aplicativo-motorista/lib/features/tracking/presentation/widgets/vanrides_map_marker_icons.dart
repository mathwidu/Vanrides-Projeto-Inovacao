import 'package:flutter/material.dart';
import 'package:google_maps_flutter/google_maps_flutter.dart';

import '../../domain/trip_tracking_state_record.dart';
import 'vanrides_map_marker_specs.dart';

class VanRidesMapMarkerIconSet {
  const VanRidesMapMarkerIconSet({
    required this.vehicleLive,
    required this.vehicleDelayed,
    required this.vehicleStale,
    required this.vehiclePaused,
    required this.vehicleUnavailable,
    required this.passenger,
    required this.passengerSelected,
    required this.stopCompleted,
    required this.stopNext,
    required this.stopFuture,
    required this.stopSkipped,
    required this.stopNoShow,
  });

  final BitmapDescriptor vehicleLive;
  final BitmapDescriptor vehicleDelayed;
  final BitmapDescriptor vehicleStale;
  final BitmapDescriptor vehiclePaused;
  final BitmapDescriptor vehicleUnavailable;
  final BitmapDescriptor passenger;
  final BitmapDescriptor passengerSelected;
  final BitmapDescriptor stopCompleted;
  final BitmapDescriptor stopNext;
  final BitmapDescriptor stopFuture;
  final BitmapDescriptor stopSkipped;
  final BitmapDescriptor stopNoShow;

  BitmapDescriptor vehicleIconFor(TripLocationFreshness freshness) {
    switch (freshness) {
      case TripLocationFreshness.live:
        return vehicleLive;
      case TripLocationFreshness.delayed:
        return vehicleDelayed;
      case TripLocationFreshness.stale:
        return vehicleStale;
      case TripLocationFreshness.paused:
        return vehiclePaused;
      case TripLocationFreshness.unavailable:
      case TripLocationFreshness.unknown:
        return vehicleUnavailable;
    }
  }

  BitmapDescriptor passengerIcon({bool selected = false}) {
    return selected ? passengerSelected : passenger;
  }

  BitmapDescriptor stopIconFor(VanRidesStopMarkerState state) {
    switch (state) {
      case VanRidesStopMarkerState.completed:
        return stopCompleted;
      case VanRidesStopMarkerState.next:
        return stopNext;
      case VanRidesStopMarkerState.future:
        return stopFuture;
      case VanRidesStopMarkerState.skipped:
        return stopSkipped;
      case VanRidesStopMarkerState.noShow:
        return stopNoShow;
    }
  }
}

class VanRidesMapMarkerIconFactory {
  const VanRidesMapMarkerIconFactory({
    this.pixelRatio = 3,
    this.logicalSize = const Size(58, 58),
  });

  static const String _assetPrefix = 'assets/map_markers';

  final double pixelRatio;
  final Size logicalSize;

  Future<VanRidesMapMarkerIconSet> create() async {
    return VanRidesMapMarkerIconSet(
      vehicleLive: await _asset('vehicle_live.png'),
      vehicleDelayed: await _asset('vehicle_delayed.png'),
      vehicleStale: await _asset('vehicle_stale.png'),
      vehiclePaused: await _asset('vehicle_paused.png'),
      vehicleUnavailable: await _asset('vehicle_unavailable.png'),
      passenger: await _asset('passenger.png'),
      passengerSelected: await _asset('passenger_selected.png'),
      stopCompleted: await _asset('stop_completed.png'),
      stopNext: await _asset('stop_next.png'),
      stopFuture: await _asset('stop_future.png'),
      stopSkipped: await _asset('stop_skipped.png'),
      stopNoShow: await _asset('stop_no_show.png'),
    );
  }

  Future<BitmapDescriptor> _asset(String fileName) {
    return BitmapDescriptor.asset(
      ImageConfiguration(size: logicalSize, devicePixelRatio: pixelRatio),
      '$_assetPrefix/$fileName',
      imagePixelRatio: pixelRatio,
      width: logicalSize.width,
      height: logicalSize.height,
    );
  }
}
