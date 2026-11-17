import 'driver_run_intent.dart';

/// A separate purpose; it can never be passed to startRun.
class DriverFinishIntent {
  DriverFinishIntent._(this._authorization)
    : _restoredId = null,
      _restoredVersion = null;
  DriverFinishIntent._restored(this._restoredId, this._restoredVersion)
    : _authorization = null;
  final DriverRunIntent? _authorization;
  final String? _restoredId;
  final int? _restoredVersion;
  bool get restored => _authorization == null;
  String get id => _authorization?.id ?? _restoredId!;
  int get expectedVersion =>
      _authorization?.expectedVersion ?? _restoredVersion!;
  Map<String, Object> get nativeArguments =>
      _authorization?.nativeArguments ??
      {'intentId': id, 'expectedVersion': expectedVersion};
  static DriverFinishIntent fromJournal(String id, int version) {
    if (!RegExp(
          r'^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$',
        ).hasMatch(id) ||
        version < 0 ||
        version > 9007199254740991) {
      throw const FormatException('INVALID_FINISH_JOURNAL');
    }
    return DriverFinishIntent._restored(id, version);
  }

  /// Uri may have already normalized escapes; raw native entry validation is
  /// still required before the collector accepts this authorization.
  static DriverFinishIntent? parse(Uri uri, {String host = ''}) =>
      parseRaw(uri.toString(), host: host);

  static DriverFinishIntent? parseRaw(String raw, {String host = ''}) {
    if (raw.contains('%')) return null;
    final base = raw.split('#').first;
    if (base != 'vanridesdriver://run/finish' &&
        (host.isEmpty || base != 'https://$host/motorista/fim')) {
      return null;
    }
    final start = DriverRunIntent.parseRaw(
      raw.replaceFirst(base, 'vanridesdriver://run/start'),
    );
    return start == null ? null : DriverFinishIntent._(start);
  }

  @override
  String toString() => 'DriverFinishIntent(redacted)';
}
