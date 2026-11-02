import 'package:vanrides_frontend_dev/features/driver_trip/domain/driver_trip_record.dart';
import 'package:vanrides_frontend_dev/features/navigation/domain/driver_navigation_action_queue.dart';

typedef DriverNavigationQueuedActionSender =
    Future<DriverTripRecord> Function(DriverNavigationQueuedAction action);

class DriverNavigationActionQueueSync {
  const DriverNavigationActionQueueSync();

  Future<DriverNavigationActionQueueSyncResult> sync({
    required DriverNavigationActionQueue queue,
    required DriverNavigationQueuedActionSender send,
  }) async {
    final remaining = <DriverNavigationQueuedAction>[];
    var syncedCount = 0;
    DriverTripRecord? lastSyncedTrip;
    DriverNavigationQueuedAction? failedAction;
    Object? failure;

    for (final action in queue.actions) {
      if (failure != null) {
        remaining.add(action);
        continue;
      }

      try {
        lastSyncedTrip = await send(action);
        syncedCount += 1;
      } catch (error) {
        failure = error;
        failedAction = action;
        remaining.add(action);
      }
    }

    return DriverNavigationActionQueueSyncResult(
      syncedCount: syncedCount,
      remainingQueue: DriverNavigationActionQueue(actions: remaining),
      lastSyncedTrip: lastSyncedTrip,
      failedAction: failedAction,
      failure: failure,
    );
  }
}

class DriverNavigationActionQueueSyncResult {
  const DriverNavigationActionQueueSyncResult({
    required this.syncedCount,
    required this.remainingQueue,
    this.lastSyncedTrip,
    this.failedAction,
    this.failure,
  });

  final int syncedCount;
  final DriverNavigationActionQueue remainingQueue;
  final DriverTripRecord? lastSyncedTrip;
  final DriverNavigationQueuedAction? failedAction;
  final Object? failure;
}
