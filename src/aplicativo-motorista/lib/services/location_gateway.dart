import 'package:geolocator/geolocator.dart';

abstract class LocationGateway {
  Future<bool> isLocationServiceEnabled();
  Future<void> openLocationSettings();

  Future<LocationPermission> checkPermission();
  Future<LocationPermission> requestPermission();
  Future<void> openAppSettings();
  Future<Position> getCurrentPosition();

  Stream<Position> getPositionStream({required bool allowBackground});
}

class GeolocatorLocationGateway implements LocationGateway {
  @override
  Future<bool> isLocationServiceEnabled() =>
      Geolocator.isLocationServiceEnabled();

  @override
  Future<void> openLocationSettings() => Geolocator.openLocationSettings();

  @override
  Future<LocationPermission> checkPermission() => Geolocator.checkPermission();

  @override
  Future<LocationPermission> requestPermission() =>
      Geolocator.requestPermission();

  @override
  Future<void> openAppSettings() => Geolocator.openAppSettings();

  @override
  Future<Position> getCurrentPosition() {
    return Geolocator.getCurrentPosition(
      desiredAccuracy: LocationAccuracy.high,
    );
  }

  @override
  Stream<Position> getPositionStream({required bool allowBackground}) {
    return Geolocator.getPositionStream(
      locationSettings: AppleSettings(
        accuracy: LocationAccuracy.high,
        distanceFilter: 10,
        allowBackgroundLocationUpdates: allowBackground,
        showBackgroundLocationIndicator: allowBackground,
      ),
    );
  }
}
