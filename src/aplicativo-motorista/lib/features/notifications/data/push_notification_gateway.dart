import '../domain/push_notification_models.dart';

abstract class PushNotificationGateway {
  Future<PushPermissionStatus> requestPermission();
  Future<PushPermissionStatus> currentPermissionStatus();
  Future<PushNotificationToken?> getToken();
  Stream<PushNotificationToken> get onTokenRefresh;
  Stream<Map<String, dynamic>> get onMessageReceivedForeground;
  Stream<Map<String, dynamic>> get onMessageOpenedApp;
}
