import 'dart:async';

import 'package:flutter/foundation.dart';

import '../../tracking/data/trip_route_event_service.dart';
import '../../tracking/domain/trip_route_event.dart';
import '../../tracking/domain/trip_tracking_exception.dart';
import '../../tracking/domain/trip_tracking_record.dart';
import '../data/driver_trip_service.dart';
import '../domain/driver_trip_record.dart';

enum MotoristaSessionStatus { loading, loaded, empty, error }

class MotoristaSessionState {
  const MotoristaSessionState({
    required this.status,
    required this.errorMessage,
    required this.trip,
    required this.dayTrips,
    required this.editableStops,
  });

  final MotoristaSessionStatus status;
  final String? errorMessage;
  final DriverTripRecord? trip;
  final List<DriverTripRecord> dayTrips;
  final List<DriverTripStopRecord> editableStops;
}

/// Presenter que centraliza o estado de sessão do motorista:
/// viagem ativa, fila editável e estado de carregamento/erro.
/// Sem referência a widgets, navegação, permissões ou WebSocket.
class MotoristaSessionPresenter extends ChangeNotifier {
  MotoristaSessionPresenter({required DriverTripGateway tripGateway})
    : _tripGateway = tripGateway;

  final DriverTripGateway _tripGateway;

  bool _loading = true;
  String? _errorMessage;
  DriverTripRecord? _trip;
  List<DriverTripRecord> _dayTrips = const [];
  List<DriverTripStopRecord> _editableStops = const [];
  int? _motoristaId;
  TripRouteEventGateway? _routeEventGateway;
  int? _routeCompanyId;
  String? _routeAuthorizationHeader;
  bool _isDisposed = false;

  /// Pré: chamar antes de `dispose()`.
  /// Efeito: nenhum.
  /// Pós: retorna `true` enquanto a viagem do dia está sendo carregada.
  bool get loading => _loading;

  /// Pré: chamar antes de `dispose()`.
  /// Efeito: nenhum.
  /// Pós: retorna a última mensagem de erro (ou `null` se não há erro).
  String? get errorMessage => _errorMessage;

  /// Pré: chamar antes de `dispose()`.
  /// Efeito: nenhum.
  /// Pós: retorna o snapshot atual da viagem (ou `null` se ainda não carregou / não existe).
  DriverTripRecord? get trip => _trip;

  /// Pré: chamar antes de `dispose()`.
  /// Efeito: nenhum.
  /// Pós: retorna todas as viagens operacionais do motorista no dia carregado.
  List<DriverTripRecord> get dayTrips => _dayTrips;

  /// Pré: chamar antes de `dispose()`.
  /// Efeito: nenhum.
  /// Pós: retorna a lista atual de paradas editáveis (snapshot ou cópia em edição).
  List<DriverTripStopRecord> get editableStops => _editableStops;

  MotoristaSessionState get state => MotoristaSessionState(
    status: _status,
    errorMessage: _errorMessage,
    trip: _trip,
    dayTrips: List.unmodifiable(_dayTrips),
    editableStops: List.unmodifiable(_editableStops),
  );

  MotoristaSessionStatus get _status {
    if (_loading) {
      return MotoristaSessionStatus.loading;
    }
    if (_errorMessage != null) {
      return MotoristaSessionStatus.error;
    }
    if (_trip == null) {
      return MotoristaSessionStatus.empty;
    }
    return MotoristaSessionStatus.loaded;
  }

  /// Pré: `motoristaId` válido; chamar antes de `dispose()`.
  /// Efeito: marca `_loading=true`, limpa erro e busca a agenda do dia via gateway.
  /// Pós: em sucesso chama `syncTripSnapshot(...)` e notifica; em falha seta `_errorMessage` e notifica.
  Future<DriverTripRecord?> loadTrip(
    int motoristaId, {
    int? preferredTripId,
  }) async {
    _motoristaId = motoristaId;
    _loading = true;
    _errorMessage = null;
    _notifySafely();

    try {
      final trips = await _tripGateway.fetchDayTrips(motoristaId);
      _dayTrips = trips;
      final trip = _selectRelevantTrip(
        trips,
        preferredTripId: preferredTripId ?? _trip?.id,
      );
      syncTripSnapshot(trip);
      return trip;
    } catch (error) {
      if (_isDisposed) {
        return null;
      }
      _loading = false;
      _errorMessage =
          error is TripTrackingException
              ? error.message
              : 'Nao foi possivel carregar a operacao do motorista.';
      notifyListeners();
      return null;
    }
  }

  /// Pré: `motoristaId` válido; chamar antes de `dispose()`.
  /// Efeito: busca a agenda do dia e preserva a viagem selecionada quando possível.
  /// Pós: retorna o snapshot selecionado (ou `null`) e notifica quando sincroniza.
  Future<DriverTripRecord?> refreshTripSilently(
    int motoristaId, {
    int? preferredTripId,
  }) async {
    _motoristaId = motoristaId;
    try {
      final trips = await _tripGateway.fetchDayTrips(motoristaId);
      final trip = _selectRelevantTrip(
        trips,
        preferredTripId: preferredTripId ?? _trip?.id,
      );
      if (trips.isNotEmpty || _dayTrips.isNotEmpty || trip != null) {
        _dayTrips = trips;
        syncTripSnapshot(trip);
      }
      return trip;
    } catch (_) {
      return null;
    }
  }

  /// Pré: chamar antes de `dispose()`; `trip` pode ser `null` (sem viagem hoje).
  /// Efeito: substitui `trip` e `editableStops` e reseta flags de loading/erro.
  /// Pós: `_loading=false`, `_errorMessage=null` e `notifyListeners()` executado.
  void syncTripSnapshot(DriverTripRecord? trip) {
    if (_isDisposed) {
      return;
    }
    if (trip != null) {
      _dayTrips = _replaceTripInDayTrips(trip);
    } else if (_dayTrips.isEmpty) {
      _dayTrips = const [];
    }
    _trip = trip;
    _editableStops = trip?.paradas.toList(growable: false) ?? const [];
    _loading = false;
    _errorMessage = null;
    notifyListeners();
  }

  /// Pré: `trip` veio de `dayTrips` ou de uma atualização operacional.
  /// Efeito: troca a viagem em foco sem recarregar a agenda.
  /// Pós: `trip` e `editableStops` passam a refletir a escolha do motorista.
  void selectTrip(DriverTripRecord trip) {
    syncTripSnapshot(trip);
  }

  /// Pré: `trip` já carregada (não-`null`) e presenter não disposed.
  /// Efeito: aplica o evento incremental na viagem via `copyWithRealtime(...)`.
  /// Pós: `trip` atualizado e `notifyListeners()` executado.
  void applyRealtimeEvent(TripRouteEvent event) {
    final trip = _trip;
    if (_isDisposed || trip == null) {
      return;
    }
    _trip = trip.copyWithRealtime(event);
    notifyListeners();
  }

  /// Pré: `trip` carregada (existe snapshot de paradas) e presenter não disposed.
  /// Efeito: cria uma cópia de `trip.paradas` em `editableStops` para edição local.
  /// Pós: `editableStops` passa a refletir a cópia e `notifyListeners()` executado.
  void startRouteEditing() {
    final trip = _trip;
    if (_isDisposed || trip == null) {
      return;
    }
    _editableStops = List<DriverTripStopRecord>.from(trip.paradas);
    notifyListeners();
  }

  /// Pré: chamar antes de `dispose()`; normalmente após `startRouteEditing()`.
  /// Efeito: restaura `editableStops` a partir do snapshot atual de `trip.paradas`.
  /// Pós: `editableStops` sincronizado com `trip` e `notifyListeners()` executado.
  void resetEditableStopsFromTrip() {
    if (_isDisposed) {
      return;
    }
    _editableStops = _trip?.paradas.toList(growable: false) ?? const [];
    notifyListeners();
  }

  /// Pré: `editableStops` inicializado e índices dentro do range; chamar antes de `dispose()`.
  /// Efeito: reordena a lista local de paradas (não persiste no backend).
  /// Pós: `editableStops` atualizado e `notifyListeners()` executado.
  void reorderEditableStops(int oldIndex, int newIndex) {
    if (_isDisposed) {
      return;
    }
    if (newIndex > oldIndex) {
      newIndex -= 1;
    }
    final items = List<DriverTripStopRecord>.from(_editableStops);
    final moved = items.removeAt(oldIndex);
    items.insert(newIndex, moved);
    _editableStops = items;
    notifyListeners();
  }

  /// Pré: `trip` carregada (`tripId != null`) e `authorizationHeader`/`companyId` válidos.
  /// Efeito: desconecta conexão anterior (se houver) e conecta o gateway de eventos de rota.
  /// Pós: em sucesso, eventos passam a atualizar `trip` (ou forçar refresh) sem intervenção do caller.
  void connectRouteEvents(
    TripRouteEventGateway gateway,
    int companyId,
    String authorizationHeader,
  ) {
    disconnectRouteEvents();

    final tripId = _trip?.id;
    if (_isDisposed || tripId == null) {
      return;
    }

    _routeEventGateway = gateway;
    _routeCompanyId = companyId;
    _routeAuthorizationHeader = authorizationHeader;
    gateway.connect(
      authorizationHeader: authorizationHeader,
      companyId: companyId,
      tripId: tripId,
      onEvent: (event) {
        if (_isDisposed || event.viagemId != tripId) {
          return;
        }
        if (event.rotaVersao > (_trip?.rotaVersao ?? 0) ||
            event.isRouteUpdated) {
          final motoristaId = _motoristaId;
          if (motoristaId != null) {
            unawaited(_refreshTripFromRouteEvent(motoristaId, tripId));
          }
          return;
        }
        applyRealtimeEvent(event);
      },
    );
  }

  /// Pré: nenhuma (idempotente).
  /// Efeito: desconecta o gateway de eventos de rota atual, se existir.
  /// Pós: conexão fica inativa e `_routeEventGateway` é limpo (sem `notifyListeners()`).
  void disconnectRouteEvents() {
    _routeEventGateway?.disconnect();
    _routeEventGateway = null;
  }

  Future<void> _refreshTripFromRouteEvent(
    int motoristaId,
    int previousTripId,
  ) async {
    final trip = await refreshTripSilently(motoristaId);
    if (_isDisposed || trip == null) {
      return;
    }
    if (trip.id != previousTripId) {
      final gateway = _routeEventGateway;
      final companyId = _routeCompanyId;
      final authorizationHeader = _routeAuthorizationHeader;
      if (gateway != null && companyId != null && authorizationHeader != null) {
        connectRouteEvents(gateway, companyId, authorizationHeader);
      }
    }
  }

  DriverTripRecord? _selectRelevantTrip(
    List<DriverTripRecord> trips, {
    int? preferredTripId,
  }) {
    if (trips.isEmpty) {
      return null;
    }

    if (preferredTripId != null) {
      for (final trip in trips) {
        if (trip.id == preferredTripId) {
          return trip;
        }
      }
    }

    for (final trip in trips) {
      if (trip.status == TripTrackingStatus.inProgress) {
        return trip;
      }
    }

    for (final trip in trips) {
      if (trip.status == TripTrackingStatus.planned) {
        return trip;
      }
    }

    for (final trip in trips.reversed) {
      if (trip.status == TripTrackingStatus.completed) {
        return trip;
      }
    }

    for (final trip in trips) {
      if (trip.status != TripTrackingStatus.canceled) {
        return trip;
      }
    }

    return trips.first;
  }

  List<DriverTripRecord> _replaceTripInDayTrips(DriverTripRecord updatedTrip) {
    if (_dayTrips.isEmpty) {
      return [updatedTrip];
    }

    var replaced = false;
    final trips = _dayTrips
        .map((trip) {
          if (trip.id == updatedTrip.id) {
            replaced = true;
            return updatedTrip;
          }
          return trip;
        })
        .toList(growable: true);

    if (!replaced) {
      trips.add(updatedTrip);
    }
    return List.unmodifiable(trips);
  }

  void _notifySafely() {
    if (_isDisposed) {
      return;
    }
    notifyListeners();
  }

  /// Pré: deve ser a última chamada; não usar o presenter após `dispose()`.
  /// Efeito: marca disposed e garante `disconnectRouteEvents()` antes do `super.dispose()`.
  /// Pós: recursos liberados e nenhum `notifyListeners()` futuro será emitido por este presenter.
  @override
  void dispose() {
    _isDisposed = true;
    disconnectRouteEvents();
    super.dispose();
  }
}
