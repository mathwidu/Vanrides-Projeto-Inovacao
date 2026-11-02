import 'package:flutter_test/flutter_test.dart';
import 'package:geolocator/geolocator.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/presentation/motorista_location_presenter.dart';
import 'package:vanrides_frontend_dev/services/location_sharing_use_case.dart';

import '../../support/fakes/fake_location_gateway.dart';
import '../../support/fakes/fake_websocket_service.dart';
import '../../support/test_position.dart';

void main() {
  test('estado inicial começa sem compartilhamento e sem erro', () {
    final presenter = MotoristaLocationPresenter(
      webSocketService: FakeWebSocketService(),
      locationSharingUseCase: LocationSharingUseCase(
        gateway: FakeLocationGateway(),
      ),
      authorizationHeader: 'Bearer token',
    );

    expect(presenter.isSharing, isFalse);
    expect(presenter.hasPermission, isFalse);
    expect(presenter.locationError, isNull);
  });

  test('stopSharing desliga o estado e desconecta o websocket', () async {
    final gateway =
        FakeLocationGateway()
          ..permission = LocationPermission.whileInUse
          ..currentPosition = testPosition(latitude: 10, longitude: 20);
    final websocket = FakeWebSocketService();
    final presenter = MotoristaLocationPresenter(
      webSocketService: websocket,
      locationSharingUseCase: LocationSharingUseCase(gateway: gateway),
      authorizationHeader: 'Bearer token',
    );

    await presenter.startSharing(42, 66);
    expect(presenter.isSharing, isTrue);
    expect(websocket.connectCalls, 1);

    await presenter.stopSharing();

    expect(presenter.isSharing, isFalse);
    expect(websocket.disconnectCalls, 1);

    await gateway.dispose();
  });
}
