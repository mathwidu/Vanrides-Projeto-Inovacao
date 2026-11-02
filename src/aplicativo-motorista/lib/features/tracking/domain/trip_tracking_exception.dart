class TripTrackingException implements Exception {
  const TripTrackingException(this.message);

  final String message;

  @override
  String toString() => message;
}
