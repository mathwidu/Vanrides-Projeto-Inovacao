import 'package:vanrides_frontend_dev/services/websocket_service.dart';

class FakeWebSocketService implements WebSocketServiceBase {
  int connectCalls = 0;
  int disconnectCalls = 0;
  String? lastAuthorizationHeader;
  int? lastCompanyId;

  final List<Map<String, Object>> sentLocations = <Map<String, Object>>[];

  @override
  bool get isConnected => true;

  @override
  LocationMessageHandler? onLocationReceived;

  @override
  void connect({
    required String authorizationHeader,
    required int companyId,
    String? topicSuffix,
  }) {
    connectCalls += 1;
    lastAuthorizationHeader = authorizationHeader;
    lastCompanyId = companyId;
  }

  @override
  void disconnect() {
    disconnectCalls += 1;
  }

  @override
  void dispose() {
    disconnect();
  }

  @override
  void sendLocation(
    double latitude,
    double longitude,
    int motoristaId, {
    int? companyId,
  }) {
    sentLocations.add(<String, Object>{
      'latitude': latitude,
      'longitude': longitude,
      'motoristaId': motoristaId,
      if (companyId != null) 'companyId': companyId,
    });
  }
}
