import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/app/session/app_user_role.dart';
import 'package:vanrides_frontend_dev/features/deep_linking/data/deep_link_resolver.dart';
import 'package:vanrides_frontend_dev/features/deep_linking/domain/deep_link_models.dart';

void main() {
  final resolver = DeepLinkResolver();

  test('convite é Allowed para qualquer papel incluindo null', () {
    const target = ConviteDeepLink(codigo: 'INV-2026-ABCD');

    expect(resolver.resolve(target, null), isA<DeepLinkAllowed>());
    expect(
      resolver.resolve(target, AppUserRole.passenger),
      isA<DeepLinkAllowed>(),
    );
    expect(
      resolver.resolve(target, AppUserRole.driver),
      isA<DeepLinkAllowed>(),
    );
    expect(
      resolver.resolve(target, AppUserRole.guardian),
      isA<DeepLinkAllowed>(),
    );
    expect(
      resolver.resolve(target, AppUserRole.companyAdmin),
      isA<DeepLinkAllowed>(),
    );
    expect(
      resolver.resolve(target, AppUserRole.unknown),
      isA<DeepLinkAllowed>(),
    );
  });

  test('driver invite é Allowed para qualquer papel incluindo null', () {
    const target = DriverInviteDeepLink(token: 'driver-token-123');

    expect(resolver.resolve(target, null), isA<DeepLinkAllowed>());
    expect(
      resolver.resolve(target, AppUserRole.driver),
      isA<DeepLinkAllowed>(),
    );
    expect(
      resolver.resolve(target, AppUserRole.passenger),
      isA<DeepLinkAllowed>(),
    );
  });

  test('viagem é Allowed para passageiro motorista e responsável', () {
    const target = ViagemDeepLink(tripId: 42);

    expect(
      resolver.resolve(target, AppUserRole.passenger),
      isA<DeepLinkAllowed>(),
    );
    expect(
      resolver.resolve(target, AppUserRole.driver),
      isA<DeepLinkAllowed>(),
    );
    expect(
      resolver.resolve(target, AppUserRole.guardian),
      isA<DeepLinkAllowed>(),
    );
  });

  test('viagem é RoleMismatch para companyAdmin', () {
    const target = ViagemDeepLink(tripId: 42);

    final resolution = resolver.resolve(target, AppUserRole.companyAdmin);

    expect(resolution, isA<DeepLinkRoleMismatch>());
    expect(
      (resolution as DeepLinkRoleMismatch).userRole,
      AppUserRole.companyAdmin,
    );
  });

  test('unknown é sempre Unsupported', () {
    const target = UnknownDeepLink(rawUri: 'vanrides://foo/bar');

    expect(resolver.resolve(target, null), isA<DeepLinkUnsupported>());
    expect(
      resolver.resolve(target, AppUserRole.passenger),
      isA<DeepLinkUnsupported>(),
    );
    expect(
      resolver.resolve(target, AppUserRole.companyAdmin),
      isA<DeepLinkUnsupported>(),
    );
  });
}
