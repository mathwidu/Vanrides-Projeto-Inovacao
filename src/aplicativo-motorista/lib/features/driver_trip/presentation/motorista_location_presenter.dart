import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:geolocator/geolocator.dart';

import '../../tracking/domain/trip_live_location_record.dart';
import '../../../services/location_sharing_use_case.dart';
import '../../../services/websocket_service.dart';

/// Presenter que centraliza GPS + WebSocket de localização do motorista.
/// Sem referências a widgets, navegação ou mensagens visuais.
class MotoristaLocationPresenter extends ChangeNotifier {
  MotoristaLocationPresenter({
    required WebSocketServiceBase webSocketService,
    required LocationSharingUseCase locationSharingUseCase,
    required String authorizationHeader,
  }) : _webSocketService = webSocketService,
       _locationSharingUseCase = locationSharingUseCase,
       _authorizationHeader = authorizationHeader;

  final WebSocketServiceBase _webSocketService;
  final LocationSharingUseCase _locationSharingUseCase;
  final String _authorizationHeader;

  StreamSubscription<Position>? _locationSubscription;
  bool _isSharing = false;
  bool _hasPermission = false;
  String? _locationError;
  TripLiveLocationRecord? _latestLocation;
  LocationSharingPermissionResult? _lastPermissionResult;
  bool _isDisposed = false;

  /// Pré: chamar antes de `dispose()`.
  /// Efeito: nenhum.
  /// Pós: retorna `true` quando o compartilhamento de localização está ativo.
  bool get isSharing => _isSharing;

  /// Pré: chamar antes de `dispose()`.
  /// Efeito: nenhum.
  /// Pós: retorna `true` quando a última checagem de permissão foi concedida.
  bool get hasPermission => _hasPermission;

  /// Pré: chamar antes de `dispose()`.
  /// Efeito: nenhum.
  /// Pós: retorna a última mensagem de erro de localização (ou `null` se não há erro).
  String? get locationError => _locationError;

  /// Pré: chamar antes de `dispose()`.
  /// Efeito: nenhum.
  /// Pós: retorna a última posição publicada (ou `null` se ainda não houve publicação).
  TripLiveLocationRecord? get latestLocation => _latestLocation;

  /// Pré: chamar antes de `dispose()`.
  /// Efeito: nenhum.
  /// Pós: retorna o último resultado de permissão avaliado (ou `null` se nunca checou).
  LocationSharingPermissionResult? get lastPermissionResult =>
      _lastPermissionResult;

  /// Pré: `motoristaId`/`companyId` válidos; chamar antes de `dispose()`.
  /// Efeito: garante permissões, conecta WS, obtém posição atual, inicia stream e publica posições.
  /// Pós: retorna `true` com `_isSharing=true` quando ativo; em falha define `_locationError` e notifica.
  Future<bool> startSharing(int motoristaId, int companyId) async {
    if (_isSharing) {
      return true;
    }

    final permissionResult = await _locationSharingUseCase.ensurePermissions();
    _lastPermissionResult = permissionResult;
    if (permissionResult is! LocationSharingPermissionGranted) {
      _hasPermission = false;
      _locationError = _messageForPermissionResult(permissionResult);
      _notifySafely();
      return false;
    }

    _hasPermission = true;
    _locationError = null;
    _webSocketService.connect(
      authorizationHeader: _authorizationHeader,
      companyId: companyId,
    );

    final currentPosition =
        await _locationSharingUseCase.gateway.getCurrentPosition();
    _publishPosition(currentPosition, motoristaId, companyId);

    await _locationSubscription?.cancel();
    _locationSubscription = _locationSharingUseCase.gateway
        .getPositionStream(allowBackground: permissionResult.allowBackground)
        .listen(
          (position) => _publishPosition(position, motoristaId, companyId),
          onError: (_) {
            _isSharing = false;
            _locationError =
                'Nao foi possivel continuar o sinal ao vivo desta corrida.';
            _webSocketService.disconnect();
            _notifySafely();
          },
        );

    _isSharing = true;
    _notifySafely();
    return true;
  }

  /// Pré: nenhuma (idempotente); chamar antes de `dispose()`.
  /// Efeito: cancela a stream de posição e desconecta o WebSocket.
  /// Pós: `_isSharing=false` e `notifyListeners()` executado.
  Future<void> stopSharing() async {
    await _locationSubscription?.cancel();
    _locationSubscription = null;
    _webSocketService.disconnect();
    _isSharing = false;
    _notifySafely();
  }

  void _publishPosition(Position position, int motoristaId, int companyId) {
    _latestLocation = TripLiveLocationRecord(
      latitude: position.latitude,
      longitude: position.longitude,
    );
    _locationError = null;
    _webSocketService.sendLocation(
      position.latitude,
      position.longitude,
      motoristaId,
      companyId: companyId,
    );
    _notifySafely();
  }

  String _messageForPermissionResult(LocationSharingPermissionResult result) {
    return switch (result) {
      LocationSharingServiceDisabled() =>
        'Ative a localizacao para iniciar o sinal ao vivo.',
      LocationSharingPermissionDenied() =>
        'Permissao de localizacao negada. O motorista nao aparecera no mapa ao vivo.',
      LocationSharingPermissionDeniedForever() =>
        'Permissao de localizacao bloqueada. Abra os ajustes para ligar o sinal ao vivo.',
      LocationSharingPermissionUnableToDetermine() =>
        'Nao foi possivel determinar a permissao de localizacao.',
      _ => 'Nao foi possivel iniciar o sinal ao vivo.',
    };
  }

  void _notifySafely() {
    if (_isDisposed) {
      return;
    }
    notifyListeners();
  }

  /// Pré: deve ser a última chamada; não usar o presenter após `dispose()`.
  /// Efeito: marca disposed, cancela stream ativa e libera recursos do WebSocket.
  /// Pós: nenhum `notifyListeners()` futuro será emitido por este presenter.
  @override
  void dispose() {
    _isDisposed = true;
    _locationSubscription?.cancel();
    _webSocketService.dispose();
    super.dispose();
  }
}
