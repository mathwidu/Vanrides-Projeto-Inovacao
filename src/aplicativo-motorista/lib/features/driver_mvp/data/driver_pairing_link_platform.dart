import 'package:flutter/services.dart';
import 'driver_pairing_intent.dart';
import 'driver_session_platform.dart';

class DriverPairingReview {
  DriverPairingReview.fromNative(Object? value, DriverPairingIntent intent) {
    if (value is! Map ||
        value.length != 2 ||
        !value.containsKey('preview') ||
        !value.containsKey('session')) {
      throw const FormatException('INVALID_PAIRING_REVIEW');
    }
    final preview = value['preview'];
    if (preview is! Map ||
        preview.length != 7 ||
        preview.keys.any(
          (key) =>
              !{
                'linkId',
                'state',
                'expiresAt',
                'serverTime',
                'sameCurrentDevice',
                'replacesDevice',
                'alreadyConnected',
              }.contains(key),
        ) ||
        preview['linkId'] != intent.id ||
        !{
          'READY',
          'PENDING',
          'APPROVED',
          'REDEEMED',
        }.contains(preview['state']) ||
        preview['sameCurrentDevice'] is! bool ||
        preview['replacesDevice'] is! bool ||
        preview['alreadyConnected'] is! bool) {
      throw const FormatException('INVALID_PAIRING_REVIEW');
    }
    for (final field in ['expiresAt', 'serverTime']) {
      final raw = preview[field];
      if (raw is! String ||
          !raw.endsWith('Z') ||
          DateTime.tryParse(raw)?.isUtc != true) {
        throw const FormatException('INVALID_PAIRING_REVIEW');
      }
    }
    replacesDevice = preview['replacesDevice'] == true;
    alreadyConnected = preview['alreadyConnected'] == true;
    session = DriverSessionSnapshot.fromNative(value['session']);
    if (alreadyConnected &&
        (preview['sameCurrentDevice'] != true ||
            !{
              DriverSessionState.ready,
              DriverSessionState.setupRequired,
            }.contains(session.state))) {
      throw const FormatException('INVALID_PAIRING_REVIEW');
    }
  }
  late final bool replacesDevice, alreadyConnected;
  late final DriverSessionSnapshot session;
}

abstract interface class DriverPairingLinkPlatform {
  Future<DriverPairingReview> prepare(DriverPairingIntent intent);
  Future<DriverSessionSnapshot> claim(DriverPairingIntent intent);
  Future<bool> completeReturn();
}

class NativeDriverPairingLinkPlatform implements DriverPairingLinkPlatform {
  const NativeDriverPairingLinkPlatform();
  static const _channel = MethodChannel(
    'dev.matheusduarte.vanrides.driver/session.v1',
  );
  @override
  Future<bool> completeReturn() async =>
      await _channel.invokeMethod<bool>('completePairingReturn') == true;
  @override
  Future<DriverPairingReview> prepare(DriverPairingIntent intent) async =>
      DriverPairingReview.fromNative(
        await _channel.invokeMethod<Object?>(
          'preparePairingLink',
          intent.nativeArguments,
        ),
        intent,
      );
  @override
  Future<DriverSessionSnapshot> claim(DriverPairingIntent intent) async =>
      DriverSessionSnapshot.fromNative(
        await _channel.invokeMethod<Object?>(
          'claimPairingLink',
          intent.nativeArguments,
        ),
      );
}
