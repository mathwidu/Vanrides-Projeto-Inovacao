import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/driver_mvp/data/driver_session_links.dart';
import 'package:vanrides_frontend_dev/features/driver_mvp/data/driver_session_platform.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const native = NativeDriverSessionPlatform();
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  tearDown(
    () => messenger.setMockMethodCallHandler(
      NativeDriverSessionPlatform.channel,
      null,
    ),
  );

  test(
    'the channel has six no-argument actions and only reveal returns the code',
    () async {
      final calls = <String>[];
      messenger.setMockMethodCallHandler(NativeDriverSessionPlatform.channel, (
        call,
      ) async {
        expect(call.arguments, isNull);
        calls.add(call.method);
        if (call.method == 'showPairingCode') return 'BCDFGHJZ';
        return {'state': 'PENDING', 'expiresAt': '2026-10-31T20:30:00Z'};
      });
      for (final action in DriverSessionAction.values) {
        expect((await native.run(action)).state, DriverSessionState.pending);
      }
      expect(await native.showPairingCode(), 'BCDFGHJZ');
      expect(calls, [
        'getState',
        'beginPairing',
        'syncSession',
        'refreshSession',
        'revokeSession',
        'showPairingCode',
      ]);
    },
  );

  test(
    'malformed or overbroad native data cannot imply successful pairing',
    () async {
      for (final response in <Object?>[
        null,
        ['READY'],
        {'state': 'PAIRED'},
        {'state': 'READY', 'userCode': 'anything'},
        {'state': 'READY', 'expiresAt': '2030-01-01T00:00:00Z'},
        {'state': 'PENDING', 'expiresAt': false},
        {'state': 'PENDING', 'expiresAt': 'tomorrow'},
      ]) {
        expect(
          () => DriverSessionSnapshot.fromNative(response),
          throwsA(isA<DriverSessionFailure>()),
        );
      }
      for (final value in [
        'abc',
        'BCDFGHJL',
        'BCDFGHJZ\n',
        {'userCode': 'BCDFGHJZ'},
      ]) {
        messenger.setMockMethodCallHandler(
          NativeDriverSessionPlatform.channel,
          (_) async => value,
        );
        await expectLater(
          native.showPairingCode(),
          throwsA(isA<DriverSessionFailure>()),
        );
      }
    },
  );

  test(
    'unexpected platform errors and payloads have a fixed public message',
    () async {
      messenger.setMockMethodCallHandler(
        NativeDriverSessionPlatform.channel,
        (_) async =>
            throw PlatformException(
              code: 'private diagnostic',
              message: 'private diagnostic',
            ),
      );
      await expectLater(
        native.run(DriverSessionAction.syncSession),
        throwsA(
          isA<DriverSessionFailure>()
              .having((e) => e.code, 'code', 'UNAVAILABLE')
              .having((e) => e.message, 'message', isNot(contains('private'))),
        ),
      );
    },
  );

  test(
    'both native platforms surface recoverable errors without exposing payloads',
    () async {
      for (final entry
          in {
            'NETWORK_ERROR': 'NETWORK',
            'RATE_LIMITED': 'RATE_LIMITED',
            'SECURE_STORAGE_UNAVAILABLE': 'STORAGE',
            'STORAGE_ERROR': 'STORAGE',
            'UI_NOT_VISIBLE': 'INVALID_STATE',
            'SESSION_EXPIRED': 'AUTH',
          }.entries) {
        messenger.setMockMethodCallHandler(
          NativeDriverSessionPlatform.channel,
          (_) async =>
              throw PlatformException(code: entry.key, message: 'ignored'),
        );
        await expectLater(
          native.run(DriverSessionAction.syncSession),
          throwsA(
            isA<DriverSessionFailure>().having(
              (e) => e.code,
              'code',
              entry.value,
            ),
          ),
        );
      }
    },
  );

  test('only the exact return routes are accepted', () {
    const host = 'links.example.test';
    for (final address in [
      'vanridesdriver://pairing/return',
      'https://links.example.test/motorista/vinculo/retorno',
    ]) {
      expect(DeviceSessionLinks.isReturn(Uri.parse(address), host), isTrue);
    }
    for (final address in [
      'vanridesdriver://pairing/begin',
      'vanridesdriver://pairing/return?code=ignored',
      'vanridesdriver://pairing/return#hello',
      'https://elsewhere.test/motorista/vinculo/retorno',
      'https://name@links.example.test/motorista/vinculo/retorno',
      'https://links.example.test:8443/motorista/vinculo/retorno',
      'http://links.example.test/motorista/vinculo/retorno',
    ]) {
      expect(DeviceSessionLinks.isReturn(Uri.parse(address), host), isFalse);
    }
  });
}
