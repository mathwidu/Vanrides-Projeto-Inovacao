import 'package:vanrides_frontend_dev/features/navigation/domain/driver_navigation_models.dart';

abstract class DriverNavigationVoiceGateway {
  Future<void> speak(String text);
}

class NoopDriverNavigationVoiceGateway implements DriverNavigationVoiceGateway {
  const NoopDriverNavigationVoiceGateway();

  @override
  Future<void> speak(String text) async {}
}

enum DriverNavigationVoiceEvent { stopArrival, offlineFallback, offRoute }

class DriverNavigationVoiceState {
  const DriverNavigationVoiceState({
    this.muted = false,
    this.reducedAttention = false,
    this.lastSpokenInstructionId,
    this.lastSpokenEventId,
    this.lastSpokenText,
  });

  final bool muted;
  final bool reducedAttention;
  final String? lastSpokenInstructionId;
  final String? lastSpokenEventId;
  final String? lastSpokenText;

  DriverNavigationVoiceState copyWith({
    bool? muted,
    bool? reducedAttention,
    String? lastSpokenInstructionId,
    String? lastSpokenEventId,
    String? lastSpokenText,
  }) {
    return DriverNavigationVoiceState(
      muted: muted ?? this.muted,
      reducedAttention: reducedAttention ?? this.reducedAttention,
      lastSpokenInstructionId:
          lastSpokenInstructionId ?? this.lastSpokenInstructionId,
      lastSpokenEventId: lastSpokenEventId ?? this.lastSpokenEventId,
      lastSpokenText: lastSpokenText ?? this.lastSpokenText,
    );
  }
}

class DriverNavigationVoiceController {
  const DriverNavigationVoiceController({required this.gateway});

  final DriverNavigationVoiceGateway gateway;

  Future<DriverNavigationVoiceState> speakCurrent({
    required DriverNavigationViewModel viewModel,
    required String instructionId,
    required DriverNavigationVoiceState state,
  }) async {
    if (state.muted || state.reducedAttention) {
      return state;
    }
    if (state.lastSpokenInstructionId == instructionId) {
      return state;
    }
    return _speak(viewModel, instructionId, state);
  }

  Future<DriverNavigationVoiceState> repeat({
    required DriverNavigationViewModel viewModel,
    required String instructionId,
    required DriverNavigationVoiceState state,
  }) async {
    if (state.muted) {
      return state;
    }
    return _speak(viewModel, instructionId, state);
  }

  Future<DriverNavigationVoiceState> speakEvent({
    required DriverNavigationVoiceEvent event,
    required DriverNavigationViewModel viewModel,
    required String eventId,
    required DriverNavigationVoiceState state,
  }) async {
    if (state.muted) {
      return state;
    }
    final voiceId = 'event:${event.name}:$eventId';
    if (state.lastSpokenEventId == voiceId) {
      return state;
    }
    final text = _eventTextFor(event, viewModel);
    if (text.isEmpty) {
      return state;
    }
    await gateway.speak(text);
    return state.copyWith(lastSpokenEventId: voiceId, lastSpokenText: text);
  }

  DriverNavigationVoiceState toggleMute(DriverNavigationVoiceState state) {
    return state.copyWith(muted: !state.muted);
  }

  Future<DriverNavigationVoiceState> _speak(
    DriverNavigationViewModel viewModel,
    String instructionId,
    DriverNavigationVoiceState state,
  ) async {
    final text = _textFor(viewModel);
    if (text.isEmpty) {
      return state;
    }
    await gateway.speak(text);
    return state.copyWith(
      lastSpokenInstructionId: instructionId,
      lastSpokenText: text,
    );
  }

  String _textFor(DriverNavigationViewModel viewModel) {
    if (viewModel.mode != DriverNavigationMode.internal) {
      return viewModel.instructionText.trim();
    }
    final prompt = viewModel.voicePrompt?.trim();
    if (prompt != null && prompt.isNotEmpty) {
      return prompt;
    }
    return viewModel.instructionText.trim();
  }

  String _eventTextFor(
    DriverNavigationVoiceEvent event,
    DriverNavigationViewModel viewModel,
  ) {
    switch (event) {
      case DriverNavigationVoiceEvent.stopArrival:
        final stopName = viewModel.nextStopName.trim();
        if (stopName.isEmpty || stopName == 'Sem proxima parada') {
          return 'Prepare a chegada na proxima parada.';
        }
        return 'Prepare a chegada em $stopName.';
      case DriverNavigationVoiceEvent.offlineFallback:
        return 'Navegacao offline limitada. Use o fallback se precisar.';
      case DriverNavigationVoiceEvent.offRoute:
        return 'Fora da rota planejada. Use o modo seguro.';
    }
  }
}
