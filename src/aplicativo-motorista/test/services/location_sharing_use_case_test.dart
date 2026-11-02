import 'package:flutter_test/flutter_test.dart';
import 'package:geolocator/geolocator.dart';
import 'package:vanrides_frontend_dev/services/location_sharing_use_case.dart';

import '../support/fakes/fake_location_gateway.dart';

void main() {
  test('returns service disabled when location services are off', () async {
    final gateway = FakeLocationGateway()..serviceEnabled = false;
    final useCase = LocationSharingUseCase(gateway: gateway);

    final result = await useCase.ensurePermissions();

    expect(result, isA<LocationSharingServiceDisabled>());
    expect(gateway.checkPermissionCalls, 0);
  });

  test('denied -> request -> denied returns permission denied', () async {
    final gateway = FakeLocationGateway()
      ..permission = LocationPermission.denied
      ..requestPermissionResult = LocationPermission.denied;
    final useCase = LocationSharingUseCase(gateway: gateway);

    final result = await useCase.ensurePermissions();

    expect(result, isA<LocationSharingPermissionDenied>());
    expect(gateway.checkPermissionCalls, 1);
    expect(gateway.requestPermissionCalls, 1);
  });

  test('denied -> request -> whileInUse grants without background', () async {
    final gateway = FakeLocationGateway()
      ..permission = LocationPermission.denied
      ..requestPermissionResult = LocationPermission.whileInUse;
    final useCase = LocationSharingUseCase(gateway: gateway);

    final result = await useCase.ensurePermissions();

    expect(result, isA<LocationSharingPermissionGranted>());
    expect((result as LocationSharingPermissionGranted).allowBackground, isFalse);
  });

  test('always grants with background', () async {
    final gateway = FakeLocationGateway()..permission = LocationPermission.always;
    final useCase = LocationSharingUseCase(gateway: gateway);

    final result = await useCase.ensurePermissions();

    expect(result, isA<LocationSharingPermissionGranted>());
    expect((result as LocationSharingPermissionGranted).allowBackground, isTrue);
  });

  test('deniedForever returns permission denied forever', () async {
    final gateway = FakeLocationGateway()..permission = LocationPermission.deniedForever;
    final useCase = LocationSharingUseCase(gateway: gateway);

    final result = await useCase.ensurePermissions();

    expect(result, isA<LocationSharingPermissionDeniedForever>());
  });

  test('unableToDetermine returns unable to determine', () async {
    final gateway = FakeLocationGateway()..permission = LocationPermission.unableToDetermine;
    final useCase = LocationSharingUseCase(gateway: gateway);

    final result = await useCase.ensurePermissions();

    expect(result, isA<LocationSharingPermissionUnableToDetermine>());
  });
}
