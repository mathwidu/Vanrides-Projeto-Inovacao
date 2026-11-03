import 'package:vanrides_frontend_dev/features/tracking/data/trip_route_event_service.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_route_event.dart';

class FakeTripRouteEventGateway implements TripRouteEventGateway {
  TripRouteEventHandler? _handler;
  int connectCalls = 0;
  int disconnectCalls = 0;
  String? lastAuthorizationHeader;
  int? lastCompanyId;
  int? lastTripId;

  @override
  bool get isConnected => _handler != null;

  @override
  void connect({
    required String authorizationHeader,
    required int companyId,
    int? tripId,
    required TripRouteEventHandler onEvent,
  }) {
    connectCalls += 1;
    lastAuthorizationHeader = authorizationHeader;
    lastCompanyId = companyId;
    lastTripId = tripId;
    _handler = onEvent;
  }

  @override
  void disconnect() {
    disconnectCalls += 1;
    _handler = null;
  }

  @override
  void dispose() {
    disconnect();
  }

  void emit(TripRouteEvent event) {
    _handler?.call(event);
  }
}
