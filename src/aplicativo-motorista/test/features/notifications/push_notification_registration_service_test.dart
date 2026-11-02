import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:vanrides_frontend_dev/features/notifications/data/noop_push_notification_gateway.dart';
import 'package:vanrides_frontend_dev/features/notifications/data/push_notification_gateway.dart';
import 'package:vanrides_frontend_dev/features/notifications/data/push_notification_registration_service.dart';
import 'package:vanrides_frontend_dev/features/notifications/domain/push_notification_models.dart';

class _FakePushNotificationGateway implements PushNotificationGateway {
  _FakePushNotificationGateway({
    required this.permissionStatus,
    required this.token,
  });

  final PushPermissionStatus permissionStatus;
  final PushNotificationToken? token;
  int requestPermissionCalls = 0;
  int currentPermissionStatusCalls = 0;

  @override
  Future<PushPermissionStatus> requestPermission() async {
    requestPermissionCalls += 1;
    return permissionStatus;
  }

  @override
  Future<PushPermissionStatus> currentPermissionStatus() async {
    currentPermissionStatusCalls += 1;
    return permissionStatus;
  }

  @override
  Future<PushNotificationToken?> getToken() async => token;

  @override
  Stream<PushNotificationToken> get onTokenRefresh => const Stream.empty();

  @override
  Stream<Map<String, dynamic>> get onMessageReceivedForeground =>
      const Stream.empty();

  @override
  Stream<Map<String, dynamic>> get onMessageOpenedApp => const Stream.empty();
}

class _FakePushNotificationRegistrationGateway
    implements PushNotificationRegistrationGateway {
  int registerCalls = 0;
  String? lastToken;
  String? lastPlatform;
  String? lastAuthorizationHeader;

  @override
  Future<void> registerToken({
    required String token,
    required String platform,
    required String authorizationHeader,
  }) async {
    registerCalls += 1;
    lastToken = token;
    lastPlatform = platform;
    lastAuthorizationHeader = authorizationHeader;
  }
}

void main() {
  group('PushNotificationRegistrationService', () {
    test('setupAndRegister retorna false quando permissao negada', () async {
      final service = PushNotificationRegistrationService(
        pushGateway: _FakePushNotificationGateway(
          permissionStatus: PushPermissionStatus.denied,
          token: const PushNotificationToken(
            token: 'token-123',
            platform: PushNotificationPlatform.ios,
          ),
        ),
        registrationGateway: _FakePushNotificationRegistrationGateway(),
      );

      final result = await service.setupAndRegister(
        authorizationHeader: 'Bearer test',
      );

      expect(result, isFalse);
    });

    test('setupAndRegister retorna false quando token é null', () async {
      final registrationGateway = _FakePushNotificationRegistrationGateway();
      final service = PushNotificationRegistrationService(
        pushGateway: _FakePushNotificationGateway(
          permissionStatus: PushPermissionStatus.granted,
          token: null,
        ),
        registrationGateway: registrationGateway,
      );

      final result = await service.setupAndRegister(
        authorizationHeader: 'Bearer test',
      );

      expect(result, isFalse);
      expect(registrationGateway.registerCalls, 0);
    });

    test(
      'setupAndRegister retorna true e registra token quando permissao concedida',
      () async {
        final registrationGateway = _FakePushNotificationRegistrationGateway();
        final pushGateway = _FakePushNotificationGateway(
          permissionStatus: PushPermissionStatus.granted,
          token: const PushNotificationToken(
            token: 'token-abc',
            platform: PushNotificationPlatform.android,
          ),
        );
        final service = PushNotificationRegistrationService(
          pushGateway: pushGateway,
          registrationGateway: registrationGateway,
        );

        final result = await service.setupAndRegister(
          authorizationHeader: 'Bearer live-token',
        );

        expect(result, isTrue);
        expect(pushGateway.requestPermissionCalls, 0);
        expect(pushGateway.currentPermissionStatusCalls, 1);
        expect(registrationGateway.registerCalls, 1);
        expect(registrationGateway.lastToken, 'token-abc');
        expect(registrationGateway.lastPlatform, 'android');
        expect(
          registrationGateway.lastAuthorizationHeader,
          'Bearer live-token',
        );
      },
    );

    test('requestPermissionAndRegister usa opt-in explicito', () async {
      final registrationGateway = _FakePushNotificationRegistrationGateway();
      final pushGateway = _FakePushNotificationGateway(
        permissionStatus: PushPermissionStatus.provisional,
        token: const PushNotificationToken(
          token: 'token-opt-in',
          platform: PushNotificationPlatform.ios,
        ),
      );
      final service = PushNotificationRegistrationService(
        pushGateway: pushGateway,
        registrationGateway: registrationGateway,
      );

      final result = await service.requestPermissionAndRegister(
        authorizationHeader: 'Bearer live-token',
      );

      expect(result, isTrue);
      expect(pushGateway.requestPermissionCalls, 1);
      expect(pushGateway.currentPermissionStatusCalls, 0);
      expect(registrationGateway.lastToken, 'token-opt-in');
    });
  });

  group('NoopPushNotificationGateway', () {
    test('retorna valores neutros e streams vazias', () async {
      final gateway = NoopPushNotificationGateway();

      expect(
        await gateway.requestPermission(),
        PushPermissionStatus.unavailable,
      );
      expect(
        await gateway.currentPermissionStatus(),
        PushPermissionStatus.unavailable,
      );
      expect(await gateway.getToken(), isNull);
      expect(await gateway.onTokenRefresh.isEmpty, isTrue);
      expect(await gateway.onMessageReceivedForeground.isEmpty, isTrue);
      expect(await gateway.onMessageOpenedApp.isEmpty, isTrue);
    });
  });

  group('PushNotificationRegistrationHttpGateway', () {
    test('posta para /devices/fcm-token com headers corretos', () async {
      late http.Request capturedRequest;
      final client = MockClient((request) async {
        capturedRequest = request;
        return http.Response('', 204);
      });

      final gateway = PushNotificationRegistrationHttpGateway(client: client);

      await gateway.registerToken(
        token: 'token-123',
        platform: 'ios',
        authorizationHeader: 'Bearer abc',
      );

      expect(capturedRequest.url.path, '/devices/fcm-token');
      expect(capturedRequest.headers['Authorization'], 'Bearer abc');
      expect(
        capturedRequest.headers['Content-Type'],
        startsWith('application/json'),
      );
    });

    test('inclui token e platform no body json', () async {
      late http.Request capturedRequest;
      final client = MockClient((request) async {
        capturedRequest = request;
        return http.Response('', 204);
      });

      final gateway = PushNotificationRegistrationHttpGateway(client: client);

      await gateway.registerToken(
        token: 'token-xyz',
        platform: 'android',
        authorizationHeader: 'Bearer abc',
      );

      expect(jsonDecode(capturedRequest.body), {
        'token': 'token-xyz',
        'platform': 'android',
      });
    });

    test('nao lança excecao quando o servidor retorna 500', () async {
      final client = MockClient((_) async => http.Response('error', 500));
      final gateway = PushNotificationRegistrationHttpGateway(client: client);

      await expectLater(
        gateway.registerToken(
          token: 'token-123',
          platform: 'ios',
          authorizationHeader: 'Bearer abc',
        ),
        completes,
      );
    });

    test('nao lança excecao quando o client lança ClientException', () async {
      final client = MockClient(
        (_) async => throw http.ClientException('network down'),
      );
      final gateway = PushNotificationRegistrationHttpGateway(client: client);

      await expectLater(
        gateway.registerToken(
          token: 'token-123',
          platform: 'ios',
          authorizationHeader: 'Bearer abc',
        ),
        completes,
      );
    });
  });
}
