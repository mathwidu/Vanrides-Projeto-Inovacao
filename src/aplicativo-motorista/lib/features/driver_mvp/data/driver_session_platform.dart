import 'package:flutter/services.dart';

enum DriverSessionAction {
  getState,
  beginPairing,
  syncSession,
  refreshSession,
  revokeSession,
}

enum DriverSessionState {
  unconfigured('UNCONFIGURED'),
  unpaired('UNPAIRED'),
  requesting('REQUESTING'),
  pending('PENDING'),
  setupRequired('SETUP_REQUIRED'),
  ready('READY'),
  expired('EXPIRED'),
  revoking('REVOKING'),
  revoked('REVOKED');

  const DriverSessionState(this.wire);
  final String wire;
}

class DriverSessionSnapshot {
  const DriverSessionSnapshot(this.state, {this.expiresAt});
  final DriverSessionState state;
  final DateTime? expiresAt;

  factory DriverSessionSnapshot.fromNative(Object? value) {
    if (value is! Map ||
        value.keys.any((k) => k != 'state' && k != 'expiresAt')) {
      throw const DriverSessionFailure('PROTOCOL');
    }
    final state =
        DriverSessionState.values
            .where((s) => s.wire == value['state'])
            .firstOrNull;
    if (state == null) throw const DriverSessionFailure('PROTOCOL');
    final raw = value['expiresAt'];
    if (!value.containsKey('expiresAt')) return DriverSessionSnapshot(state);
    if (state != DriverSessionState.pending ||
        raw is! String ||
        !raw.endsWith('Z')) {
      throw const DriverSessionFailure('PROTOCOL');
    }
    final expiry = DateTime.tryParse(raw);
    if (expiry == null || !expiry.isUtc) {
      throw const DriverSessionFailure('PROTOCOL');
    }
    return DriverSessionSnapshot(state, expiresAt: expiry);
  }
}

class DriverSessionFailure implements Exception {
  const DriverSessionFailure(this.code);
  final String code;

  String get message => switch (code) {
    'NETWORK' => 'Confira a conexão e toque em tentar novamente.',
    'RATE_LIMITED' => 'Aguarde um pouco antes de tentar novamente.',
    'AUTH' =>
      'A autorização precisa ser verificada. Atualize o estado do aparelho.',
    'STORAGE' =>
      'Desbloqueie o celular e tente novamente para acessar o vínculo.',
    'INVALID_STATE' => 'O vínculo mudou. Atualize o estado do aparelho.',
    _ =>
      'Não foi possível concluir esta etapa. Tente novamente ou fale com o suporte pelo WhatsApp.',
  };
}

abstract interface class DriverSessionPlatform {
  Future<DriverSessionSnapshot> run(DriverSessionAction action);
  Future<String?> showPairingCode();
}

class NativeDriverSessionPlatform implements DriverSessionPlatform {
  const NativeDriverSessionPlatform();
  static const channel = MethodChannel(
    'dev.matheusduarte.vanrides.driver/session.v1',
  );

  Future<Object?> _invoke(String method) async {
    try {
      return await channel.invokeMethod<Object?>(method);
    } on PlatformException catch (error) {
      final code = switch (error.code) {
        'NETWORK' || 'NETWORK_ERROR' => 'NETWORK',
        'RATE_LIMITED' => 'RATE_LIMITED',
        'AUTH' || 'SESSION_EXPIRED' => 'AUTH',
        'STORAGE' ||
        'STORAGE_ERROR' ||
        'SECURE_STORAGE_UNAVAILABLE' ||
        'KEY_UNAVAILABLE' ||
        'DEVICE_KEY_UNAVAILABLE' => 'STORAGE',
        'INVALID_STATE' ||
        'ACTION_NOT_ALLOWED' ||
        'UI_NOT_VISIBLE' ||
        'BUSY' => 'INVALID_STATE',
        _ => 'UNAVAILABLE',
      };
      throw DriverSessionFailure(code);
    } on MissingPluginException {
      throw const DriverSessionFailure('UNAVAILABLE');
    }
  }

  @override
  Future<DriverSessionSnapshot> run(DriverSessionAction action) async =>
      DriverSessionSnapshot.fromNative(await _invoke(action.name));

  @override
  Future<String?> showPairingCode() async {
    final value = await _invoke('showPairingCode');
    if (value == null) return null;
    if (value is! String ||
        !RegExp(r'^[BCDFGHJKMNPQRSTVWXYZ]{8}$').hasMatch(value) ||
        value.length != 8) {
      throw const DriverSessionFailure('PROTOCOL');
    }
    return value;
  }
}
