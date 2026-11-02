import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/navigation/domain/driver_navigation_models.dart';
import 'package:vanrides_frontend_dev/features/navigation/domain/driver_navigation_voice.dart';

void main() {
  test('speakCurrent speaks a new internal instruction only once', () async {
    final gateway = _RecordingVoiceGateway();
    final controller = DriverNavigationVoiceController(gateway: gateway);

    var state = await controller.speakCurrent(
      viewModel: _viewModel(),
      instructionId: 'inst-1',
      state: const DriverNavigationVoiceState(),
    );

    expect(gateway.spoken, ['Em 300 metros, vire a direita']);
    expect(state.lastSpokenInstructionId, 'inst-1');
    expect(state.lastSpokenText, 'Em 300 metros, vire a direita');

    state = await controller.speakCurrent(
      viewModel: _viewModel(),
      instructionId: 'inst-1',
      state: state,
    );

    expect(gateway.spoken, ['Em 300 metros, vire a direita']);
    expect(state.lastSpokenInstructionId, 'inst-1');
  });

  test(
    'repeat speaks the current instruction even when it was already spoken',
    () async {
      final gateway = _RecordingVoiceGateway();
      final controller = DriverNavigationVoiceController(gateway: gateway);
      final state = const DriverNavigationVoiceState(
        lastSpokenInstructionId: 'inst-1',
        lastSpokenText: 'Em 300 metros, vire a direita',
      );

      final nextState = await controller.repeat(
        viewModel: _viewModel(),
        instructionId: 'inst-1',
        state: state,
      );

      expect(gateway.spoken, ['Em 300 metros, vire a direita']);
      expect(nextState.lastSpokenInstructionId, 'inst-1');
    },
  );

  test('muted state blocks automatic speech and repeat', () async {
    final gateway = _RecordingVoiceGateway();
    final controller = DriverNavigationVoiceController(gateway: gateway);
    final muted = controller.toggleMute(const DriverNavigationVoiceState());

    expect(muted.muted, isTrue);

    final afterSpeak = await controller.speakCurrent(
      viewModel: _viewModel(),
      instructionId: 'inst-1',
      state: muted,
    );
    final afterRepeat = await controller.repeat(
      viewModel: _viewModel(),
      instructionId: 'inst-1',
      state: afterSpeak,
    );

    expect(gateway.spoken, isEmpty);
    expect(afterRepeat.muted, isTrue);
    expect(afterRepeat.lastSpokenInstructionId, isNull);
  });

  test('fallback and safe mode speak the safe view-model text', () async {
    final gateway = _RecordingVoiceGateway();
    final controller = DriverNavigationVoiceController(gateway: gateway);

    await controller.speakCurrent(
      viewModel: _viewModel(
        mode: DriverNavigationMode.externalFallback,
        instructionText: 'Use o fallback para seguir com seguranca',
        voicePrompt: 'Vire a direita na rota antiga',
      ),
      instructionId: 'fallback-1',
      state: const DriverNavigationVoiceState(),
    );
    await controller.repeat(
      viewModel: _viewModel(
        mode: DriverNavigationMode.safeMode,
        instructionText: 'Fora da rota planejada',
        voicePrompt: 'Vire a direita na rota antiga',
      ),
      instructionId: 'safe-1',
      state: const DriverNavigationVoiceState(),
    );

    expect(gateway.spoken, [
      'Use o fallback para seguir com seguranca',
      'Fora da rota planejada',
    ]);
  });

  test(
    'reduced attention blocks auto speech but allows explicit repeat',
    () async {
      final gateway = _RecordingVoiceGateway();
      final controller = DriverNavigationVoiceController(gateway: gateway);
      final reduced = const DriverNavigationVoiceState(reducedAttention: true);

      final afterAuto = await controller.speakCurrent(
        viewModel: _viewModel(),
        instructionId: 'inst-1',
        state: reduced,
      );
      final afterRepeat = await controller.repeat(
        viewModel: _viewModel(),
        instructionId: 'inst-1',
        state: afterAuto,
      );

      expect(gateway.spoken, ['Em 300 metros, vire a direita']);
      expect(afterRepeat.lastSpokenInstructionId, 'inst-1');
    },
  );

  test('critical events speak once and bypass reduced attention', () async {
    final gateway = _RecordingVoiceGateway();
    final controller = DriverNavigationVoiceController(gateway: gateway);
    var state = const DriverNavigationVoiceState(reducedAttention: true);

    state = await controller.speakEvent(
      event: DriverNavigationVoiceEvent.stopArrival,
      viewModel: _viewModel(),
      eventId: 'stop-80',
      state: state,
    );
    state = await controller.speakEvent(
      event: DriverNavigationVoiceEvent.stopArrival,
      viewModel: _viewModel(),
      eventId: 'stop-80',
      state: state,
    );
    state = await controller.speakEvent(
      event: DriverNavigationVoiceEvent.offlineFallback,
      viewModel: _viewModel(
        mode: DriverNavigationMode.externalFallback,
        instructionText: 'Use o fallback para seguir com seguranca',
        statusLabel: 'Pacote offline ausente',
      ),
      eventId: 'offline-missing',
      state: state,
    );
    state = await controller.speakEvent(
      event: DriverNavigationVoiceEvent.offRoute,
      viewModel: _viewModel(
        mode: DriverNavigationMode.safeMode,
        instructionText: 'Fora da rota planejada',
        statusLabel: 'Modo seguro',
      ),
      eventId: 'off-route-1',
      state: state,
    );

    expect(gateway.spoken, [
      'Prepare a chegada em Ana Clara.',
      'Navegacao offline limitada. Use o fallback se precisar.',
      'Fora da rota planejada. Use o modo seguro.',
    ]);
    expect(state.lastSpokenInstructionId, isNull);
    expect(state.lastSpokenEventId, 'event:offRoute:off-route-1');
  });

  test('muted state blocks critical voice events', () async {
    final gateway = _RecordingVoiceGateway();
    final controller = DriverNavigationVoiceController(gateway: gateway);

    final state = await controller.speakEvent(
      event: DriverNavigationVoiceEvent.offRoute,
      viewModel: _viewModel(mode: DriverNavigationMode.safeMode),
      eventId: 'off-route-1',
      state: const DriverNavigationVoiceState(muted: true),
    );

    expect(gateway.spoken, isEmpty);
    expect(state.lastSpokenInstructionId, isNull);
    expect(state.lastSpokenEventId, isNull);
  });

  test('critical events do not reset instruction dedupe', () async {
    final gateway = _RecordingVoiceGateway();
    final controller = DriverNavigationVoiceController(gateway: gateway);
    var state = await controller.speakCurrent(
      viewModel: _viewModel(),
      instructionId: 'inst-1',
      state: const DriverNavigationVoiceState(),
    );

    state = await controller.speakEvent(
      event: DriverNavigationVoiceEvent.stopArrival,
      viewModel: _viewModel(),
      eventId: 'stop-80',
      state: state,
    );
    state = await controller.speakCurrent(
      viewModel: _viewModel(),
      instructionId: 'inst-1',
      state: state,
    );

    expect(gateway.spoken, [
      'Em 300 metros, vire a direita',
      'Prepare a chegada em Ana Clara.',
    ]);
    expect(state.lastSpokenInstructionId, 'inst-1');
    expect(state.lastSpokenEventId, 'event:stopArrival:stop-80');
  });
}

DriverNavigationViewModel _viewModel({
  DriverNavigationMode mode = DriverNavigationMode.internal,
  String instructionText = 'Vire a direita na Av. Brasil',
  String? voicePrompt = 'Em 300 metros, vire a direita',
  String statusLabel = 'Offline pronto',
}) {
  return DriverNavigationViewModel(
    mode: mode,
    instructionText: instructionText,
    distanceLabel: 'Em 300 m',
    helperText: 'Siga com atencao ate a proxima parada.',
    nextStopLabel: 'Proxima parada',
    nextStopName: 'Ana Clara',
    nextStopSummary: 'Parada 1 - Linha Manha',
    primaryActionLabel: 'Cheguei',
    fallbackLabel: 'Fallback',
    statusLabel: statusLabel,
    voicePrompt: voicePrompt,
    fallbackIsPrimary: mode != DriverNavigationMode.internal,
    canUseInternalNavigation: mode == DriverNavigationMode.internal,
  );
}

class _RecordingVoiceGateway implements DriverNavigationVoiceGateway {
  final List<String> spoken = [];

  @override
  Future<void> speak(String text) async {
    spoken.add(text);
  }
}
