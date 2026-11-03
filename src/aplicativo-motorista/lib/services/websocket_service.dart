import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:stomp_dart_client/stomp.dart';
import 'package:stomp_dart_client/stomp_config.dart';

import 'api_constants.dart';
import 'jwt_claims.dart' as jwt_claims;

typedef LocationMessageHandler = void Function(Map<String, dynamic> data);

abstract class WebSocketServiceBase {
  bool get isConnected;
  LocationMessageHandler? get onLocationReceived;
  set onLocationReceived(LocationMessageHandler? handler);

  void connect({
    required String authorizationHeader,
    required int companyId,
    String? topicSuffix,
  });

  void sendLocation(
    double latitude,
    double longitude,
    int motoristaId, {
    int? companyId,
  });

  void disconnect();
  void dispose();
}

class WebSocketService implements WebSocketServiceBase {
  StompClient? stompClient;
  final _controller = StreamController<Map<String, dynamic>>.broadcast();
  bool _connected = false;
  LocationMessageHandler? _onLocationReceived;

  Stream<Map<String, dynamic>> get stream => _controller.stream;

  @override
  bool get isConnected => _connected;

  @override
  LocationMessageHandler? get onLocationReceived => _onLocationReceived;

  @override
  set onLocationReceived(LocationMessageHandler? handler) {
    _onLocationReceived = handler;
  }

  @override
  void connect({
    required String authorizationHeader,
    required int companyId,
    String? topicSuffix,
  }) {
    disconnect();

    final websocketUrl = _buildWebSocketUrl(baseUrl);
    final destination =
        '/topic/localizacao/${topicSuffix ?? companyId.toString()}';

    stompClient = StompClient(
      config: StompConfig.SockJS(
        url: websocketUrl,
        stompConnectHeaders: {'Authorization': authorizationHeader},
        webSocketConnectHeaders: {'Authorization': authorizationHeader},
        onConnect: (frame) {
          _connected = true;
          stompClient?.subscribe(
            destination: destination,
            callback: (frame) {
              final body = frame.body;
              if (body == null || body.isEmpty) {
                return;
              }
              try {
                final decoded = jsonDecode(body);
                final payload =
                    decoded is Map<String, dynamic>
                        ? decoded
                        : decoded is Map
                        ? decoded.map(
                          (key, value) => MapEntry(key.toString(), value),
                        )
                        : null;
                if (payload != null) {
                  _controller.add(payload);
                  _onLocationReceived?.call(payload);
                }
              } catch (error) {
                debugPrint(
                  '[DEBUG FLUTTER] ❌ Erro ao decodificar payload WS: $error',
                );
              }
            },
          );
        },
        onStompError: (frame) {
          _connected = false;
          debugPrint('[DEBUG FLUTTER] ❌ Erro STOMP: ${frame.body}');
        },
        onWebSocketError: (dynamic error) {
          _connected = false;
          debugPrint('[DEBUG FLUTTER] ❌ Erro WebSocket: $error');
        },
        onDisconnect: (_) {
          _connected = false;
        },
      ),
    );

    stompClient?.activate();
  }

  @override
  void sendLocation(
    double latitude,
    double longitude,
    int motoristaId, {
    int? companyId,
  }) {
    if (!_connected || stompClient == null) {
      debugPrint(
        '[DEBUG FLUTTER] ⚠️ Tentativa de enviar localização sem conexão STOMP ativa.',
      );
      return;
    }

    final payload = jsonEncode({
      'motoristaId': motoristaId,
      'latitude': latitude,
      'longitude': longitude,
    });

    stompClient!.send(destination: '/app/enviarLocalizacao', body: payload);
  }

  @override
  void disconnect() {
    _connected = false;
    stompClient?.deactivate();
    stompClient = null;
  }

  @override
  void dispose() {
    disconnect();
    _controller.close();
  }
}

String _buildWebSocketUrl(String apiBaseUrl) {
  final uri = Uri.parse(apiBaseUrl);
  final path =
      uri.path == '/' || uri.path.isEmpty
          ? '/ws-location'
          : '${uri.path}/ws-location';
  final port = uri.hasPort ? uri.port : (uri.scheme == 'https' ? 443 : 80);
  final host =
      uri.host.contains(':') && !uri.host.startsWith('[')
          ? '[${uri.host}]'
          : uri.host;
  final query = uri.hasQuery ? '?${uri.query}' : '';

  // SockJS reparses this URL and reads uri.port directly. Keeping the port
  // explicit avoids it falling back to `0` for default http/https ports.
  return '${uri.scheme}://$host:$port$path$query';
}

String buildWebSocketUrlForTest(String apiBaseUrl) =>
    _buildWebSocketUrl(apiBaseUrl);

int? extractCompanyIdFromAccessToken(String? accessToken) {
  return jwt_claims.extractCompanyIdFromAccessToken(accessToken);
}
