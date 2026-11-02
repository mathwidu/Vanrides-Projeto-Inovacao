import 'trip_tracking_state_record.dart';

class TripLiveLocationRecord {
  const TripLiveLocationRecord({
    required this.latitude,
    required this.longitude,
    this.updatedAt,
    this.freshness = TripLocationFreshness.unavailable,
  });

  final double latitude;
  final double longitude;
  final DateTime? updatedAt;
  final TripLocationFreshness freshness;

  factory TripLiveLocationRecord.fromJson(Map<String, dynamic> json) {
    return TripLiveLocationRecord(
      latitude: (json['latitude'] as num).toDouble(),
      longitude: (json['longitude'] as num).toDouble(),
      updatedAt: parseTripOptionalDateTime(json['updatedAt']),
      freshness: parseTripLocationFreshness(json['freshness'] as String?),
    );
  }
}
