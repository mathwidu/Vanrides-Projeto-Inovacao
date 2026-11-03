import 'package:vanrides_frontend_dev/features/navigation/domain/driver_navigation_models.dart';

class DriverNavigationInstructionCopy {
  const DriverNavigationInstructionCopy({
    required this.text,
    required this.voiceText,
    required this.wasNormalized,
  });

  final String text;
  final String voiceText;
  final bool wasNormalized;

  static DriverNavigationInstructionCopy? fromInstruction(
    DriverNavigationInstruction? instruction,
  ) {
    if (instruction == null) {
      return null;
    }
    final normalizedText = _normalize(instruction.text);
    final normalizedVoice = _normalize(
      instruction.voiceText,
      fallback: normalizedText.text,
    );
    return DriverNavigationInstructionCopy(
      text: normalizedText.text,
      voiceText: normalizedVoice.text,
      wasNormalized: normalizedText.changed || normalizedVoice.changed,
    );
  }
}

class _NormalizedInstructionText {
  const _NormalizedInstructionText({required this.text, required this.changed});

  final String text;
  final bool changed;
}

final _ptBrSignalPattern = RegExp(
  r'\b(siga|continue|chegue|destino|parada|mantenha|entre|converta|retorne|origem|dirija|vire|prepare)\b',
  caseSensitive: false,
);

final _overconfidentPattern = RegExp(
  r'\b(perfeit[ao]|sem risco|garantid[ao]|100%|validada perfeita|rota segura garantida)\b',
  caseSensitive: false,
);

_NormalizedInstructionText _normalize(String value, {String? fallback}) {
  final raw = value.trim();
  if (raw.isEmpty) {
    return _NormalizedInstructionText(
      text: fallback ?? 'Siga pela rota planejada.',
      changed: fallback != null,
    );
  }

  if (_isOverconfident(raw)) {
    return _NormalizedInstructionText(
      text: fallback ?? 'Siga pela rota planejada.',
      changed: true,
    );
  }

  if (_hasPtBrSignal(raw)) {
    return _NormalizedInstructionText(text: raw, changed: false);
  }

  return _NormalizedInstructionText(
    text: _buildConservativeInstruction(raw),
    changed: true,
  );
}

bool _hasPtBrSignal(String value) {
  return _ptBrSignalPattern.hasMatch(value);
}

bool _isOverconfident(String value) {
  return _overconfidentPattern.hasMatch(value);
}

String _buildConservativeInstruction(String rawText) {
  final raw = rawText.toLowerCase();
  final road = _extractRoadName(rawText);

  if (RegExp(r'\b(arrive|destination|destino)\b').hasMatch(raw)) {
    return 'Chegue ao destino.';
  }
  if (RegExp(r'\b(left|esquerda)\b').hasMatch(raw)) {
    return _withRoad('Converta a esquerda', road);
  }
  if (RegExp(r'\b(right|direita)\b').hasMatch(raw)) {
    return _withRoad('Converta a direita', road);
  }
  if (RegExp(r'\b(continue|straight|north|south|east|west)\b').hasMatch(raw)) {
    return _withRoad('Continue pela rota planejada', road);
  }

  return _withRoad('Siga pela rota planejada', road);
}

String _withRoad(String action, String? roadName) {
  return roadName == null ? '$action.' : '$action em $roadName.';
}

String? _extractRoadName(String value) {
  final match = RegExp(
    r'\b(?:on|onto|em)\s+([^.,]+)',
    caseSensitive: false,
  ).firstMatch(value);
  return _cleanRoadName(match?.group(1));
}

String? _cleanRoadName(String? value) {
  final cleaned =
      (value ?? '')
          .replaceAll(RegExp(r'\s+'), ' ')
          .replaceAll(RegExp(r'[.]+$'), '')
          .trim();
  return cleaned.isEmpty ? null : cleaned;
}
