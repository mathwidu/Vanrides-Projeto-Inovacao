import '../../../app/session/app_user_role.dart';
import '../domain/deep_link_models.dart';

/// Decide se um deep link é resolvível pelo papel ativo do usuário.
/// Não navega — apenas indica qual ação é apropriada.
sealed class DeepLinkResolution {
  const DeepLinkResolution();
}

class DeepLinkAllowed extends DeepLinkResolution {
  const DeepLinkAllowed(this.target);

  final DeepLinkTarget target;
}

class DeepLinkRoleMismatch extends DeepLinkResolution {
  const DeepLinkRoleMismatch({required this.target, required this.userRole});

  final DeepLinkTarget target;
  final AppUserRole userRole;
}

class DeepLinkUnsupported extends DeepLinkResolution {
  const DeepLinkUnsupported(this.target);

  final DeepLinkTarget target;
}

class DeepLinkResolver {
  /// `userRole == null` significa usuário não logado.
  DeepLinkResolution resolve(DeepLinkTarget target, AppUserRole? userRole) {
    if (target is UnknownDeepLink) {
      return DeepLinkUnsupported(target);
    }

    if (target is ConviteDeepLink || target is DriverInviteDeepLink) {
      return DeepLinkAllowed(target);
    }

    if (userRole == null) {
      return DeepLinkRoleMismatch(
        target: target,
        userRole: AppUserRole.unknown,
      );
    }

    if (target is ViagemDeepLink || target is RastreioDeepLink) {
      switch (userRole) {
        case AppUserRole.passenger:
        case AppUserRole.driver:
        case AppUserRole.guardian:
          return DeepLinkAllowed(target);
        case AppUserRole.companyAdmin:
        case AppUserRole.unknown:
          return DeepLinkRoleMismatch(target: target, userRole: userRole);
      }
    }

    return DeepLinkUnsupported(target);
  }
}
