import 'package:flutter/services.dart';

import 'driver_run_intent.dart';

/// Optional native metadata only; unknown keys never cross this allowlist.
class DriverTrackingDiagnostics {
  const DriverTrackingDiagnostics.empty() : values = const {};
  const DriverTrackingDiagnostics._(this.values);

  factory DriverTrackingDiagnostics.fromMap(Map<Object?, Object?> data) {
    final values = <String, Object>{};
    for (final key in _nonnegativeIntegers) {
      final value = data[key];
      if (value == null) continue;
      if (value is! int || value < 0) {
        throw const FormatException('INVALID_NATIVE_STATUS');
      }
      values[key] = value;
    }
    for (final key in _signedAges) {
      final value = data[key];
      if (value == null) continue;
      if (value is! int) {
        throw const FormatException('INVALID_NATIVE_STATUS');
      }
      values[key] = value;
    }
    final accuracy = data['lastLocationAccuracyM'];
    if (accuracy != null) {
      if (accuracy is! num || !accuracy.isFinite || accuracy < 0) {
        throw const FormatException('INVALID_NATIVE_STATUS');
      }
      values['lastLocationAccuracyM'] = accuracy;
    }
    for (final entry in _enums.entries) {
      final value = data[entry.key];
      if (value == null) continue;
      if (value is! String || !entry.value.contains(value)) {
        throw const FormatException('INVALID_NATIVE_STATUS');
      }
      values[entry.key] = value;
    }
    for (final key in const [
      'reconciliationPending',
      'diagnosticWriteFailed',
    ]) {
      final value = data[key];
      if (value == null) continue;
      if (value is! bool) {
        throw const FormatException('INVALID_NATIVE_STATUS');
      }
      values[key] = value;
    }
    final energy = data['energySession'];
    if (energy != null) {
      if (energy is! Map) {
        throw const FormatException('INVALID_NATIVE_STATUS');
      }
      values['energySession'] = _energySession(energy);
    }
    return DriverTrackingDiagnostics._(Map.unmodifiable(values));
  }

  static Map<String, Object> _energySession(Map<Object?, Object?> data) {
    final safe = <String, Object>{};
    for (final entry
        in const {
          'state': {'OBSERVING', 'STOP_CONFIRMED', 'INCOMPLETE'},
          'observationMode': {'SESSION_BROADCASTS', 'SNAPSHOTS_ONLY'},
          'unpluggedWindowState': {
            'NOT_STARTED',
            'OBSERVING',
            'STOP_CONFIRMED',
            'POWER_RETURNED',
            'POWER_UNKNOWN',
            'INCOMPLETE',
            'UNAVAILABLE',
          },
        }.entries) {
      final value = data[entry.key];
      if (value is! String || !entry.value.contains(value)) {
        throw const FormatException('INVALID_NATIVE_STATUS');
      }
      safe[entry.key] = value;
    }
    for (final key in const ['powerSaveModeChanged', 'powerStateUncertain']) {
      final value = data[key];
      if (value is! bool) {
        throw const FormatException('INVALID_NATIVE_STATUS');
      }
      safe[key] = value;
    }
    for (final key in const [
      'start',
      'stop',
      'unpluggedStart',
      'unpluggedEnd',
    ]) {
      final value = data[key];
      if (value == null && key != 'start') continue;
      if (value is! Map) {
        throw const FormatException('INVALID_NATIVE_STATUS');
      }
      safe[key] = _energySnapshot(value);
    }
    if ((safe['state'] == 'STOP_CONFIRMED') != safe.containsKey('stop')) {
      throw const FormatException('INVALID_NATIVE_STATUS');
    }
    return Map.unmodifiable(safe);
  }

  static Map<String, Object> _energySnapshot(Map<Object?, Object?> data) {
    final safe = <String, Object>{};
    for (final key in const ['elapsedMs', 'batteryPercent', 'bootCount']) {
      final value = data[key];
      if (value == null && key != 'elapsedMs') continue;
      if (value is! int ||
          value < 0 ||
          (key == 'batteryPercent' && value > 100)) {
        throw const FormatException('INVALID_NATIVE_STATUS');
      }
      safe[key] = value;
    }
    for (final entry
        in const {
          'chargeState': {
            'UNKNOWN',
            'CHARGING',
            'DISCHARGING',
            'NOT_CHARGING',
            'FULL',
          },
          'powerSource': {
            'UNKNOWN',
            'NONE',
            'AC',
            'USB',
            'WIRELESS',
            'DOCK',
            'OTHER',
          },
        }.entries) {
      final value = data[entry.key];
      if (value is! String || !entry.value.contains(value)) {
        throw const FormatException('INVALID_NATIVE_STATUS');
      }
      safe[entry.key] = value;
    }
    final saver = data['powerSaveMode'];
    if (saver != null) {
      if (saver is! bool) {
        throw const FormatException('INVALID_NATIVE_STATUS');
      }
      safe['powerSaveMode'] = saver;
    }
    return Map.unmodifiable(safe);
  }

  final Map<String, Object> values;
  static const _nonnegativeIntegers = {
    'nativeStopCalledAt', 'nativeStopCompletedAt',
    'leaseDeadlineElapsedMs', 'stopRequestedElapsedMs', 'stoppedElapsedMs',
    'nativeStopCalledElapsedMs', 'nativeStopCompletedElapsedMs',
    'lateCallbacksDiscarded', 'previousNativeStopCalledAt',
    'previousNativeStopCompletedAt', 'previousNativeStopCalledElapsedMs',
    'previousNativeStopCompletedElapsedMs', 'previousLeaseDeadlineElapsedMs',
    'diagnosticBootCount', 'previousDiagnosticBootCount',
    'remoteStopAcknowledgedAt', 'processReconciledAt',
    // iOS equivalents remain optional; older native snapshots omit them.
    'nativeStopMonotonicMs', 'leaseDeadlineMonotonicMs',
    'activeLeaseDeadlineMonotonicMs', 'discardedCallbacks',
    'locationCallbacksReceived', 'locationSamplesOffered',
    'lastLocationCallbackElapsedMs', 'diagnosticSnapshotElapsedMs',
  };
  static const _signedAges = {
    'lastLocationAgeElapsedMs',
    'lastLocationAgeWallMs',
    'lastLocationAgeElapsedAtCallbackMs',
    'lastLocationAgeWallAtCallbackMs',
  };
  static const _ageStates = {'VALID', 'INVALID', 'FUTURE'};
  static const _enums = {
    'lastLocationAccuracyState': {'VALID', 'MISSING', 'INVALID'},
    'lastLocationElapsedState': _ageStates,
    'lastLocationWallState': _ageStates,
    'lastLocationElapsedStateAtCallback': _ageStates,
    'lastLocationWallStateAtCallback': _ageStates,
    'lastLocationCallbackDisposition': {
      'RECEIVED',
      'OFFERED',
      'DISCARDED',
      'PERMISSION_REVOKED',
    },
  };
}

/// Sanitized status only. Coordinates and credentials stay in the native layer.
class DriverTrackingStatus {
  const DriverTrackingStatus({
    this.state = 'UNCONFIGURED',
    this.credentialConfigured = false,
    this.permission = 'UNKNOWN',
    this.notificationPermissionGranted = true,
    this.stopPending = false,
    this.queued = 0,
    this.accepted = 0,
    this.duplicates = 0,
    this.rejected = 0,
    this.dropped = 0,
    this.gpsState = 'UNKNOWN',
    this.leaseExpiresAt,
    this.reason,
    this.stopRequestedAt,
    this.stoppedAt,
    this.diagnostics = const DriverTrackingDiagnostics.empty(),
  });

  factory DriverTrackingStatus.fromMap(Map<Object?, Object?> data) {
    const states = {
      'UNCONFIGURED',
      'READY',
      'STARTING',
      'TRACKING',
      'STOPPED',
      'ERROR',
    };
    const permissions = {'UNKNOWN', 'DENIED', 'APPROXIMATE', 'GRANTED'};
    const gpsStates = {'UNKNOWN', 'FRESH', 'STALE', 'CLOCK_SKEW'};
    if (!states.contains(data['state']) ||
        data['credentialConfigured'] is! bool ||
        !permissions.contains(data['permission']) ||
        !gpsStates.contains(data['gpsState']) ||
        (data.containsKey('notificationPermissionGranted') &&
            data['notificationPermissionGranted'] is! bool) ||
        (data.containsKey('stopPending') && data['stopPending'] is! bool)) {
      throw const FormatException('INVALID_NATIVE_STATUS');
    }
    int counter(String key) {
      final value = data[key];
      if (value is! int || value < 0) {
        throw const FormatException('INVALID_NATIVE_STATUS');
      }
      return value;
    }

    int? instant(String key) {
      final value = data[key];
      if (value == null) return null;
      if (value is! int || value < 0) {
        throw const FormatException('INVALID_NATIVE_STATUS');
      }
      return value;
    }

    final reason = data['reason'];
    if (reason != null &&
        (reason is! String || !RegExp(r'^[A-Z0-9_]{1,64}$').hasMatch(reason))) {
      throw const FormatException('INVALID_NATIVE_STATUS');
    }
    return DriverTrackingStatus(
      state: data['state']! as String,
      credentialConfigured: data['credentialConfigured']! as bool,
      permission: data['permission']! as String,
      notificationPermissionGranted:
          data['notificationPermissionGranted'] as bool? ?? true,
      stopPending: data['stopPending'] as bool? ?? false,
      queued: counter('queued'),
      accepted: counter('accepted'),
      duplicates: counter('duplicates'),
      rejected: counter('rejected'),
      dropped: counter('dropped'),
      gpsState: data['gpsState']! as String,
      leaseExpiresAt: instant('leaseExpiresAt'),
      reason: reason as String?,
      stopRequestedAt: instant('stopRequestedAt'),
      stoppedAt: instant('stoppedAt'),
      diagnostics: DriverTrackingDiagnostics.fromMap(data),
    );
  }

  final String state;
  final bool credentialConfigured;
  final String permission;
  final bool notificationPermissionGranted;
  final bool stopPending;
  final int queued;
  final int accepted;
  final int duplicates;
  final int rejected;
  final int dropped;
  final String gpsState;
  final int? leaseExpiresAt;
  final String? reason;
  final int? stopRequestedAt;
  final int? stoppedAt;
  final DriverTrackingDiagnostics diagnostics;
  bool get isActive => state == 'STARTING' || state == 'TRACKING';

  /// Rebuild from typed fields instead of exporting the original native map.
  Map<String, Object?> toSanitizedMap() => {
    'state': state,
    'credentialConfigured': credentialConfigured,
    'permission': permission,
    'notificationPermissionGranted': notificationPermissionGranted,
    'stopPending': stopPending,
    'gpsState': gpsState,
    'queued': queued,
    'accepted': accepted,
    'duplicates': duplicates,
    'rejected': rejected,
    'dropped': dropped,
    'leaseExpiresAt': leaseExpiresAt,
    'reason':
        reason == null || _diagnosticReasons.contains(reason)
            ? reason
            : 'UNRECOGNIZED',
    'stopRequestedAt': stopRequestedAt,
    'stoppedAt': stoppedAt,
    ...diagnostics.values,
  };

  // Even a syntactically valid unknown reason must not export arbitrary text.
  static const _diagnosticReasons = {
    'RECONCILIATION_REQUIRED',
    'STOP_RECONCILIATION_REQUIRED',
    'STOP_STORAGE_ERROR',
    'CREDENTIAL_STORAGE_ERROR',
    'UI_NOT_VISIBLE',
    'INVALID_CONTRACT',
    'REMOTE_STOP',
    'INVALID_LEASE',
    'SESSION_TERMINAL',
    'START_NOT_ALLOWED',
    'FOREGROUND_SERVICE_FAILED',
    'LOCATION_PROVIDER_FAILED',
    'SERVICE_DESTROYED',
    'LEASE_EXPIRED',
    'LOCATION_PERMISSION_REVOKED',
    'USER_STOP',
    'NATIVE_STOP_FAILED',
    'PROCESS_RECREATED',
    'START_CANCELLED',
    'STOP_CONFIRMED',
    'PERMISSION_REVOKED',
    'SESSION_OWNER_MISMATCH',
    'NETWORK_RETRY',
    'REDIRECT_REJECTED',
    'HTTP_401',
    'HTTP_403',
    'HTTP_404',
    'HTTP_409',
    'HTTP_410',
    'INVALID_ARGUMENTS',
    'INVALID_ENDPOINT',
    'INVALID_CREDENTIAL',
    'CREDENTIAL_STORAGE',
    'CREDENTIAL_REQUIRED',
    'FOREGROUND_REQUIRED',
    'PERMISSION_REQUIRED',
    'BUSY',
    'CANCELLED',
    'NETWORK_ERROR',
    'SERVER_REJECTED',
    'BACKGROUND_PERMISSION_REQUIRED',
    'PRECISE_PERMISSION_REQUIRED',
    'PROCESS_TERMINATING',
  };
}

abstract interface class DriverTrackingPlatform {
  Future<DriverTrackingStatus> configureCredential(
    String baseUrl,
    String token,
  );
  Future<DriverTrackingStatus> requestPermission();
  Future<DriverTrackingStatus> start(String intentId);
  Future<DriverTrackingStatus> stop();
  Future<DriverTrackingStatus> getStatus();
  Future<DriverTrackingStatus> syncStatus();
}

abstract interface class DriverRunPlatform {
  Future<DriverTrackingStatus> startRun(DriverRunIntent intent);
}

class NativeDriverTrackingPlatform
    implements DriverTrackingPlatform, DriverRunPlatform {
  const NativeDriverTrackingPlatform({
    MethodChannel channel = const MethodChannel('vanrides.driver/tracking.v1'),
  }) : _channel = channel;

  final MethodChannel _channel;

  Future<DriverTrackingStatus> _invoke(
    String method, [
    Map<String, Object>? args,
  ]) async {
    final data = await _channel.invokeMapMethod<Object?, Object?>(method, args);
    if (data == null) throw const FormatException('INVALID_NATIVE_STATUS');
    return DriverTrackingStatus.fromMap(data);
  }

  @override
  Future<DriverTrackingStatus> configureCredential(
    String baseUrl,
    String token,
  ) => _invoke('configureCredential', {
    'kind': 'TEST_BEARER',
    'baseUrl': baseUrl,
    'token': token,
  });
  @override
  Future<DriverTrackingStatus> requestPermission() =>
      _invoke('requestPermission');
  @override
  Future<DriverTrackingStatus> start(String intentId) =>
      _invoke('start', {'intentId': intentId});
  @override
  Future<DriverTrackingStatus> startRun(DriverRunIntent intent) =>
      _invoke('startRun', intent.nativeArguments);
  @override
  Future<DriverTrackingStatus> stop() => _invoke('stop');
  @override
  Future<DriverTrackingStatus> getStatus() => _invoke('getStatus');
  @override
  Future<DriverTrackingStatus> syncStatus() => _invoke('syncStatus');
}
