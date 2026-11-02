import 'package:vanrides_frontend_dev/features/driver_trip/domain/driver_trip_record.dart';
import 'package:vanrides_frontend_dev/features/navigation/domain/driver_navigation_models.dart';
import 'package:vanrides_frontend_dev/features/navigation/domain/driver_navigation_voice.dart';
import 'package:vanrides_frontend_dev/features/navigation/presentation/driver_navigation_presenter.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_record.dart';

class DriverNavigationDriveSimulation {
  const DriverNavigationDriveSimulation();

  Future<DriverNavigationDriveSimulationResult> run() async {
    final gateway = _RecordingNavigationVoiceGateway();
    final voiceController = DriverNavigationVoiceController(gateway: gateway);
    var voiceState = const DriverNavigationVoiceState();
    final steps = <DriverNavigationDriveSimulationStep>[];

    for (final scenario in _scenarios) {
      final viewModel = const DriverNavigationPresenter().present(
        scenario.session,
      );
      final beforeSpeechCount = gateway.spoken.length;
      voiceState = await voiceController.speakCurrent(
        viewModel: viewModel,
        instructionId: scenario.instructionId,
        state: voiceState,
      );
      final spokenText =
          gateway.spoken.length > beforeSpeechCount
              ? gateway.spoken.last
              : null;
      final beforeEventSpeechCount = gateway.spoken.length;
      String? eventSpokenText;
      if (scenario.voiceEvent != null) {
        voiceState = await voiceController.speakEvent(
          event: scenario.voiceEvent!,
          viewModel: viewModel,
          eventId: scenario.id,
          state: voiceState,
        );
        eventSpokenText =
            gateway.spoken.length > beforeEventSpeechCount
                ? gateway.spoken.last
                : null;
      }
      steps.add(
        DriverNavigationDriveSimulationStep(
          id: scenario.id,
          title: scenario.title,
          viewModel: viewModel,
          spokenText: spokenText,
          eventSpokenText: eventSpokenText,
        ),
      );
    }

    return DriverNavigationDriveSimulationResult(steps: steps);
  }
}

class DriverNavigationDriveSimulationResult {
  const DriverNavigationDriveSimulationResult({required this.steps});

  final List<DriverNavigationDriveSimulationStep> steps;

  bool get coversInternalNavigation =>
      steps.any((step) => step.viewModel.mode == DriverNavigationMode.internal);

  bool get coversFallback => steps.any(
    (step) => step.viewModel.mode == DriverNavigationMode.externalFallback,
  );

  bool get coversSafeMode =>
      steps.any((step) => step.viewModel.mode == DriverNavigationMode.safeMode);

  bool get coversOfflineRisk => steps.any(
    (step) =>
        step.viewModel.statusLabel.contains('offline') ||
        step.viewModel.statusLabel.contains('Offline') ||
        step.viewModel.statusLabel.contains('Fallback') ||
        step.viewModel.statusLabel.contains('Modo seguro'),
  );

  List<String> get spokenPrompts => steps
      .map((step) => step.spokenText)
      .whereType<String>()
      .toList(growable: false);

  List<String> get eventSpokenPrompts => steps
      .map((step) => step.eventSpokenText)
      .whereType<String>()
      .toList(growable: false);

  DriverNavigationDriveSimulationStep stepById(String id) {
    return steps.singleWhere((step) => step.id == id);
  }

  Map<String, Object?> toJson() {
    return {
      'card': 'APP-NAV-SIM-DRIVE-QA-001',
      'summary': 'Simulacao local com dados QA',
      'fieldReady': false,
      'coversInternalNavigation': coversInternalNavigation,
      'coversFallback': coversFallback,
      'coversSafeMode': coversSafeMode,
      'coversOfflineRisk': coversOfflineRisk,
      'spokenPrompts': spokenPrompts,
      'eventSpokenPrompts': eventSpokenPrompts,
      'steps': steps.map((step) => step.toJson()).toList(growable: false),
    };
  }

  String toMarkdown() {
    final buffer =
        StringBuffer()
          ..writeln('# APP-NAV-SIM-DRIVE-QA-001')
          ..writeln()
          ..writeln('Simulacao local com dados QA.')
          ..writeln()
          ..writeln('Nao usa parceiro, GPS real, backend, deploy ou VPS.')
          ..writeln()
          ..writeln(
            '| Step | Modo | Status | Instrucao | Normalizada | Proxima parada | Voz |',
          )
          ..writeln('| --- | --- | --- | --- | --- | --- | --- |');

    for (final step in steps) {
      buffer.writeln(
        '| ${step.id} | ${step.viewModel.mode.name} | '
        '${step.viewModel.statusLabel} | ${step.viewModel.instructionText} | '
        '${step.viewModel.instructionWasNormalized ? 'sim' : 'nao'} | '
        '${step.viewModel.nextStopName} | ${step.spokenText ?? '-'} |',
      );
    }
    buffer.writeln();
    buffer
      ..writeln('| Step | Evento de voz |')
      ..writeln('| --- | --- |');

    for (final step in steps) {
      buffer.writeln('| ${step.id} | ${step.eventSpokenText ?? '-'} |');
    }

    return buffer.toString();
  }
}

class DriverNavigationDriveSimulationStep {
  const DriverNavigationDriveSimulationStep({
    required this.id,
    required this.title,
    required this.viewModel,
    required this.spokenText,
    required this.eventSpokenText,
  });

  final String id;
  final String title;
  final DriverNavigationViewModel viewModel;
  final String? spokenText;
  final String? eventSpokenText;

  Map<String, Object?> toJson() {
    return {
      'id': id,
      'title': title,
      'mode': viewModel.mode.name,
      'statusLabel': viewModel.statusLabel,
      'instructionText': viewModel.instructionText,
      'distanceLabel': viewModel.distanceLabel,
      'nextStopName': viewModel.nextStopName,
      'instructionWasNormalized': viewModel.instructionWasNormalized,
      'fallbackIsPrimary': viewModel.fallbackIsPrimary,
      'canUseInternalNavigation': viewModel.canUseInternalNavigation,
      'spokenText': spokenText,
      'eventSpokenText': eventSpokenText,
    };
  }
}

class _NavigationDriveScenario {
  const _NavigationDriveScenario({
    required this.id,
    required this.title,
    required this.instructionId,
    required this.session,
    this.voiceEvent,
  });

  final String id;
  final String title;
  final String instructionId;
  final DriverNavigationSession session;
  final DriverNavigationVoiceEvent? voiceEvent;
}

class _RecordingNavigationVoiceGateway implements DriverNavigationVoiceGateway {
  final List<String> spoken = [];

  @override
  Future<void> speak(String text) async {
    spoken.add(text);
  }
}

final _scenarios = [
  _NavigationDriveScenario(
    id: 'start-internal',
    title: 'Saida com navegacao VanRides',
    instructionId: 'start-internal',
    session: DriverNavigationSession(
      trip: _trip(nextStop: _ana, etaMinutes: 9),
      currentInstruction: const DriverNavigationInstruction(
        id: 'start-internal',
        text: 'Siga pela Rua Bento Goncalves',
        voiceText: 'Siga por 800 metros ate a Rua das Flores',
        distanceMeters: 800,
      ),
      offlineState: DriverNavigationOfflineState.ready,
      coverageStatus: DriverNavigationCoverageStatus.validated,
    ),
  ),
  _NavigationDriveScenario(
    id: 'next-stop-action',
    title: 'Chegada na primeira parada',
    instructionId: 'next-stop-action',
    voiceEvent: DriverNavigationVoiceEvent.stopArrival,
    session: DriverNavigationSession(
      trip: _trip(nextStop: _ana, etaMinutes: 2),
      currentInstruction: const DriverNavigationInstruction(
        id: 'next-stop-action',
        text: 'Prepare a chegada na parada',
        voiceText: 'Em 120 metros, prepare a chegada na parada da Ana Clara',
        distanceMeters: 120,
      ),
      offlineState: DriverNavigationOfflineState.ready,
      coverageStatus: DriverNavigationCoverageStatus.validated,
    ),
  ),
  _NavigationDriveScenario(
    id: 'raw-provider-instruction',
    title: 'Instrucao crua protegida para PT-BR',
    instructionId: 'raw-provider-instruction',
    session: DriverNavigationSession(
      trip: _trip(nextStop: _bruno, etaMinutes: 8),
      currentInstruction: const DriverNavigationInstruction(
        id: 'raw-provider-instruction',
        text: 'Turn right on Main Street',
        voiceText:
            'The route is perfect and risk free. Turn right on Main Street',
        distanceMeters: 220,
      ),
      offlineState: DriverNavigationOfflineState.ready,
      coverageStatus: DriverNavigationCoverageStatus.validated,
    ),
  ),
  _NavigationDriveScenario(
    id: 'weak-signal-fallback',
    title: 'Rede ruim com fallback externo',
    instructionId: 'weak-signal-fallback',
    voiceEvent: DriverNavigationVoiceEvent.offlineFallback,
    session: DriverNavigationSession(
      trip: _trip(nextStop: _bruno, etaMinutes: 7),
      currentInstruction: const DriverNavigationInstruction(
        id: 'weak-signal-fallback',
        text: 'Continue pela rota planejada',
        voiceText: 'Continue pela rota planejada',
        distanceMeters: 450,
      ),
      offlineState: DriverNavigationOfflineState.missing,
      coverageStatus: DriverNavigationCoverageStatus.fallback,
    ),
  ),
  _NavigationDriveScenario(
    id: 'partial-pack-corridor',
    title: 'Pacote parcial no corredor validado',
    instructionId: 'partial-pack-corridor',
    voiceEvent: DriverNavigationVoiceEvent.offlineFallback,
    session: DriverNavigationSession(
      trip: _trip(nextStop: _bruno, etaMinutes: 6),
      currentInstruction: const DriverNavigationInstruction(
        id: 'partial-pack-corridor',
        text: 'Mantenha o corredor validado',
        voiceText: 'Mantenha o corredor validado',
        distanceMeters: 390,
      ),
      offlineState: DriverNavigationOfflineState.partial,
      coverageStatus: DriverNavigationCoverageStatus.validated,
    ),
  ),
  _NavigationDriveScenario(
    id: 'off-route-safe-mode',
    title: 'Desvio de rota em modo seguro',
    instructionId: 'off-route-safe-mode',
    voiceEvent: DriverNavigationVoiceEvent.offRoute,
    session: DriverNavigationSession(
      trip: _trip(nextStop: _bruno, etaMinutes: 5),
      currentInstruction: const DriverNavigationInstruction(
        id: 'off-route-safe-mode',
        text: 'Reaproxime da rota planejada',
        voiceText: 'Reaproxime da rota planejada',
        distanceMeters: 250,
      ),
      offlineState: DriverNavigationOfflineState.ready,
      coverageStatus: DriverNavigationCoverageStatus.validated,
      offRoute: true,
    ),
  ),
];

DriverTripRecord _trip({
  required DriverTripStopRecord nextStop,
  required int etaMinutes,
}) {
  return DriverTripRecord(
    id: 9101,
    rotaBaseId: 44,
    rotaNome: 'Rota QA Centro',
    servicoNome: 'Linha QA Manha',
    tipoOperacao: 'IDA',
    dataOperacao: DateTime(2026, 7, 7),
    status: TripTrackingStatus.inProgress,
    rotaVersao: 12,
    etaMinutosAteProximaParada: etaMinutes,
    etaAtualizadoEm: DateTime(2026, 7, 7, 7, 45),
    motorista: const TripTrackingDriverRecord(id: 7, nome: 'Motorista QA'),
    proximaParada: nextStop,
    paradas: const [_ana, _bruno],
    rotaGeografica: null,
  );
}

const _ana = DriverTripStopRecord(
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

const _bruno = DriverTripStopRecord(
  id: 81,
  ordem: 2,
  status: TripTrackingStopStatus.pending,
  etaMinutos: 7,
  confirmadoPresenca: true,
  proximaParada: true,
  passageiroId: 31,
  passageiroNome: 'Bruno Lima',
  menorDeIdade: false,
);
