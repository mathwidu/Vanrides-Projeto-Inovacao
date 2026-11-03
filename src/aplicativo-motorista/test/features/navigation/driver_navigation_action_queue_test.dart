import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/navigation/domain/driver_navigation_action_queue.dart';

void main() {
  final createdAt = DateTime.utc(2026, 7, 7, 12);

  test('enqueues a complete stop action with a local pending summary', () {
    final queue = const DriverNavigationActionQueue.empty().enqueue(
      tripId: 91,
      stopId: 80,
      stopName: 'Ana Clara',
      type: DriverNavigationQueuedActionType.completeStop,
      createdAt: createdAt,
    );

    expect(queue.pendingCount, 1);
    expect(queue.hasPendingForStop(tripId: 91, stopId: 80), isTrue);

    final action = queue.actions.single;
    expect(action.id, '91:80:completeStop');
    expect(action.label, 'Confirmar embarque');
    expect(action.status, DriverNavigationQueuedActionStatus.pending);
    expect(action.createdAt, createdAt);

    final summary = queue.pendingSummary;
    expect(summary, isNotNull);
    expect(summary!.statusLabel, '1 acao local pendente');
    expect(summary.detailLabel, 'Confirmar embarque de Ana Clara');
    expect(summary.helperText, 'Sera enviada quando a conexao estiver segura.');
  });

  test(
    'deduplicates the same trip stop action without changing the first tap',
    () {
      final firstTap = createdAt;
      final secondTap = createdAt.add(const Duration(seconds: 30));

      final queue = const DriverNavigationActionQueue.empty()
          .enqueue(
            tripId: 91,
            stopId: 80,
            stopName: 'Ana Clara',
            type: DriverNavigationQueuedActionType.skipStop,
            createdAt: firstTap,
          )
          .enqueue(
            tripId: 91,
            stopId: 80,
            stopName: 'Ana Clara',
            type: DriverNavigationQueuedActionType.skipStop,
            createdAt: secondTap,
          );

      expect(queue.pendingCount, 1);
      expect(queue.actions.single.id, '91:80:skipStop');
      expect(queue.actions.single.createdAt, firstTap);
      expect(queue.actions.single.stopName, 'Ana Clara');
    },
  );

  test('keeps distinct queued action types ordered for driver review', () {
    final queue = const DriverNavigationActionQueue.empty()
        .enqueue(
          tripId: 91,
          stopId: 80,
          stopName: 'Ana Clara',
          type: DriverNavigationQueuedActionType.skipStop,
          createdAt: createdAt,
        )
        .enqueue(
          tripId: 91,
          stopId: 81,
          stopName: 'Bruno',
          type: DriverNavigationQueuedActionType.noShow,
          createdAt: createdAt.add(const Duration(minutes: 1)),
        );

    expect(queue.pendingCount, 2);
    expect(queue.actions.map((action) => action.id), [
      '91:80:skipStop',
      '91:81:noShow',
    ]);
    expect(queue.actions.map((action) => action.label), ['Pular', 'Faltou']);
    expect(queue.pendingSummary!.statusLabel, '2 acoes locais pendentes');
    expect(queue.pendingSummary!.detailLabel, 'Proxima: Pular de Ana Clara');
  });

  test('serializes and restores a persisted queue payload', () {
    final queue = const DriverNavigationActionQueue.empty()
        .enqueue(
          tripId: 91,
          stopId: 80,
          stopName: 'Ana Clara',
          type: DriverNavigationQueuedActionType.completeStop,
          createdAt: createdAt,
        )
        .enqueue(
          tripId: 91,
          stopId: 81,
          stopName: 'Bruno',
          type: DriverNavigationQueuedActionType.noShow,
          createdAt: createdAt.add(const Duration(minutes: 1)),
        );

    final restored = DriverNavigationActionQueue.fromJson(queue.toJson());

    expect(restored.actions.map((action) => action.id), [
      '91:80:completeStop',
      '91:81:noShow',
    ]);
    expect(restored.actions.first.stopName, 'Ana Clara');
    expect(restored.actions.first.createdAt, createdAt);
    expect(restored.pendingSummary!.statusLabel, '2 acoes locais pendentes');
  });

  test('removes synced actions by id without changing the remaining order', () {
    final queue = const DriverNavigationActionQueue.empty()
        .enqueue(
          tripId: 91,
          stopId: 80,
          stopName: 'Ana Clara',
          type: DriverNavigationQueuedActionType.completeStop,
          createdAt: createdAt,
        )
        .enqueue(
          tripId: 91,
          stopId: 81,
          stopName: 'Bruno',
          type: DriverNavigationQueuedActionType.noShow,
          createdAt: createdAt.add(const Duration(minutes: 1)),
        );

    final remaining = queue.removeAction('91:80:completeStop');

    expect(remaining.actions.map((action) => action.id), ['91:81:noShow']);
    expect(remaining.pendingCount, 1);
  });

  test('empty queue has no pending summary', () {
    const queue = DriverNavigationActionQueue.empty();

    expect(queue.actions, isEmpty);
    expect(queue.pendingCount, 0);
    expect(queue.pendingSummary, isNull);
    expect(queue.hasPendingForStop(tripId: 91, stopId: 80), isFalse);
  });
}
