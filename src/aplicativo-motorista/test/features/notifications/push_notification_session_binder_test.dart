import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/notifications/data/push_notification_gateway.dart';
import 'package:vanrides_frontend_dev/features/notifications/data/push_notification_registration_service.dart';
import 'package:vanrides_frontend_dev/features/notifications/data/push_notification_session_binder.dart';
import 'package:vanrides_frontend_dev/features/notifications/domain/push_notification_models.dart';

class _FakePushNotificationGateway implements PushNotificationGateway {
  int requestPermissionCalls = 0;
  int currentPermissionStatusCalls = 0;

  @override
  Future<PushPermissionStatus> requestPermission() async {
    requestPermissionCalls += 1;
    return PushPermissionStatus.granted;
  }

  @override
  Future<PushPermissionStatus> currentPermissionStatus() async {
    currentPermissionStatusCalls += 1;
    return PushPermissionStatus.granted;
  }

  @override
  Future<PushNotificationToken?> getToken() async =>
      const PushNotificationToken(
        token: 'token-123',
        platform: PushNotificationPlatform.ios,
      );

  @override
  Stream<PushNotificationToken> get onTokenRefresh => const Stream.empty();

  @override
  Stream<Map<String, dynamic>> get onMessageReceivedForeground =>
      const Stream.empty();

  @override
  Stream<Map<String, dynamic>> get onMessageOpenedApp => const Stream.empty();
}

class _FakeRegistrationGateway implements PushNotificationRegistrationGateway {
  _FakeRegistrationGateway({this.throwOnRegister = false});

  final bool throwOnRegister;
  int registerCalls = 0;

  @override
  Future<void> registerToken({
    required String token,
    required String platform,
    required String authorizationHeader,
  }) async {
    registerCalls += 1;
    if (throwOnRegister) {
      throw Exception('register exploded');
    }
  }
}

void main() {
  test('bindSession chama setupAndRegister via gateways internos', () async {
    final registrationGateway = _FakeRegistrationGateway();
    final pushGateway = _FakePushNotificationGateway();
    final binder = PushNotificationSessionBinder(
      pushGateway: pushGateway,
      registrationGateway: registrationGateway,
    );

    await binder.bindSession(authorizationHeader: 'Bearer abc');

    expect(registrationGateway.registerCalls, 1);
    expect(pushGateway.requestPermissionCalls, 0);
    expect(pushGateway.currentPermissionStatusCalls, 1);
  });

  test('bindSession não propaga exceções do service interno', () async {
    final binder = PushNotificationSessionBinder(
      pushGateway: _FakePushNotificationGateway(),
      registrationGateway: _FakeRegistrationGateway(throwOnRegister: true),
    );

    await expectLater(
      binder.bindSession(authorizationHeader: 'Bearer abc'),
      completes,
    );
  });
}
