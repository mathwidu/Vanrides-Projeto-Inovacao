import 'dart:async';

import 'package:geolocator/geolocator.dart';
import 'package:vanrides_frontend_dev/services/location_gateway.dart';

import '../test_position.dart';

class FakeLocationGateway implements LocationGateway {
  bool serviceEnabled = true;
  LocationPermission permission = LocationPermission.whileInUse;
  LocationPermission? requestPermissionResult;
  Position currentPosition = testPosition();

  int openLocationSettingsCalls = 0;
  int checkPermissionCalls = 0;
  int requestPermissionCalls = 0;
  int openAppSettingsCalls = 0;

  bool? lastAllowBackground;

  final StreamController<Position> positionController =
      StreamController<Position>.broadcast();

  @override
  Future<bool> isLocationServiceEnabled() async => serviceEnabled;

  @override
  Future<void> openLocationSettings() async {
    openLocationSettingsCalls += 1;
  }

  @override
  Future<LocationPermission> checkPermission() async {
    checkPermissionCalls += 1;
    return permission;
  }

  @override
  Future<LocationPermission> requestPermission() async {
    requestPermissionCalls += 1;
    permission = requestPermissionResult ?? permission;
    return permission;
  }

  @override
  Future<void> openAppSettings() async {
    openAppSettingsCalls += 1;
  }

  @override
  Future<Position> getCurrentPosition() async => currentPosition;

  @override
  Stream<Position> getPositionStream({required bool allowBackground}) {
    lastAllowBackground = allowBackground;
    return positionController.stream;
  }

  Future<void> dispose() async {
    await positionController.close();
  }
}
