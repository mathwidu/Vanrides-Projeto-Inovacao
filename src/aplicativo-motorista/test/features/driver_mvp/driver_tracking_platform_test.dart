import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/driver_mvp/data/driver_tracking_platform.dart';

import 'driver_energy_fixture.dart';

Map<String, Object?> nativeStatus() => {
  'state': 'TRACKING',
  'credentialConfigured': true,
  'permission': 'GRANTED',
  'queued': 2,
  'accepted': 7,
  'duplicates': 1,
  'rejected': 0,
  'dropped': 3,
  'gpsState': 'FRESH',
  'leaseExpiresAt': 1800000300000,
  'reason': null,
  'stopRequestedAt': null,
  'stoppedAt': null,
};

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  test('bridge preserves individual counters and nullable stop evidence', () {
    final status = DriverTrackingStatus.fromMap(nativeStatus());
    expect(status.isActive, isTrue);
    expect(status.accepted, 7);
    expect(status.queued, 2);
    expect(status.stoppedAt, isNull);
  });
  test('invalid native types or enums never become a success state', () {
    for (final patch in <Map<String, Object?>>[
      {'state': 'ALL_GOOD'},
      {'credentialConfigured': 1},
      {'stopPending': 'true'},
      {'permission': 'ALWAYS'},
      {'queued': -1},
      {'accepted': 1.0},
      {'dropped': null},
      {'gpsState': 'OK'},
      {'reason': 'arbitrary error with coordinates'},
      {'leaseExpiresAt': 'tomorrow'},
      {'nativeStopCompletedElapsedMs': -1},
      {'diagnosticBootCount': '2'},
      {'lastLocationAccuracyM': double.nan},
      {'lastLocationAccuracyM': double.infinity},
      {'lastLocationAgeElapsedMs': 1.5},
      {'lastLocationElapsedState': 'FRESH'},
      {'lastLocationCallbackDisposition': 'private-text'},
      {'diagnosticWriteFailed': 'false'},
    ]) {
      expect(
        () => DriverTrackingStatus.fromMap({...nativeStatus(), ...patch}),
        throwsFormatException,
      );
    }
  });
  test(
    'old native snapshots and absent platform-specific metadata stay valid',
    () {
      final status = DriverTrackingStatus.fromMap(nativeStatus());
      expect(status.diagnostics.values, isEmpty);
      final withNull = DriverTrackingStatus.fromMap({
        ...nativeStatus(),
        'nativeStopCompletedElapsedMs': null,
        'nativeStopMonotonicMs': null,
        'lastLocationAccuracyM': null,
      });
      expect(withNull.toSanitizedMap(), status.toSanitizedMap());
    },
  );
  test(
    'Android stop/deadline/boot evidence survives without wall clock conversion',
    () {
      final evidence = <String, Object>{
        'leaseDeadlineElapsedMs': 300000,
        'stopRequestedElapsedMs': 300001,
        'nativeStopCalledElapsedMs': 300002,
        'nativeStopCompletedElapsedMs': 300003,
        'stoppedElapsedMs': 300004,
        'nativeStopCalledAt': 1800000000000,
        'nativeStopCompletedAt': 1800000000001,
        'previousNativeStopCalledElapsedMs': 4000,
        'previousNativeStopCompletedElapsedMs': 4001,
        'previousLeaseDeadlineElapsedMs': 3999,
        'diagnosticBootCount': 7,
        'previousDiagnosticBootCount': 6,
        'lateCallbacksDiscarded': 1,
        'remoteStopAcknowledgedAt': 1800000002000,
      };
      final status = DriverTrackingStatus.fromMap({
        ...nativeStatus(),
        ...evidence,
      });
      expect(status.diagnostics.values, evidence);
      expect(
        () => status.diagnostics.values['diagnosticBootCount'] = 8,
        throwsUnsupportedError,
      );
    },
  );
  test(
    'optional iOS monotonic evidence remains in its native clock domain',
    () {
      final evidence = <String, Object>{
        'nativeStopMonotonicMs': 5001,
        'leaseDeadlineMonotonicMs': 5000,
        'activeLeaseDeadlineMonotonicMs': 6000,
        'discardedCallbacks': 2,
        'diagnosticWriteFailed': false,
      };
      final status = DriverTrackingStatus.fromMap({
        ...nativeStatus(),
        ...evidence,
      });
      expect(status.diagnostics.values, evidence);
    },
  );
  test(
    'callback metadata cannot make stale GPS fresh or hide future/invalid ages',
    () {
      final status = DriverTrackingStatus.fromMap({
        ...nativeStatus(),
        'gpsState': 'STALE',
        'locationCallbacksReceived': 20,
        'locationSamplesOffered': 19,
        'lastLocationAccuracyM': 75.5,
        'lastLocationAccuracyState': 'VALID',
        'lastLocationAgeElapsedMs': 25000,
        'lastLocationElapsedState': 'VALID',
        'lastLocationAgeWallMs': -15000,
        'lastLocationWallState': 'FUTURE',
        'lastLocationAgeElapsedAtCallbackMs': -2,
        'lastLocationElapsedStateAtCallback': 'FUTURE',
        'lastLocationCallbackDisposition': 'OFFERED',
      });
      final safe = status.toSanitizedMap();
      expect(status.gpsState, 'STALE');
      expect(safe['lastLocationAccuracyM'], 75.5);
      expect(safe['lastLocationAgeElapsedMs'], 25000);
      expect(safe['lastLocationAgeWallMs'], -15000);
      expect(safe['lastLocationWallState'], 'FUTURE');
      expect(safe['lastLocationElapsedStateAtCallback'], 'FUTURE');
      expect(safe['lastLocationAgeElapsedAtCallbackMs'], -2);
      final invalid = DriverTrackingStatus.fromMap({
        ...nativeStatus(),
        'lastLocationAccuracyState': 'MISSING',
        'lastLocationElapsedState': 'INVALID',
      });
      expect(invalid.diagnostics.values['lastLocationElapsedState'], 'INVALID');
      expect(
        invalid.diagnostics.values.containsKey('lastLocationAgeElapsedMs'),
        isFalse,
      );
    },
  );
  test(
    'diagnostic export ignores unknown data including nested secrets and GPS',
    () {
      final status = DriverTrackingStatus.fromMap({
        ...nativeStatus(),
        'nativeStopCalledElapsedMs': 10,
        'token': 'PRIVATE_BEARER',
        'sessionId': 'PRIVATE_SESSION',
        'intentId': 'PRIVATE_INTENT',
        'latitude': -90.0,
        'longitude': 180.0,
        'speed': 10,
        'bearing': 45,
        'diagnostics': {'token': 'NESTED_PRIVATE'},
        'reason': 'PRIVATE_REASON_TEXT',
      });
      final safe = status.toSanitizedMap();
      expect(safe['nativeStopCalledElapsedMs'], 10);
      expect(safe['reason'], 'UNRECOGNIZED');
      expect(
        safe.keys,
        unorderedEquals([
          ...nativeStatus().keys,
          'notificationPermissionGranted',
          'stopPending',
          'nativeStopCalledElapsedMs',
        ]),
      );
      expect(safe.toString(), isNot(contains('PRIVATE')));
      expect(
        DriverTrackingStatus.fromMap({
          ...nativeStatus(),
          'reason': 'LEASE_EXPIRED',
        }).toSanitizedMap()['reason'],
        'LEASE_EXPIRED',
      );
    },
  );
  test(
    'method channel uses the frozen typed envelope and never returns token',
    () async {
      const channel = MethodChannel('driver-test-channel');
      final calls = <MethodCall>[];
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (call) async {
            calls.add(call);
            return nativeStatus();
          });
      const platform = NativeDriverTrackingPlatform(channel: channel);
      final result = await platform.configureCredential(
        'https://example.invalid',
        'synthetic',
      );
      await platform.start('intent_test');
      await platform.stop();
      await platform.syncStatus();
      expect(calls.map((c) => c.method), [
        'configureCredential',
        'start',
        'stop',
        'syncStatus',
      ]);
      expect(calls[0].arguments, {
        'kind': 'TEST_BEARER',
        'baseUrl': 'https://example.invalid',
        'token': 'synthetic',
      });
      expect(calls[1].arguments, {'intentId': 'intent_test'});
      expect(result.accepted, 7);
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, null);
    },
  );
  test('energy endpoints remain optional, raw and immutable after export', () {
    final status = DriverTrackingStatus.fromMap({
      ...nativeStatus(),
      'energySession': nativeEnergySession(),
    });
    final exported = status.toSanitizedMap()['energySession'] as Map;
    expect(exported, nativeEnergySession());
    expect(() => exported['approved'] = true, throwsUnsupportedError);
    expect(
      () => (exported['start'] as Map)['batteryPercent'] = 0,
      throwsUnsupportedError,
    );
    expect(exported.containsKey('consumptionPercent'), isFalse);
    expect(exported.containsKey('autonomyHours'), isFalse);
  });
  test('energy export strips unknown nested GPS, secrets and acceptance', () {
    final status = DriverTrackingStatus.fromMap({
      ...nativeStatus(),
      'energySession': {
        ...nativeEnergySession(),
        'sessionId': 'PRIVATE_SESSION',
        'approved': true,
        'consumptionPercent': 5,
        'start': {
          ...energySnapshot(elapsedMs: 1000, powerSource: 'USB'),
          'token': 'PRIVATE_BEARER',
          'latitude': -90,
          'rawIntent': {'private': 'PRIVATE'},
        },
      },
    });
    expect(status.toSanitizedMap()['energySession'], nativeEnergySession());
    expect(status.toSanitizedMap().toString(), isNot(contains('PRIVATE')));
  });
  test('energy malformed known fields never become usable diagnostics', () {
    for (final patch in <Map<String, Object?>>[
      {'state': 'APPROVED'},
      {'observationMode': 'ALWAYS_ON'},
      {'unpluggedWindowState': 'OK'},
      {'powerSaveModeChanged': 1},
      {'powerStateUncertain': 'false'},
      {'start': null},
      {
        'start': {'elapsedMs': -1},
      },
      {
        'start': {...energySnapshot(elapsedMs: 1), 'batteryPercent': 101},
      },
      {
        'start': {...energySnapshot(elapsedMs: 1), 'batteryPercent': 1.5},
      },
      {
        'start': {...energySnapshot(elapsedMs: 1), 'powerSource': 'PRIVATE'},
      },
      {
        'start': {...energySnapshot(elapsedMs: 1), 'powerSaveMode': 'false'},
      },
      {
        'start': {...energySnapshot(elapsedMs: 1), 'bootCount': -1},
      },
      {'stop': null},
      {'state': 'INCOMPLETE'},
    ]) {
      expect(
        () => DriverTrackingStatus.fromMap({
          ...nativeStatus(),
          'energySession': {...nativeEnergySession(), ...patch},
        }),
        throwsFormatException,
      );
    }
  });
  test('interrupted energy session exports no invented final snapshot', () {
    final status = DriverTrackingStatus.fromMap({
      ...nativeStatus(),
      'energySession': {
        ...(nativeEnergySession()..remove('stop')),
        'state': 'INCOMPLETE',
        'unpluggedWindowState': 'INCOMPLETE',
        'unpluggedEnd': null,
      },
    });
    final energy = status.diagnostics.values['energySession'] as Map;
    expect(energy['state'], 'INCOMPLETE');
    expect(energy.containsKey('stop'), isFalse);
    expect(energy.containsKey('unpluggedEnd'), isFalse);
  });
}
