import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/domain/driver_trip_record.dart';
import 'package:vanrides_frontend_dev/features/navigation/domain/driver_navigation_action_queue.dart';
import 'package:vanrides_frontend_dev/features/navigation/domain/driver_navigation_models.dart';
import 'package:vanrides_frontend_dev/features/navigation/presentation/driver_navigation_presenter.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_record.dart';

void main() {
  const instruction = DriverNavigationInstruction(
    id: 'inst-1',
    text: 'Vire a direita na Av. Brasil',
    voiceText: 'Em 300 metros, vire a direita na Avenida Brasil',
    distanceMeters: 300,
  );

  test(
    'ready session selects instruction, next stop and internal navigation',
    () {
      final viewModel = const DriverNavigationPresenter().present(
        DriverNavigationSession(
          trip: _trip(),
          currentInstruction: instruction,
          offlineState: DriverNavigationOfflineState.ready,
          coverageStatus: DriverNavigationCoverageStatus.validated,
        ),
      );

      expect(viewModel.mode, DriverNavigationMode.internal);
      expect(viewModel.instructionText, 'Vire a direita na Av. Brasil');
      expect(viewModel.distanceLabel, 'Em 300 m');
      expect(viewModel.nextStopName, 'Ana Clara');
      expect(viewModel.primaryActionLabel, 'Confirmar embarque');
      expect(viewModel.secondaryActions.map((action) => action.label), [
        'Pular',
        'Faltou',
      ]);
      expect(viewModel.secondaryActions.map((action) => action.type), [
        DriverNavigationSecondaryActionType.skipStop,
        DriverNavigationSecondaryActionType.noShow,
      ]);
      expect(viewModel.fallbackLabel, 'Fallback');
      expect(viewModel.fallbackIsPrimary, isFalse);
      expect(
        viewModel.voicePrompt,
        'Em 300 metros, vire a direita na Avenida Brasil',
      );
    },
  );

  test('missing offline pack makes external fallback primary', () {
    final viewModel = const DriverNavigationPresenter().present(
      DriverNavigationSession(
        trip: _trip(),
        currentInstruction: instruction,
        offlineState: DriverNavigationOfflineState.missing,
        coverageStatus: DriverNavigationCoverageStatus.validated,
      ),
    );

    expect(viewModel.mode, DriverNavigationMode.externalFallback);
    expect(viewModel.statusLabel, 'Pacote offline ausente');
    expect(
      viewModel.instructionText,
      'Use o fallback para seguir com seguranca',
    );
    expect(viewModel.fallbackIsPrimary, isTrue);
    expect(viewModel.canUseInternalNavigation, isFalse);
  });

  test(
    'external fallback explains route-plan reason without technical copy',
    () {
      final viewModel = const DriverNavigationPresenter().present(
        DriverNavigationSession(
          trip: _trip(),
          currentInstruction: instruction,
          offlineState: DriverNavigationOfflineState.missing,
          coverageStatus: DriverNavigationCoverageStatus.fallback,
          fallbackReason: DriverNavigationFallbackReason.routeNotValidated,
        ),
      );

      expect(viewModel.mode, DriverNavigationMode.externalFallback);
      expect(
        viewModel.helperText,
        'Rota ainda em validacao. Abra o mapa externo para seguir.',
      );
      expect(viewModel.helperText, isNot(contains('_')));
      expect(viewModel.helperText, isNot(contains('fixture')));
      expect(viewModel.fallbackIsPrimary, isTrue);
    },
  );

  test('unknown fallback reason keeps the generic fallback helper', () {
    final viewModel = const DriverNavigationPresenter().present(
      DriverNavigationSession(
        trip: _trip(),
        currentInstruction: instruction,
        offlineState: DriverNavigationOfflineState.missing,
        coverageStatus: DriverNavigationCoverageStatus.fallback,
        fallbackReason: DriverNavigationFallbackReason.unknown,
      ),
    );

    expect(
      viewModel.helperText,
      'Fallback externo e o caminho mais seguro agora.',
    );
  });

  test(
    'partial offline pack enters safe mode without overclaiming reroute',
    () {
      final viewModel = const DriverNavigationPresenter().present(
        DriverNavigationSession(
          trip: _trip(),
          currentInstruction: instruction,
          offlineState: DriverNavigationOfflineState.partial,
          coverageStatus: DriverNavigationCoverageStatus.validated,
          offRoute: true,
        ),
      );

      expect(viewModel.mode, DriverNavigationMode.safeMode);
      expect(viewModel.statusLabel, 'Modo seguro');
      expect(viewModel.instructionText, 'Fora da rota planejada');
      expect(viewModel.helperText, contains('fallback'));
      expect(viewModel.canUseInternalNavigation, isFalse);
    },
  );

  test('partial offline pack keeps its offline status visible on route', () {
    final viewModel = const DriverNavigationPresenter().present(
      DriverNavigationSession(
        trip: _trip(),
        currentInstruction: instruction,
        offlineState: DriverNavigationOfflineState.partial,
        coverageStatus: DriverNavigationCoverageStatus.validated,
      ),
    );

    expect(viewModel.mode, DriverNavigationMode.safeMode);
    expect(viewModel.statusLabel, 'Pacote offline parcial');
    expect(viewModel.instructionText, 'Siga apenas no corredor validado');
    expect(viewModel.fallbackIsPrimary, isTrue);
    expect(viewModel.canUseInternalNavigation, isFalse);
  });

  test(
    'non validated coverage blocks internal navigation with clear status',
    () {
      final scenarios = {
        DriverNavigationCoverageStatus.observation: 'Cobertura em observacao',
        DriverNavigationCoverageStatus.fallback: 'Fallback externo recomendado',
        DriverNavigationCoverageStatus.blocked:
            'Navegacao interna indisponivel',
      };

      for (final entry in scenarios.entries) {
        final viewModel = const DriverNavigationPresenter().present(
          DriverNavigationSession(
            trip: _trip(),
            currentInstruction: instruction,
            offlineState: DriverNavigationOfflineState.ready,
            coverageStatus: entry.key,
          ),
        );

        expect(viewModel.mode, DriverNavigationMode.externalFallback);
        expect(viewModel.statusLabel, entry.value);
        expect(viewModel.statusLabel, isNot('Offline pronto'));
        expect(viewModel.fallbackIsPrimary, isTrue);
        expect(viewModel.canUseInternalNavigation, isFalse);
      }
    },
  );

  test('missing instruction falls back to next stop guidance', () {
    final viewModel = const DriverNavigationPresenter().present(
      DriverNavigationSession(
        trip: _trip(),
        currentInstruction: null,
        offlineState: DriverNavigationOfflineState.ready,
        coverageStatus: DriverNavigationCoverageStatus.validated,
      ),
    );

    expect(viewModel.instructionText, 'Siga para a proxima parada');
    expect(viewModel.distanceLabel, '9 min');
    expect(viewModel.nextStopName, 'Ana Clara');
  });

  test('passes local action queue summary to the view model', () {
    final viewModel = const DriverNavigationPresenter().present(
      DriverNavigationSession(
        trip: _trip(),
        currentInstruction: instruction,
        offlineState: DriverNavigationOfflineState.ready,
        coverageStatus: DriverNavigationCoverageStatus.validated,
        actionQueueSummary: const DriverNavigationActionQueueSummary(
          pendingCount: 1,
          statusLabel: '1 acao local pendente',
          detailLabel: 'Confirmar embarque de Ana Clara',
          helperText: 'Sera enviada quando a conexao estiver segura.',
        ),
      ),
    );

    expect(viewModel.queuedActionSummaryLabel, '1 acao local pendente');
    expect(
      viewModel.queuedActionDetailLabel,
      'Confirmar embarque de Ana Clara',
    );
    expect(
      viewModel.queuedActionHelperText,
      'Sera enviada quando a conexao estiver segura.',
    );
  });

  test('normalizes raw provider instructions for panel and voice', () {
    const rawProviderInstruction = DriverNavigationInstruction(
      id: 'maps-raw-1',
      text: 'Turn right on Main Street',
      voiceText: 'Turn right on Main Street',
      distanceMeters: 220,
    );

    final viewModel = const DriverNavigationPresenter().present(
      DriverNavigationSession(
        trip: _trip(),
        currentInstruction: rawProviderInstruction,
        offlineState: DriverNavigationOfflineState.ready,
        coverageStatus: DriverNavigationCoverageStatus.validated,
      ),
    );

    expect(viewModel.mode, DriverNavigationMode.internal);
    expect(viewModel.instructionText, 'Converta a direita em Main Street.');
    expect(viewModel.voicePrompt, 'Converta a direita em Main Street.');
    expect(viewModel.distanceLabel, 'Em 220 m');
  });

  test('blocks overconfident copy before it reaches the driver', () {
    const riskyInstruction = DriverNavigationInstruction(
      id: 'maps-risk-1',
      text: 'Rota validada perfeita e sem risco.',
      voiceText: 'Rota garantida e sem risco para seguir.',
      distanceMeters: 180,
    );

    final viewModel = const DriverNavigationPresenter().present(
      DriverNavigationSession(
        trip: _trip(),
        currentInstruction: riskyInstruction,
        offlineState: DriverNavigationOfflineState.ready,
        coverageStatus: DriverNavigationCoverageStatus.validated,
      ),
    );

    expect(viewModel.mode, DriverNavigationMode.internal);
    expect(viewModel.instructionText, 'Siga pela rota planejada.');
    expect(viewModel.voicePrompt, 'Siga pela rota planejada.');
    expect(viewModel.instructionText, isNot(contains('perfeita')));
    expect(viewModel.voicePrompt, isNot(contains('sem risco')));
  });
}

DriverTripRecord _trip() {
  return DriverTripRecord(
    id: 91,
    rotaBaseId: 4,
    rotaNome: 'Rota Centro',
    servicoNome: 'Linha Manha',
    dataOperacao: DateTime(2026, 7, 5),
    status: TripTrackingStatus.inProgress,
    rotaVersao: 3,
    etaMinutosAteProximaParada: 9,
    etaAtualizadoEm: DateTime(2026, 7, 5, 7, 30),
    motorista: const TripTrackingDriverRecord(id: 7, nome: 'Motorista QA'),
    proximaParada: _stop,
    paradas: const [_stop],
    rotaGeografica: null,
  );
}

const _stop = DriverTripStopRecord(
  id: 80,
  ordem: 1,
  status: TripTrackingStopStatus.pending,
  etaMinutos: 9,
  confirmadoPresenca: true,
  proximaParada: true,
  passageiroId: 30,
  passageiroNome: 'Ana Clara',
  menorDeIdade: false,
);
