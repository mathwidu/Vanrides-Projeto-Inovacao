import 'package:geolocator/geolocator.dart';

import 'location_gateway.dart';

sealed class LocationSharingPermissionResult {
  const LocationSharingPermissionResult();
}

class LocationSharingServiceDisabled extends LocationSharingPermissionResult {
  const LocationSharingServiceDisabled();
}

class LocationSharingPermissionDenied extends LocationSharingPermissionResult {
  const LocationSharingPermissionDenied();
}

class LocationSharingPermissionDeniedForever extends LocationSharingPermissionResult {
  const LocationSharingPermissionDeniedForever();
}

class LocationSharingPermissionUnableToDetermine extends LocationSharingPermissionResult {
  const LocationSharingPermissionUnableToDetermine();
}

class LocationSharingPermissionGranted extends LocationSharingPermissionResult {
  final bool allowBackground;

  const LocationSharingPermissionGranted({required this.allowBackground});
}

class LocationSharingUseCase {
  final LocationGateway gateway;

  const LocationSharingUseCase({required this.gateway});

  Future<LocationSharingPermissionResult> ensurePermissions() async {
    final serviceEnabled = await gateway.isLocationServiceEnabled();
    if (!serviceEnabled) {
      return const LocationSharingServiceDisabled();
    }

    var permission = await gateway.checkPermission();
    if (permission == LocationPermission.denied) {
      permission = await gateway.requestPermission();
    }

    if (permission == LocationPermission.denied) {
      return const LocationSharingPermissionDenied();
    }

    if (permission == LocationPermission.deniedForever) {
      return const LocationSharingPermissionDeniedForever();
    }

    if (permission == LocationPermission.unableToDetermine) {
      return const LocationSharingPermissionUnableToDetermine();
    }

    final allowBackground = permission == LocationPermission.always;
    return LocationSharingPermissionGranted(allowBackground: allowBackground);
  }
}
