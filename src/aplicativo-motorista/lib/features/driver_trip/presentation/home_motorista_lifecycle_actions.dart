part of 'home_motorista_screen.dart';

extension _HomeMotoristaLifecycleActions on _HomeMotoristaScreenState {
  Future<void> _runLifecycleAction(
    Future<DriverTripRecord> Function() action, {
    required String errorFallback,
    DriverNavigationQueuedActionType? queueOnFailureType,
  }) async {
    if (!_uiPresenter.startLifecycleAction()) {
      return;
    }

    try {
      final updatedTrip = await action();
      if (!mounted) {
        return;
      }
      _sessionPresenter.syncTripSnapshot(updatedTrip);
      _uiPresenter.finishRouteEditing();
      _wireRouteEventsIfPossible(updatedTrip.id);
    } catch (error) {
      if (!mounted) {
        return;
      }
      if (queueOnFailureType != null && error is! TripTrackingException) {
        final queued = await _enqueueNavigationAction(queueOnFailureType);
        if (!mounted) {
          return;
        }
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              queued
                  ? AppStrings.motorista.navigationActionQueuedOffline
                  : errorFallback,
            ),
          ),
        );
        return;
      }
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            error is TripTrackingException ? error.message : errorFallback,
          ),
        ),
      );
    } finally {
      if (mounted) {
        _uiPresenter.finishLifecycleAction();
      }
    }
  }

  Future<void> _iniciarCorrida() async {
    final trip = _trip;
    if (trip == null) {
      return;
    }
    if (!_uiPresenter.startLifecycleAction()) {
      return;
    }

    try {
      final updatedTrip = await _tripGateway.startTrip(trip.id);
      if (!mounted) {
        return;
      }
      _sessionPresenter.syncTripSnapshot(updatedTrip);
      _uiPresenter.finishRouteEditing();
      _tripRefreshedFromLiveAnchor = false;
      _wireRouteEventsIfPossible(updatedTrip.id);
      final sharingStarted = await _startLiveSharing();
      if (!mounted) {
        return;
      }
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            sharingStarted
                ? AppStrings.motorista.tripStartedAndLiveSignalEnabled
                : AppStrings.motorista.tripStartedEnableLocationForMap,
          ),
        ),
      );
    } catch (error) {
      if (!mounted) {
        return;
      }
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            error is TripTrackingException
                ? error.message
                : AppStrings.motorista.unableStartTrip,
          ),
        ),
      );
    } finally {
      if (mounted) {
        _uiPresenter.finishLifecycleAction();
      }
    }
  }

  Future<void> _concluirProximaParada() async {
    final trip = _trip;
    final nextStop = trip?.proximaParada;
    if (trip == null || nextStop == null) {
      return;
    }
    await _runLifecycleAction(
      () => _tripGateway.completeStop(trip.id, nextStop.id),
      errorFallback: AppStrings.motorista.unableCompleteNextStop,
      queueOnFailureType: DriverNavigationQueuedActionType.completeStop,
    );
  }

  Future<void> _pularProximaParada() async {
    final trip = _trip;
    final nextStop = trip?.proximaParada;
    if (trip == null || nextStop == null) {
      return;
    }
    final confirmed = await showDriverLifecycleConfirmationDialog(
      context,
      title: AppStrings.motorista.confirmarPularParadaTitulo,
      message: AppStrings.motorista.confirmarPularParadaMensagem(
        nextStop.passageiroNome,
      ),
    );
    if (!mounted || !confirmed) {
      return;
    }
    await _runLifecycleAction(
      () => _tripGateway.skipStop(trip.id, nextStop.id),
      errorFallback: AppStrings.motorista.unableSkipNextStop,
      queueOnFailureType: DriverNavigationQueuedActionType.skipStop,
    );
  }

  Future<void> _marcarNoShowProximaParada() async {
    final trip = _trip;
    final nextStop = trip?.proximaParada;
    if (trip == null || nextStop == null) {
      return;
    }
    final confirmed = await showDriverLifecycleConfirmationDialog(
      context,
      title: AppStrings.motorista.confirmarNaoEmbarcouTitulo,
      message: AppStrings.motorista.confirmarNaoEmbarcouMensagem(
        nextStop.passageiroNome,
      ),
    );
    if (!mounted || !confirmed) {
      return;
    }
    await _runLifecycleAction(
      () => _tripGateway.noShowStop(trip.id, nextStop.id),
      errorFallback: AppStrings.motorista.unableNoShowNextStop,
      queueOnFailureType: DriverNavigationQueuedActionType.noShow,
    );
  }

  Future<void> _encerrarCorrida() async {
    final trip = _trip;
    if (trip == null) {
      return;
    }
    final confirmed = await showDriverLifecycleConfirmationDialog(
      context,
      title: AppStrings.motorista.confirmarEncerrarCorridaTitulo,
      message: AppStrings.motorista.confirmarEncerrarCorridaMensagem,
    );
    if (!mounted || !confirmed) {
      return;
    }
    await _runLifecycleAction(
      () => _tripGateway.endTrip(trip.id),
      errorFallback: AppStrings.motorista.unableEndTrip,
    );
    await _stopLiveSharing();
  }
}
