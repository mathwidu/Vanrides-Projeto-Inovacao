import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:vanrides_frontend_dev/features/navigation/data/driver_navigation_action_queue_store.dart';
import 'package:vanrides_frontend_dev/features/navigation/domain/driver_navigation_action_queue.dart';

void main() {
  final createdAt = DateTime.utc(2026, 7, 7, 12);

  setUp(() {
    SharedPreferences.setMockInitialValues({});
  });

  test('persists and restores queued navigation actions', () async {
    final store = SharedPreferencesDriverNavigationActionQueueStore();
    final queue = const DriverNavigationActionQueue.empty().enqueue(
      tripId: 91,
      stopId: 80,
      stopName: 'Ana Clara',
      type: DriverNavigationQueuedActionType.completeStop,
      createdAt: createdAt,
    );

    await store.write(queue);

    final restored = await store.read();

    expect(restored.pendingCount, 1);
    expect(restored.actions.single.id, '91:80:completeStop');
    expect(restored.actions.single.stopName, 'Ana Clara');
    expect(restored.actions.single.createdAt, createdAt);
  });

  test(
    'enqueue updates the persisted queue with deterministic dedupe',
    () async {
      final store = SharedPreferencesDriverNavigationActionQueueStore();

      await store.enqueue(
        tripId: 91,
        stopId: 80,
        stopName: 'Ana Clara',
        type: DriverNavigationQueuedActionType.skipStop,
        createdAt: createdAt,
      );
      await store.enqueue(
        tripId: 91,
        stopId: 80,
        stopName: 'Ana Clara',
        type: DriverNavigationQueuedActionType.skipStop,
        createdAt: createdAt.add(const Duration(seconds: 30)),
      );

      final restored = await store.read();

      expect(restored.pendingCount, 1);
      expect(restored.actions.single.id, '91:80:skipStop');
      expect(restored.actions.single.createdAt, createdAt);
    },
  );

  test(
    'corrupted persisted payload returns an empty queue and clears storage',
    () async {
      SharedPreferences.setMockInitialValues({
        SharedPreferencesDriverNavigationActionQueueStore.storageKey: '{nope',
      });
      final store = SharedPreferencesDriverNavigationActionQueueStore();

      final restored = await store.read();
      final prefs = await SharedPreferences.getInstance();

      expect(restored.pendingCount, 0);
      expect(
        prefs.getString(
          SharedPreferencesDriverNavigationActionQueueStore.storageKey,
        ),
        isNull,
      );
    },
  );
}
