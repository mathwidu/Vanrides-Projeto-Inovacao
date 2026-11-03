import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/navigation/data/driver_navigation_tts_gateway.dart';

void main() {
  test(
    'native gateway configures PT-BR voice and speaks trimmed text',
    () async {
      final engine = _RecordingTtsEngine();
      final gateway = DriverNavigationTtsGateway(engine: engine);

      await gateway.speak('  Em 300 metros, vire a direita.  ');

      expect(engine.calls, [
        'awaitCompletion:true',
        'language:pt-BR',
        'speechRate:0.46',
        'volume:1.0',
        'pitch:1.0',
        'stop',
        'speak:Em 300 metros, vire a direita.',
      ]);
    },
  );

  test('native gateway ignores blank instructions', () async {
    final engine = _RecordingTtsEngine();
    final gateway = DriverNavigationTtsGateway(engine: engine);

    await gateway.speak('   ');

    expect(engine.calls, isEmpty);
  });

  test('native gateway configures the engine only once', () async {
    final engine = _RecordingTtsEngine();
    final gateway = DriverNavigationTtsGateway(engine: engine);

    await gateway.speak('Primeira instrucao.');
    await gateway.speak('Segunda instrucao.');

    expect(engine.calls.where((call) => call.startsWith('language:')), [
      'language:pt-BR',
    ]);
    expect(engine.calls.where((call) => call == 'stop'), ['stop', 'stop']);
    expect(engine.calls.last, 'speak:Segunda instrucao.');
  });
}

class _RecordingTtsEngine implements DriverNavigationTtsEngine {
  final List<String> calls = [];

  @override
  Future<void> awaitSpeakCompletion(bool enabled) async {
    calls.add('awaitCompletion:$enabled');
  }

  @override
  Future<void> setLanguage(String language) async {
    calls.add('language:$language');
  }

  @override
  Future<void> setPitch(double pitch) async {
    calls.add('pitch:$pitch');
  }

  @override
  Future<void> setSpeechRate(double speechRate) async {
    calls.add('speechRate:$speechRate');
  }

  @override
  Future<void> setVolume(double volume) async {
    calls.add('volume:$volume');
  }

  @override
  Future<void> speak(String text) async {
    calls.add('speak:$text');
  }

  @override
  Future<void> stop() async {
    calls.add('stop');
  }
}
