import 'package:url_launcher/url_launcher_string.dart';

abstract class TripNavigationLauncher {
  Future<bool> open(String url);
}

class ExternalTripNavigationLauncher implements TripNavigationLauncher {
  const ExternalTripNavigationLauncher();

  @override
  Future<bool> open(String url) async {
    final trimmed = url.trim();
    if (trimmed.isEmpty) {
      return false;
    }
    return launchUrlString(trimmed, mode: LaunchMode.externalApplication);
  }
}
