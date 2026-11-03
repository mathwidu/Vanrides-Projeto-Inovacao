import 'package:geolocator/geolocator.dart';

Position testPosition({double latitude = 1, double longitude = 2}) {
  return Position(
    latitude: latitude,
    longitude: longitude,
    timestamp: DateTime.fromMillisecondsSinceEpoch(0, isUtc: true),
    accuracy: 1,
    altitude: 0,
    altitudeAccuracy: 0,
    heading: 0,
    headingAccuracy: 0,
    speed: 0,
    speedAccuracy: 0,
    isMocked: true,
  );
}
