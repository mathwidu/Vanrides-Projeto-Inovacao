/// Temporary authorization stays in memory and never becomes visible copy.
class DriverRunIntent {
  const DriverRunIntent._(this.id, this.expectedVersion, this._token);
  final String id;
  final int expectedVersion;
  final String _token;

  static DriverRunIntent? parse(Uri uri, {String host = ''}) =>
      parseRaw(uri.toString(), host: host);

  static DriverRunIntent? parseRaw(String raw, {String host = ''}) {
    final uri = Uri.tryParse(raw);
    if (uri == null) return null;
    if (raw.contains('%') ||
        uri.hasQuery ||
        uri.userInfo.isNotEmpty ||
        uri.hasPort) {
      return null;
    }
    final base = raw.split('#').first;
    if (base != 'vanridesdriver://run/start' &&
        (host.isEmpty || base != 'https://$host/motorista/saida')) {
      return null;
    }
    final match = RegExp(
      r'^v1\.([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\.(0|[1-9][0-9]{0,15})\.([A-Za-z0-9_-]{42}[AEIMQUYcgkosw048])$',
    ).firstMatch(uri.fragment);
    if (match == null) return null;
    final version = int.tryParse(match[2]!);
    if (version == null || version > 9007199254740991) return null;
    return DriverRunIntent._(match[1]!, version, match[3]!);
  }

  Map<String, Object> get nativeArguments => {
    'intentId': id,
    'expectedVersion': expectedVersion,
    'intentToken': _token,
  };

  @override
  String toString() => 'DriverRunIntent(redacted)';
}
