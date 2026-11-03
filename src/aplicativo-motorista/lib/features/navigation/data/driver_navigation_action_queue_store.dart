import 'dart:convert';

import 'package:vanrides_frontend_dev/app/session/app_session_store.dart';
import 'package:vanrides_frontend_dev/features/navigation/domain/driver_navigation_action_queue.dart';

abstract class DriverNavigationActionQueueStore {
  Future<DriverNavigationActionQueue> read();

  Future<void> write(DriverNavigationActionQueue queue);

  Future<DriverNavigationActionQueue> enqueue({
    required int tripId,
    required int stopId,
    required String stopName,
    required DriverNavigationQueuedActionType type,
    required DateTime createdAt,
  });
}

class SharedPreferencesDriverNavigationActionQueueStore
    implements DriverNavigationActionQueueStore {
  SharedPreferencesDriverNavigationActionQueueStore({
    AppLocalPreferenceStore preferenceStore = const AppLocalPreferenceStore(),
  }) : _preferenceStore = preferenceStore;

  static const storageKey = 'driverNavigation.actionQueue.v1';

  final AppLocalPreferenceStore _preferenceStore;

  @override
  Future<DriverNavigationActionQueue> read() async {
    final payload = await _preferenceStore.readString(storageKey);
    if (payload == null || payload.trim().isEmpty) {
      return const DriverNavigationActionQueue.empty();
    }

    try {
      final decoded = jsonDecode(payload);
      if (decoded is! Map<String, dynamic>) {
        throw const FormatException('Payload de fila local invalido.');
      }
      return DriverNavigationActionQueue.fromJson(decoded);
    } catch (_) {
      await _preferenceStore.remove(storageKey);
      return const DriverNavigationActionQueue.empty();
    }
  }

  @override
  Future<void> write(DriverNavigationActionQueue queue) async {
    if (queue.actions.isEmpty) {
      await _preferenceStore.remove(storageKey);
      return;
    }
    await _preferenceStore.writeString(storageKey, jsonEncode(queue.toJson()));
  }

  @override
  Future<DriverNavigationActionQueue> enqueue({
    required int tripId,
    required int stopId,
    required String stopName,
    required DriverNavigationQueuedActionType type,
    required DateTime createdAt,
  }) async {
    final current = await read();
    final next = current.enqueue(
      tripId: tripId,
      stopId: stopId,
      stopName: stopName,
      type: type,
      createdAt: createdAt,
    );
    await write(next);
    return next;
  }
}
