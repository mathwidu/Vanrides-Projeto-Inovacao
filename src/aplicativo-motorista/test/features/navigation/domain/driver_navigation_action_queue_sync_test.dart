import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/domain/driver_trip_record.dart';
import 'package:vanrides_frontend_dev/features/navigation/domain/driver_navigation_action_queue.dart';
import 'package:vanrides_frontend_dev/features/navigation/domain/driver_navigation_action_queue_sync.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_record.dart';

void main() {
  final createdAt = DateTime.utc(2026, 7, 7, 12);

  test(
    'sync sends queued actions in order and removes successful items',
    () async {
      final sent = <String>[];
      final queue = _queue(createdAt);

      final result = await const DriverNavigationActionQueueSync().sync(
        queue: queue,
        send: (action) async {
          sent.add(action.id);
          return _trip(action.tripId);
        },
      );

      expect(sent, ['91:80:completeStop', '91:81:noShow']);
      expect(result.syncedCount, 2);
      expect(result.remainingQueue.pendingCount, 0);
      expect(result.lastSyncedTrip?.id, 91);
      expect(result.failedAction, isNull);
    },
  );

  test(
    'sync stops on first failure and keeps failed plus later actions',
    () async {
      final sent = <String>[];
      final queue = _queue(createdAt).enqueue(
        tripId: 91,
        stopId: 82,
        stopName: 'Carla',
        type: DriverNavigationQueuedActionType.skipStop,
        createdAt: createdAt.add(const Duration(minutes: 2)),
      );

      final result = await const DriverNavigationActionQueueSync().sync(
        queue: queue,
        send: (action) async {
          sent.add(action.id);
          if (action.id == '91:81:noShow') {
            throw StateError('offline');
          }
          return _trip(action.tripId);
        },
      );

      expect(sent, ['91:80:completeStop', '91:81:noShow']);
      expect(result.syncedCount, 1);
      expect(result.failedAction?.id, '91:81:noShow');
      expect(result.remainingQueue.actions.map((action) => action.id), [
        '91:81:noShow',
        '91:82:skipStop',
      ]);
    },
  );
}

DriverNavigationActionQueue _queue(DateTime createdAt) {
  return const DriverNavigationActionQueue.empty()
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
}

DriverTripRecord _trip(int id) {
  return DriverTripRecord(
    id: id,
    rotaBaseId: 4,
    rotaNome: 'Rota Centro',
    servicoNome: 'Linha Manha',
    dataOperacao: DateTime(2026, 7, 7),
    status: TripTrackingStatus.inProgress,
    rotaVersao: 3,
    etaMinutosAteProximaParada: 9,
    etaAtualizadoEm: DateTime(2026, 7, 7, 7, 30),
    motorista: const TripTrackingDriverRecord(id: 7, nome: 'Motorista QA'),
    proximaParada: null,
    paradas: const [],
    rotaGeografica: null,
  );
}
