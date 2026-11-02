import '../domain/push_notification_models.dart';
import 'push_notification_gateway.dart';

/// Implementação placeholder. Será substituída por
/// FirebaseMessagingPushNotificationGateway quando o setup do Firebase
/// estiver concluído (GoogleService-Info.plist + APNs + projeto FCM).
class NoopPushNotificationGateway implements PushNotificationGateway {
  @override
  Future<PushPermissionStatus> requestPermission() async =>
      PushPermissionStatus.unavailable;

  @override
  Future<PushPermissionStatus> currentPermissionStatus() async =>
      PushPermissionStatus.unavailable;

  @override
  Future<PushNotificationToken?> getToken() async => null;

  @override
  Stream<PushNotificationToken> get onTokenRefresh => const Stream.empty();

  @override
  Stream<Map<String, dynamic>> get onMessageReceivedForeground =>
      const Stream.empty();

  @override
  Stream<Map<String, dynamic>> get onMessageOpenedApp => const Stream.empty();
}
