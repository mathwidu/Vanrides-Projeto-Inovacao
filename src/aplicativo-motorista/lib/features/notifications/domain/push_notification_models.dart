class PushNotificationToken {
  const PushNotificationToken({required this.token, required this.platform});

  final String token;
  final PushNotificationPlatform platform;
}

enum PushNotificationPlatform { ios, android, web, unknown }

enum PushPermissionStatus {
  granted,
  denied,
  notDetermined,
  provisional,
  unavailable,
}
