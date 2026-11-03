sealed class DeepLinkTarget {
  const DeepLinkTarget();
}

class ViagemDeepLink extends DeepLinkTarget {
  const ViagemDeepLink({required this.tripId});

  final int tripId;
}

class RastreioDeepLink extends DeepLinkTarget {
  const RastreioDeepLink({required this.passageiroId});

  final int passageiroId;
}

class ConviteDeepLink extends DeepLinkTarget {
  const ConviteDeepLink({required this.codigo});

  final String codigo;
}

class DriverInviteDeepLink extends DeepLinkTarget {
  const DriverInviteDeepLink({required this.token});

  final String token;
}

class UnknownDeepLink extends DeepLinkTarget {
  const UnknownDeepLink({required this.rawUri});

  final String rawUri;
}
