import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/deep_linking/data/deep_link_parser.dart';
import 'package:vanrides_frontend_dev/features/deep_linking/domain/deep_link_models.dart';

void main() {
  final parser = DeepLinkParser();

  test('parse vanrides://viagem/42 retorna ViagemDeepLink', () {
    final result = parser.parseString('vanrides://viagem/42');

    expect(result, isA<ViagemDeepLink>());
    expect((result as ViagemDeepLink).tripId, 42);
  });

  test('parse vanrides://rastreio/7 retorna RastreioDeepLink', () {
    final result = parser.parseString('vanrides://rastreio/7');

    expect(result, isA<RastreioDeepLink>());
    expect((result as RastreioDeepLink).passageiroId, 7);
  });

  test('parse vanrides://convite/INV-2026-ABCD retorna ConviteDeepLink', () {
    final result = parser.parseString('vanrides://convite/INV-2026-ABCD');

    expect(result, isA<ConviteDeepLink>());
    expect((result as ConviteDeepLink).codigo, 'INV-2026-ABCD');
  });

  test(
    'parse vanrides://driver-invite/token-123 retorna DriverInviteDeepLink',
    () {
      final result = parser.parseString('vanrides://driver-invite/token-123');

      expect(result, isA<DriverInviteDeepLink>());
      expect((result as DriverInviteDeepLink).token, 'token-123');
    },
  );

  test('parse vanrides://desconhecido retorna UnknownDeepLink', () {
    final result = parser.parseString('vanrides://desconhecido');

    expect(result, isA<UnknownDeepLink>());
  });

  test('parse https://exemplo.com/foo retorna UnknownDeepLink', () {
    final result = parser.parseString('https://exemplo.com/foo');

    expect(result, isA<UnknownDeepLink>());
  });

  test('parse vanrides://viagem/abc retorna UnknownDeepLink', () {
    final result = parser.parseString('vanrides://viagem/abc');

    expect(result, isA<UnknownDeepLink>());
  });
}
