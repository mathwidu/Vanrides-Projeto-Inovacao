import '../domain/deep_link_models.dart';

class DeepLinkParser {
  static const String scheme = 'vanrides';

  DeepLinkTarget parse(Uri uri) {
    if (uri.scheme != scheme) {
      return UnknownDeepLink(rawUri: uri.toString());
    }

    final segments = [
      uri.host,
      ...uri.pathSegments,
    ].where((segment) => segment.isNotEmpty).toList(growable: false);

    if (segments.isEmpty) {
      return UnknownDeepLink(rawUri: uri.toString());
    }

    switch (segments.first) {
      case 'viagem':
        if (segments.length < 2) {
          break;
        }
        final id = int.tryParse(segments[1]);
        if (id == null) {
          break;
        }
        return ViagemDeepLink(tripId: id);
      case 'rastreio':
        if (segments.length < 2) {
          break;
        }
        final id = int.tryParse(segments[1]);
        if (id == null) {
          break;
        }
        return RastreioDeepLink(passageiroId: id);
      case 'convite':
        if (segments.length < 2) {
          break;
        }
        if (segments[1].isEmpty) {
          break;
        }
        return ConviteDeepLink(codigo: segments[1]);
      case 'driver-invite':
        if (segments.length < 2) {
          break;
        }
        if (segments[1].isEmpty) {
          break;
        }
        return DriverInviteDeepLink(token: segments[1]);
    }

    return UnknownDeepLink(rawUri: uri.toString());
  }

  DeepLinkTarget parseString(String url) {
    final uri = Uri.tryParse(url);
    if (uri == null) {
      return UnknownDeepLink(rawUri: url);
    }
    return parse(uri);
  }
}
