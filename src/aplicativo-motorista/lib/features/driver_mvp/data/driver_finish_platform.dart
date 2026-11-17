import 'package:flutter/services.dart';
import 'driver_finish_intent.dart';

class DriverFinishPreview {
  DriverFinishPreview.fromMap(
    Map<Object?, Object?> value,
    DriverFinishIntent intent,
  ) : intentId = _uuid(value['intentId']),
      runId = _uuid(value['runId']),
      sessionId = _uuid(value['trackingSessionId']),
      generation = _integer(value['sessionGeneration'], 1),
      version = _integer(value['expectedVersion'], 0),
      expiresAt = _instant(value['expiresAt']),
      state = value['state'] as String,
      trip = Map<String, Object?>.from(value['trip'] as Map) {
    if (intentId != intent.id ||
        version != intent.expectedVersion ||
        !{'READY', 'COMPLETED'}.contains(state) ||
        value['stopAcknowledged'] is! bool ||
        trip.keys.toSet().difference({
          'name',
          'direction',
          'localDate',
          'localTime',
          'timezone',
        }).isNotEmpty ||
        trip.length != 5 ||
        trip['name'] is! String ||
        (trip['name'] as String).isEmpty ||
        (trip['name'] as String).length > 80 ||
        !{'OUTBOUND', 'RETURN'}.contains(trip['direction']) ||
        !RegExp(
          r'^\d{4}-\d{2}-\d{2}$',
        ).hasMatch(trip['localDate'] as String? ?? '') ||
        !RegExp(
          r'^([01]\d|2[0-3]):[0-5]\d(:[0-5]\d)?$',
        ).hasMatch(trip['localTime'] as String? ?? '') ||
        trip['timezone'] is! String ||
        (trip['timezone'] as String).isEmpty) {
      throw const FormatException('INVALID_FINISH_PREVIEW');
    }
    _exact(value, {
      'intentId',
      'runId',
      'trackingSessionId',
      'sessionGeneration',
      'expectedVersion',
      'expiresAt',
      'state',
      'trip',
      'stopAcknowledged',
    });
  }
  final String intentId, runId, sessionId, state;
  final int generation, version;
  final DateTime expiresAt;
  final Map<String, Object?> trip;
  String get label =>
      '${trip['name']} · ${trip['direction'] == 'OUTBOUND' ? 'ida' : 'volta'}'
      ' · ${trip['localDate']} às ${(trip['localTime'] as String).substring(0, 5)}';

  bool accepts(Map<Object?, Object?> result, DriverFinishIntent intent) {
    _exact(result, {'receipt', 'providerStopped'});
    final receipt = Map<Object?, Object?>.from(result['receipt'] as Map);
    _exact(receipt, {
      'intentId',
      'runId',
      'trackingSessionId',
      'sessionGeneration',
      'expectedVersion',
      'state',
      'finishConsumedAt',
      'stopAcknowledged',
      'stopRequestRef',
      'stoppedAt',
    });
    return result['providerStopped'] == true &&
        intent.id == intentId &&
        receipt['intentId'] == intentId &&
        receipt['runId'] == runId &&
        receipt['trackingSessionId'] == sessionId &&
        receipt['sessionGeneration'] == generation &&
        receipt['expectedVersion'] == version &&
        receipt['state'] == 'COMPLETED' &&
        receipt['stopAcknowledged'] == true &&
        _instant(receipt['finishConsumedAt']).isUtc &&
        _instant(receipt['stoppedAt']).isUtc &&
        (receipt['stopRequestRef'] == null ||
            _uuid(receipt['stopRequestRef']).isNotEmpty);
  }
}

void _exact(Map<Object?, Object?> value, Set<String> fields) {
  if (value.length != fields.length ||
      value.keys.any((key) => !fields.contains(key))) {
    throw const FormatException('INVALID_FINISH_CONTRACT');
  }
}

String _uuid(Object? value) {
  if (value is! String ||
      !RegExp(
        r'^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$',
      ).hasMatch(value)) {
    throw const FormatException('INVALID_FINISH_CONTRACT');
  }
  return value;
}

int _integer(Object? value, int minimum) {
  if (value is! int || value < minimum || value > 9007199254740991) {
    throw const FormatException('INVALID_FINISH_CONTRACT');
  }
  return value;
}

DateTime _instant(Object? value) {
  final parsed =
      value is String && value.endsWith('Z') ? DateTime.tryParse(value) : null;
  if (parsed == null || !parsed.isUtc) {
    throw const FormatException('INVALID_FINISH_CONTRACT');
  }
  return parsed;
}

abstract interface class DriverFinishPlatform {
  Future<DriverFinishPreview?> pendingFinish();
  Future<DriverFinishPreview> previewFinish(DriverFinishIntent intent);
  Future<Map<Object?, Object?>> finishRun(DriverFinishIntent intent);
}

class NativeDriverFinishPlatform implements DriverFinishPlatform {
  const NativeDriverFinishPlatform();
  static const _channel = MethodChannel('vanrides.driver/tracking.v1');
  @override
  Future<DriverFinishPreview?> pendingFinish() async {
    final value = await _channel.invokeMapMethod<Object?, Object?>(
      'getPendingFinish',
    );
    if (value == null) return null;
    return DriverFinishPreview.fromMap(
      value,
      DriverFinishIntent.fromJournal(
        _uuid(value['intentId']),
        _integer(value['expectedVersion'], 0),
      ),
    );
  }

  @override
  Future<DriverFinishPreview> previewFinish(DriverFinishIntent intent) async {
    final value = await _channel.invokeMapMethod<Object?, Object?>(
      intent.restored ? 'previewPendingFinish' : 'previewFinish',
      intent.nativeArguments,
    );
    if (value == null) throw const FormatException('INVALID_FINISH_CONTRACT');
    return DriverFinishPreview.fromMap(value, intent);
  }

  @override
  Future<Map<Object?, Object?>> finishRun(DriverFinishIntent intent) async {
    final value = await _channel.invokeMapMethod<Object?, Object?>(
      intent.restored ? 'retryFinish' : 'finishRun',
      intent.nativeArguments,
    );
    if (value == null) throw const FormatException('INVALID_FINISH_CONTRACT');
    return value;
  }
}
