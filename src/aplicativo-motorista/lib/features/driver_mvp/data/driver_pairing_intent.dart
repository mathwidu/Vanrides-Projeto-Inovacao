/// Link authorization is private and never becomes visible copy.
class DriverPairingIntent {
  const DriverPairingIntent._(this.id, this._token);
  final String id;
  final String _token;
  static DriverPairingIntent? parse(Uri uri, {String host = ''}) =>
      parseRaw(uri.toString(), host: host);
  // Uri may normalize escapes. Native entry checks the raw URL independently.
  static DriverPairingIntent? parseRaw(String raw, {String host = ''}) {
    if (raw.contains('%')) return null;
    final uri = Uri.tryParse(raw);
    if (uri == null || uri.hasQuery || uri.hasPort || uri.userInfo.isNotEmpty) {
      return null;
    }
    final base = raw.split('#').first;
    if (base != 'vanridesdriver://pair/connect' &&
        (host.isEmpty || base != 'https://$host/motorista/conectar')) {
      return null;
    }
    final match = RegExp(
      r'^v1\.([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\.([A-Za-z0-9_-]{42}[AEIMQUYcgkosw048])$',
    ).firstMatch(uri.fragment);
    return match == null ? null : DriverPairingIntent._(match[1]!, match[2]!);
  }

  Map<String, Object> get nativeArguments => {
    'linkId': id,
    'linkToken': _token,
  };
  @override
  String toString() => 'DriverPairingIntent(redacted)';
}
