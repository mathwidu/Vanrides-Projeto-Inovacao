import 'package:flutter_tts/flutter_tts.dart';
import 'package:vanrides_frontend_dev/features/navigation/domain/driver_navigation_voice.dart';

class DriverNavigationTtsSettings {
  const DriverNavigationTtsSettings({
    this.language = 'pt-BR',
    this.speechRate = 0.46,
    this.volume = 1.0,
    this.pitch = 1.0,
  });

  final String language;
  final double speechRate;
  final double volume;
  final double pitch;
}

abstract class DriverNavigationTtsEngine {
  Future<void> awaitSpeakCompletion(bool enabled);
  Future<void> setLanguage(String language);
  Future<void> setPitch(double pitch);
  Future<void> setSpeechRate(double speechRate);
  Future<void> setVolume(double volume);
  Future<void> speak(String text);
  Future<void> stop();
}

class FlutterDriverNavigationTtsEngine implements DriverNavigationTtsEngine {
  FlutterDriverNavigationTtsEngine({FlutterTts? tts})
    : _tts = tts ?? FlutterTts();

  final FlutterTts _tts;

  @override
  Future<void> awaitSpeakCompletion(bool enabled) async {
    await _tts.awaitSpeakCompletion(enabled);
  }

  @override
  Future<void> setLanguage(String language) async {
    await _tts.setLanguage(language);
  }

  @override
  Future<void> setPitch(double pitch) async {
    await _tts.setPitch(pitch);
  }

  @override
  Future<void> setSpeechRate(double speechRate) async {
    await _tts.setSpeechRate(speechRate);
  }

  @override
  Future<void> setVolume(double volume) async {
    await _tts.setVolume(volume);
  }

  @override
  Future<void> speak(String text) async {
    await _tts.speak(text);
  }

  @override
  Future<void> stop() async {
    await _tts.stop();
  }
}

class DriverNavigationTtsGateway implements DriverNavigationVoiceGateway {
  DriverNavigationTtsGateway({
    required DriverNavigationTtsEngine engine,
    this.settings = const DriverNavigationTtsSettings(),
  }) : _engine = engine;

  factory DriverNavigationTtsGateway.native({
    DriverNavigationTtsSettings settings = const DriverNavigationTtsSettings(),
  }) {
    return DriverNavigationTtsGateway(
      engine: FlutterDriverNavigationTtsEngine(),
      settings: settings,
    );
  }

  final DriverNavigationTtsEngine _engine;
  final DriverNavigationTtsSettings settings;
  bool _configured = false;

  @override
  Future<void> speak(String text) async {
    final instruction = text.trim();
    if (instruction.isEmpty) {
      return;
    }
    await _configureOnce();
    await _engine.stop();
    await _engine.speak(instruction);
  }

  Future<void> _configureOnce() async {
    if (_configured) {
      return;
    }
    await _engine.awaitSpeakCompletion(true);
    await _engine.setLanguage(settings.language);
    await _engine.setSpeechRate(settings.speechRate);
    await _engine.setVolume(settings.volume);
    await _engine.setPitch(settings.pitch);
    _configured = true;
  }
}
