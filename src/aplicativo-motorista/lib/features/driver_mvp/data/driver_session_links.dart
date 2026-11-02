import 'package:app_links/app_links.dart';
import 'package:flutter/services.dart';
import 'package:url_launcher/url_launcher.dart';

abstract interface class DriverSessionLinks {
  Future<Uri?> initial();
  Future<bool> isLauncherVisit();
  Stream<Uri> get incoming;
  bool acceptsReturn(Uri uri);
  bool get hasConversation;
  Future<bool> openConversation();
}

class DeviceSessionLinks implements DriverSessionLinks {
  DeviceSessionLinks({
    this.number = const String.fromEnvironment('VANRIDES_WHATSAPP_BOT_NUMBER'),
    this.returnHost = const String.fromEnvironment(
      'VANRIDES_PAIRING_LINK_HOST',
    ),
  });

  final String number;
  final String returnHost;
  final AppLinks _links = AppLinks();

  @override
  Future<Uri?> initial() => _links.getInitialLink();

  @override
  Future<bool> isLauncherVisit() async {
    const channel = MethodChannel(
      'dev.matheusduarte.vanrides.driver/launch.v1',
    );
    try {
      return await channel.invokeMethod<bool>('isLauncherVisit') == true;
    } on PlatformException {
      return false;
    } on MissingPluginException {
      return false;
    }
  }

  @override
  Stream<Uri> get incoming => _links.uriLinkStream;

  @override
  bool acceptsReturn(Uri uri) => isReturn(uri, returnHost);

  static bool isReturn(Uri uri, String host) {
    if (uri.toString() == 'vanridesdriver://pairing/return') return true;
    if (!RegExp(r'^[a-z0-9-]+(\.[a-z0-9-]+)+$').hasMatch(host)) return false;
    return uri.toString() ==
        Uri.https(host, '/motorista/vinculo/retorno').toString();
  }

  @override
  bool get hasConversation => RegExp(r'^[1-9][0-9]{7,14}$').hasMatch(number);

  @override
  Future<bool> openConversation() async {
    if (!hasConversation) return false;
    return launchUrl(
      Uri.https('wa.me', '/$number'),
      mode: LaunchMode.externalApplication,
    );
  }
}
