part of 'home_motorista_screen.dart';

extension _HomeMotoristaNavigationActionQueue on _HomeMotoristaScreenState {
  void _handleNavigationActionQueueLifecycleStateChanged(
    AppLifecycleState state,
  ) {
    if (state == AppLifecycleState.resumed) {
      unawaited(_loadNavigationActionQueue(syncAfterLoad: true));
    }
  }

  Future<void> _loadNavigationActionQueue({bool syncAfterLoad = false}) async {
    final queue = await _navigationActionQueueStore.read();
    if (!mounted) {
      return;
    }
    _navigationActionQueue.value = queue;
    if (syncAfterLoad) {
      await _syncNavigationActionQueue(showSnackBar: false);
    }
  }

  Future<bool> _enqueueNavigationAction(
    DriverNavigationQueuedActionType type,
  ) async {
    final trip = _trip;
    final stop = trip?.proximaParada;
    if (trip == null || stop == null) {
      return false;
    }

    final queue = await _navigationActionQueueStore.enqueue(
      tripId: trip.id,
      stopId: stop.id,
      stopName: stop.passageiroNome,
      type: type,
      createdAt: DateTime.now(),
    );
    if (!mounted) {
      return false;
    }
    _navigationActionQueue.value = queue;
    return queue.hasPendingForStop(tripId: trip.id, stopId: stop.id);
  }

  Future<void> _retryNavigationActionQueue() {
    return _syncNavigationActionQueue(showSnackBar: true);
  }

  Future<void> _syncNavigationActionQueue({required bool showSnackBar}) async {
    if (_navigationActionQueue.value.pendingCount == 0) {
      return;
    }
    if (!_uiPresenter.startLifecycleAction()) {
      return;
    }

    try {
      final result = await _navigationActionQueueSync.sync(
        queue: _navigationActionQueue.value,
        send: _sendQueuedNavigationAction,
      );
      await _navigationActionQueueStore.write(result.remainingQueue);
      if (!mounted) {
        return;
      }
      _navigationActionQueue.value = result.remainingQueue;
      final updatedTrip = result.lastSyncedTrip;
      if (updatedTrip != null) {
        _sessionPresenter.syncTripSnapshot(updatedTrip);
        _uiPresenter.finishRouteEditing();
        _wireRouteEventsIfPossible(updatedTrip.id);
      }
      if (showSnackBar) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(_retryQueueMessageFor(result))));
      }
    } finally {
      if (mounted) {
        _uiPresenter.finishLifecycleAction();
      }
    }
  }

  Future<DriverTripRecord> _sendQueuedNavigationAction(
    DriverNavigationQueuedAction action,
  ) {
    return switch (action.type) {
      DriverNavigationQueuedActionType.completeStop => _tripGateway
          .completeStop(action.tripId, action.stopId),
      DriverNavigationQueuedActionType.skipStop => _tripGateway.skipStop(
        action.tripId,
        action.stopId,
      ),
      DriverNavigationQueuedActionType.noShow => _tripGateway.noShowStop(
        action.tripId,
        action.stopId,
      ),
    };
  }

  String _retryQueueMessageFor(DriverNavigationActionQueueSyncResult result) {
    if (result.syncedCount > 0 && result.remainingQueue.pendingCount == 0) {
      return AppStrings.motorista.navigationActionQueueSynced;
    }
    if (result.syncedCount > 0) {
      return AppStrings.motorista.navigationActionQueuePartiallySynced;
    }
    return AppStrings.motorista.navigationActionQueueRetryFailed;
  }
}
