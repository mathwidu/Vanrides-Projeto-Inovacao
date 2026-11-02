import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/notifications/domain/push_notification_action_plan.dart';

void main() {
  group('PushNotificationActionPlan', () {
    test('cobre eventos internos publicados pelo backend', () {
      expect(PushNotificationActionPlan.backendEventNames, {
        'TRIP_STATUS_CHANGED',
        'STOP_STATUS_CHANGED',
        'PRESENCE_STATUS_CHANGED',
        'SUPPORT_TICKET_MESSAGE_CREATED',
      });
    });

    test('define texto, acao e destino para todos os papeis operacionais', () {
      final roles =
          PushNotificationActionPlan.notifications
              .map((notification) => notification.role)
              .toSet();

      expect(roles, {
        PushNotificationRole.passenger,
        PushNotificationRole.guardian,
        PushNotificationRole.driver,
        PushNotificationRole.company,
      });

      for (final notification in PushNotificationActionPlan.notifications) {
        expect(notification.title.trim(), isNotEmpty);
        expect(notification.body.trim(), isNotEmpty);
        expect(notification.actionLabel.trim(), isNotEmpty);
        expect(notification.destinationLabel.trim(), isNotEmpty);
        expect(notification.backendEvent.backendName.trim(), isNotEmpty);
      }
    });

    test(
      'mantem responsavel restrito ao acompanhamento da pessoa vinculada',
      () {
        final guardianNotifications = PushNotificationActionPlan.notifications
            .where(
              (notification) =>
                  notification.role == PushNotificationRole.guardian,
            );

        expect(guardianNotifications, isNotEmpty);
        expect(
          guardianNotifications.every(
            (notification) => notification.body.contains('pessoa acompanhada'),
          ),
          isTrue,
        );
      },
    );
  });
}
