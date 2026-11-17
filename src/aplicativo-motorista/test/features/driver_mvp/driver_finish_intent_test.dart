import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/driver_mvp/data/driver_finish_intent.dart';
import 'package:vanrides_frontend_dev/features/driver_mvp/data/driver_run_intent.dart';
import 'package:vanrides_frontend_dev/features/driver_mvp/data/driver_finish_platform.dart';

const finishId = '12345678-1234-1234-1234-123456789012';
const finishRun = '12345678-1234-1234-1234-123456789013';
const finishSession = '12345678-1234-1234-1234-123456789014';
Uri finishLink([String id = finishId]) => Uri.parse(
  'vanridesdriver://run/finish#v1.$id.7.${List.filled(43, 'A').join()}',
);
Map<Object?, Object?> finishPreview([String id = finishId]) => {
  'intentId': id,
  'runId': finishRun,
  'trackingSessionId': finishSession,
  'sessionGeneration': 2,
  'expectedVersion': 7,
  'expiresAt': '2030-01-01T18:05:00Z',
  'state': 'READY',
  'stopAcknowledged': false,
  'trip': {
    'name': 'Faculdade sintética',
    'direction': 'RETURN',
    'localDate': '2030-01-01',
    'localTime': '18:00:00',
    'timezone': 'America/Sao_Paulo',
  },
};
Map<Object?, Object?> finishResult([String id = finishId]) => {
  'providerStopped': true,
  'receipt': {
    'intentId': id,
    'runId': finishRun,
    'trackingSessionId': finishSession,
    'sessionGeneration': 2,
    'expectedVersion': 7,
    'state': 'COMPLETED',
    'finishConsumedAt': '2030-01-01T18:00:01Z',
    'stopAcknowledged': true,
    'stopRequestRef': '12345678-1234-1234-1234-123456789015',
    'stoppedAt': '2030-01-01T18:00:02Z',
  },
};

void main() {
  test('FINISH is a separate purpose and canonical configured host only', () {
    final raw = finishLink().toString();
    expect(DriverRunIntent.parse(finishLink()), isNull);
    final intent = DriverFinishIntent.parse(finishLink())!;
    expect(intent.id, finishId);
    expect(intent.toString(), isNot(contains('AAAA')));
    final https = Uri.parse(
      raw.replaceFirst(
        'vanridesdriver://run/finish',
        'https://driver.example/motorista/fim',
      ),
    );
    expect(DriverFinishIntent.parse(https), isNull);
    expect(
      DriverFinishIntent.parse(https, host: 'driver.example')?.nativeArguments,
      intent.nativeArguments,
    );
    expect(DriverRunIntent.parse(https, host: 'driver.example'), isNull);
    for (final value in [
      raw.replaceFirst('.7.', '.07.'),
      raw.replaceFirst('/finish#', '/finish?next=x#'),
      raw.replaceFirst(finishId, finishId.replaceFirst('1', '%31')),
      raw.replaceFirst('/finish', '/start'),
    ]) {
      expect(DriverFinishIntent.parseRaw(value), isNull);
    }
    // Uri normalizes unreserved escapes before a consumer can inspect them.
    // parse(Uri) cannot reconstruct or reject bytes which that SDK discarded.
    final encoded = raw.replaceFirst(
      finishId,
      finishId.replaceFirst('1', '%31'),
    );
    expect(Uri.parse(encoded).toString(), raw);
    expect(DriverFinishIntent.parse(Uri.parse(encoded))?.id, finishId);
  });
  test('completion requires the exact receipt and provider removal', () {
    final intent = DriverFinishIntent.parse(finishLink())!;
    final preview = DriverFinishPreview.fromMap(finishPreview(), intent);
    expect(preview.accepts(finishResult(), intent), isTrue);
    expect(
      preview.accepts({...finishResult(), 'providerStopped': false}, intent),
      isFalse,
    );
    for (final patch in [
      {'stopAcknowledged': false},
      {'trackingSessionId': finishRun},
      {'sessionGeneration': 3},
      {'intentId': finishRun},
    ]) {
      final result = finishResult();
      result['receipt'] = {...result['receipt'] as Map, ...patch};
      expect(preview.accepts(result, intent), isFalse);
    }
    expect(
      () => DriverFinishPreview.fromMap({
        ...finishPreview(),
        'latitude': 0,
      }, intent),
      throwsFormatException,
    );
  });
}
