import 'trip_tracking_record.dart';

TripTrackingRecord? selectRelevantPassengerTrip(
  List<TripTrackingRecord> trips, {
  int? preferredTripId,
}) {
  if (trips.isEmpty) {
    return null;
  }

  if (preferredTripId != null) {
    for (final trip in trips) {
      if (trip.id == preferredTripId) {
        return trip;
      }
    }
  }

  for (final trip in trips) {
    if (trip.status == TripTrackingStatus.inProgress) {
      return trip;
    }
  }

  for (final trip in trips) {
    if (trip.status == TripTrackingStatus.planned) {
      return trip;
    }
  }

  for (final trip in trips.reversed) {
    if (trip.status == TripTrackingStatus.completed) {
      return trip;
    }
  }

  for (final trip in trips) {
    if (trip.status != TripTrackingStatus.canceled) {
      return trip;
    }
  }

  return trips.first;
}
