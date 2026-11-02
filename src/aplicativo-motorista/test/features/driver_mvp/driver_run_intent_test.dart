import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/driver_mvp/data/driver_run_intent.dart';

const _id = '12345678-1234-1234-1234-123456789012';
final _token = List.filled(43, 'A').join();
String _link([String base = 'vanridesdriver://run/start']) =>
    '$base#v1.$_id.7.$_token';

void main() {
  test('scheme and configured HTTPS use the same opaque authorization', () {
    final scheme = DriverRunIntent.parse(Uri.parse(_link()))!;
    final https =
        DriverRunIntent.parse(
          Uri.parse(_link('https://driver.example/motorista/saida')),
          host: 'driver.example',
        )!;
    expect(scheme.nativeArguments, https.nativeArguments);
    expect(scheme.nativeArguments['expectedVersion'], 7);
    expect(scheme.toString(), isNot(contains(_token)));
  });

  test('rejects unconfigured hosts and ambiguous or noncanonical links', () {
    final valid = _link();
    for (final raw in [
      _link('https://driver.example/motorista/saida'),
      valid.replaceFirst('/start#', '/start?next=x#'),
      valid.replaceFirst('/start#', '/start/#'),
      valid.replaceFirst('v1.', 'v2.'),
      valid.replaceFirst('.7.', '.07.'),
      valid.replaceFirst('.7.', '.9007199254740992.'),
      valid.replaceFirst(_id, _id.replaceFirst('1', '%31')),
      '$valid.extra',
      '${valid.substring(0, valid.length - 1)}B',
      _link('https://evil.example/motorista/saida'),
      _link('https://driver.example:443/motorista/saida'),
      _link('https://user@driver.example/motorista/saida'),
    ]) {
      expect(
        DriverRunIntent.parseRaw(raw),
        isNull,
        reason: raw.split('#').first,
      );
    }
  });
}
