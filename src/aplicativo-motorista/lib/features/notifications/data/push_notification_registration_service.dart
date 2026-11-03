import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;

import '../../../services/api_constants.dart';
import '../domain/push_notification_models.dart';
import 'push_notification_gateway.dart';

abstract class PushNotificationRegistrationGateway {
  Future<void> registerToken({
    required String token,
    required String platform,
    required String authorizationHeader,
  });
}

class PushNotificationRegistrationHttpGateway
    implements PushNotificationRegistrationGateway {
  PushNotificationRegistrationHttpGateway({http.Client? client})
    : _client = client ?? http.Client();

  final http.Client _client;

  @override
  Future<void> registerToken({
    required String token,
    required String platform,
    required String authorizationHeader,
  }) async {
    final url = Uri.parse('$baseUrl/devices/fcm-token');
    try {
      final response = await _client.post(
        url,
        headers: {
          'Content-Type': 'application/json',
          'Authorization': authorizationHeader,
        },
        body: jsonEncode({'token': token, 'platform': platform}),
      );

      if (response.statusCode >= 400) {
        debugPrint('[push] registerToken falhou: HTTP ${response.statusCode}');
      }
    } catch (error) {
      debugPrint('[push] registerToken erro de rede: $error');
    }
  }
}

class PushNotificationRegistrationService {
  PushNotificationRegistrationService({
    required PushNotificationGateway pushGateway,
    required PushNotificationRegistrationGateway registrationGateway,
  }) : _pushGateway = pushGateway,
       _registrationGateway = registrationGateway;

  final PushNotificationGateway _pushGateway;
  final PushNotificationRegistrationGateway _registrationGateway;

  Future<bool> setupAndRegister({required String authorizationHeader}) async {
    final permission = await _pushGateway.currentPermissionStatus();
    return _registerIfAllowed(permission, authorizationHeader);
  }

  Future<bool> requestPermissionAndRegister({
    required String authorizationHeader,
  }) async {
    final permission = await _pushGateway.requestPermission();
    return _registerIfAllowed(permission, authorizationHeader);
  }

  Future<bool> _registerIfAllowed(
    PushPermissionStatus permission,
    String authorizationHeader,
  ) async {
    if (permission != PushPermissionStatus.granted &&
        permission != PushPermissionStatus.provisional) {
      return false;
    }

    final token = await _pushGateway.getToken();
    if (token == null) {
      return false;
    }

    await _registrationGateway.registerToken(
      token: token.token,
      platform: token.platform.name,
      authorizationHeader: authorizationHeader,
    );
    return true;
  }
}
