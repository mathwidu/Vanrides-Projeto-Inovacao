import 'dart:convert';

import 'package:stomp_dart_client/stomp.dart';
import 'package:stomp_dart_client/stomp_config.dart';

import '../../../services/websocket_service.dart';
import '../domain/trip_route_event.dart';

typedef TripRouteEventHandler = void Function(TripRouteEvent event);

abstract class TripRouteEventGateway {
  bool get isConnected;

  void connect({
    required String authorizationHeader,
    required int companyId,
    int? tripId,
    required TripRouteEventHandler onEvent,
  });

  void disconnect();
  void dispose();
}

class TripRouteEventService implements TripRouteEventGateway {
  TripRouteEventService({required String apiBaseUrl})
    : _webSocketUrl = buildWebSocketUrlForTest(apiBaseUrl);

  final String _webSocketUrl;
  StompClient? _client;
  bool _connected = false;

  @override
  bool get isConnected => _connected;

  @override
  void connect({
    required String authorizationHeader,
    required int companyId,
    int? tripId,
    required TripRouteEventHandler onEvent,
  }) {
    disconnect();

    final destination =
        tripId == null
            ? '/topic/viagens/$companyId'
            : '/topic/viagens/$companyId/$tripId';
    _client = StompClient(
      config: StompConfig.SockJS(
        url: _webSocketUrl,
        stompConnectHeaders: {'Authorization': authorizationHeader},
        webSocketConnectHeaders: {'Authorization': authorizationHeader},
        onConnect: (_) {
          _connected = true;
          _client?.subscribe(
            destination: destination,
            callback: (frame) {
              final body = frame.body;
              if (body == null || body.isEmpty) {
                return;
              }

              try {
                final decoded = jsonDecode(body);
                if (decoded is Map<String, dynamic>) {
                  onEvent(TripRouteEvent.fromJson(decoded));
                } else if (decoded is Map) {
                  onEvent(
                    TripRouteEvent.fromJson(
                      decoded.map((key, value) => MapEntry('$key', value)),
                    ),
                  );
                }
              } catch (_) {
                // payload inesperado: ignora para nao derrubar o fluxo principal
              }
            },
          );
        },
        onStompError: (_) {
          _connected = false;
        },
        onWebSocketError: (_) {
          _connected = false;
        },
        onDisconnect: (_) {
          _connected = false;
        },
      ),
    );

    _client?.activate();
  }

  @override
  void disconnect() {
    _connected = false;
    _client?.deactivate();
    _client = null;
  }

  @override
  void dispose() {
    disconnect();
  }
}
