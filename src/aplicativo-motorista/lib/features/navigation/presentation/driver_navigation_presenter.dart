import 'package:vanrides_frontend_dev/features/driver_trip/domain/driver_trip_record.dart';
import 'package:vanrides_frontend_dev/features/navigation/domain/driver_navigation_instruction_copy.dart';
import 'package:vanrides_frontend_dev/features/navigation/domain/driver_navigation_models.dart';

class DriverNavigationPresenter {
  const DriverNavigationPresenter();

  DriverNavigationViewModel present(DriverNavigationSession session) {
    final stop = session.trip.proximaParada;
    final canUseInternalNavigation = _canUseInternalNavigation(session);
    final mode = _modeFor(session, canUseInternalNavigation);
    final instructionCopy = DriverNavigationInstructionCopy.fromInstruction(
      session.currentInstruction,
    );
    final instructionText = _instructionTextFor(session, mode, instructionCopy);

    return DriverNavigationViewModel(
      mode: mode,
      instructionText: instructionText,
      distanceLabel: _distanceLabelFor(session),
      helperText: _helperTextFor(session, mode),
      nextStopLabel: 'Proxima parada',
      nextStopName: stop?.passageiroNome ?? 'Sem proxima parada',
      nextStopSummary: _nextStopSummaryFor(session.trip, stop),
      primaryActionLabel: 'Confirmar embarque',
      secondaryActions: const [
        DriverNavigationSecondaryAction(
          type: DriverNavigationSecondaryActionType.skipStop,
          label: 'Pular',
        ),
        DriverNavigationSecondaryAction(
          type: DriverNavigationSecondaryActionType.noShow,
          label: 'Faltou',
        ),
      ],
      fallbackLabel: 'Fallback',
      statusLabel: _statusLabelFor(session, mode),
      voicePrompt: instructionCopy?.voiceText,
      instructionWasNormalized: instructionCopy?.wasNormalized ?? false,
      queuedActionSummaryLabel: session.actionQueueSummary?.statusLabel,
      queuedActionDetailLabel: session.actionQueueSummary?.detailLabel,
      queuedActionHelperText: session.actionQueueSummary?.helperText,
      fallbackIsPrimary: mode != DriverNavigationMode.internal,
      canUseInternalNavigation: canUseInternalNavigation,
    );
  }

  bool _canUseInternalNavigation(DriverNavigationSession session) {
    return session.offlineState == DriverNavigationOfflineState.ready &&
        session.coverageStatus == DriverNavigationCoverageStatus.validated &&
        !session.offRoute;
  }

  DriverNavigationMode _modeFor(
    DriverNavigationSession session,
    bool canUseInternalNavigation,
  ) {
    if (session.offRoute ||
        session.offlineState == DriverNavigationOfflineState.partial) {
      return DriverNavigationMode.safeMode;
    }
    if (!canUseInternalNavigation) {
      return DriverNavigationMode.externalFallback;
    }
    return DriverNavigationMode.internal;
  }

  String _instructionTextFor(
    DriverNavigationSession session,
    DriverNavigationMode mode,
    DriverNavigationInstructionCopy? instructionCopy,
  ) {
    if (mode == DriverNavigationMode.safeMode && session.offRoute) {
      return 'Fora da rota planejada';
    }
    if (mode == DriverNavigationMode.safeMode &&
        session.offlineState == DriverNavigationOfflineState.partial) {
      return 'Siga apenas no corredor validado';
    }
    if (mode == DriverNavigationMode.externalFallback) {
      return 'Use o fallback para seguir com seguranca';
    }
    final instruction = instructionCopy?.text.trim();
    if (instruction != null && instruction.isNotEmpty) {
      return instruction;
    }
    return 'Siga para a proxima parada';
  }

  String _distanceLabelFor(DriverNavigationSession session) {
    final meters = session.currentInstruction?.distanceMeters;
    if (meters != null) {
      if (meters < 1000) {
        return 'Em $meters m';
      }
      final kilometers = meters / 1000;
      return 'Em ${kilometers.toStringAsFixed(1)} km';
    }

    final minutes = session.trip.etaMinutosAteProximaParada;
    if (minutes != null) {
      return '$minutes min';
    }
    return '--';
  }

  String _helperTextFor(
    DriverNavigationSession session,
    DriverNavigationMode mode,
  ) {
    if (mode == DriverNavigationMode.safeMode) {
      return 'Use o fallback se a rota sair do corredor validado.';
    }
    if (mode == DriverNavigationMode.externalFallback) {
      return _fallbackReasonHelperText(session.fallbackReason) ??
          'Fallback externo e o caminho mais seguro agora.';
    }
    return 'Siga com atencao ate a proxima parada.';
  }

  String? _fallbackReasonHelperText(DriverNavigationFallbackReason? reason) {
    switch (reason) {
      case DriverNavigationFallbackReason.routeNotValidated:
        return 'Rota ainda em validacao. Abra o mapa externo para seguir.';
      case DriverNavigationFallbackReason.routingProviderUnavailable:
        return 'Roteirizacao interna indisponivel agora. Abra o mapa externo.';
      case DriverNavigationFallbackReason.offlinePackMissing:
        return 'Pacote offline ausente. Abra o mapa externo para seguir.';
      case DriverNavigationFallbackReason.offlinePackOutdated:
        return 'Pacote offline desatualizado. Abra o mapa externo.';
      case DriverNavigationFallbackReason.offlinePackPartial:
        return 'Pacote offline parcial. Use o mapa externo fora do corredor.';
      case DriverNavigationFallbackReason.offlinePackFailed:
        return 'Pacote offline falhou. Abra o mapa externo para seguir.';
      case DriverNavigationFallbackReason.instructionMissing:
        return 'Instrucao interna indisponivel. Abra o mapa externo.';
      case DriverNavigationFallbackReason.unknown:
      case null:
        return null;
    }
  }

  String _statusLabelFor(
    DriverNavigationSession session,
    DriverNavigationMode mode,
  ) {
    if (mode == DriverNavigationMode.safeMode &&
        session.offlineState == DriverNavigationOfflineState.partial &&
        !session.offRoute) {
      return 'Pacote offline parcial';
    }
    if (mode == DriverNavigationMode.safeMode) {
      return 'Modo seguro';
    }
    if (session.offlineState == DriverNavigationOfflineState.ready) {
      return _coverageStatusLabelFor(session.coverageStatus);
    }
    switch (session.offlineState) {
      case DriverNavigationOfflineState.missing:
        return 'Pacote offline ausente';
      case DriverNavigationOfflineState.outdated:
        return 'Pacote offline desatualizado';
      case DriverNavigationOfflineState.partial:
        return 'Pacote offline parcial';
      case DriverNavigationOfflineState.failed:
        return 'Pacote offline falhou';
      case DriverNavigationOfflineState.ready:
        return 'Offline pronto';
    }
  }

  String _coverageStatusLabelFor(DriverNavigationCoverageStatus status) {
    switch (status) {
      case DriverNavigationCoverageStatus.validated:
        return 'Offline pronto';
      case DriverNavigationCoverageStatus.observation:
        return 'Cobertura em observacao';
      case DriverNavigationCoverageStatus.fallback:
        return 'Fallback externo recomendado';
      case DriverNavigationCoverageStatus.blocked:
        return 'Navegacao interna indisponivel';
    }
  }

  String _nextStopSummaryFor(
    DriverTripRecord trip,
    DriverTripStopRecord? stop,
  ) {
    if (stop == null) {
      return trip.nomeOperacional;
    }
    return 'Parada ${stop.ordem} - ${trip.nomeOperacional}';
  }
}
