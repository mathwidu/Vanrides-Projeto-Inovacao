import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/data/driver_trip_service.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/domain/driver_trip_record.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/presentation/motorista_session_presenter.dart';
import 'package:vanrides_frontend_dev/features/tracking/data/trip_route_event_service.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_route_event.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_record.dart';

class _FakeDriverTripGateway implements DriverTripGateway {
  const _FakeDriverTripGateway({this.trip, this.dayTrips, this.failure});

  final DriverTripRecord? trip;
  final List<DriverTripRecord>? dayTrips;
  final Object? failure;

  @override
  Future<DriverTripRecord?> fetchTodayTrip(int motoristaId) async => trip;

  @override
  Future<List<DriverTripRecord>> fetchDayTrips(int motoristaId) async {
    final error = failure;
    if (error != null) {
      throw error;
    }
    final trips = dayTrips;
    if (trips != null) {
      return trips;
    }
    final currentTrip = trip;
    return currentTrip == null ? const [] : [currentTrip];
  }

  @override
  Future<DriverTripRecord> reorderTrip(int tripId, List<int> stopIds) {
    throw UnimplementedError();
  }

  @override
  Future<DriverTripRecord> startTrip(int tripId) {
    throw UnimplementedError();
  }

  @override
  Future<DriverTripRecord> completeStop(int tripId, int stopId) {
    throw UnimplementedError();
  }

  @override
  Future<DriverTripRecord> skipStop(int tripId, int stopId) {
    throw UnimplementedError();
  }

  @override
  Future<DriverTripRecord> noShowStop(int tripId, int stopId) {
    throw UnimplementedError();
  }

  @override
  Future<DriverTripRecord> endTrip(int tripId) {
    throw UnimplementedError();
  }
}

class _FakeTripRouteEventGateway implements TripRouteEventGateway {
  TripRouteEventHandler? _onEvent;

  @override
  bool get isConnected => _onEvent != null;

  @override
  void connect({
    required String authorizationHeader,
    required int companyId,
    int? tripId,
    required TripRouteEventHandler onEvent,
  }) {
    _onEvent = onEvent;
  }

  @override
  void disconnect() {
    _onEvent = null;
  }

  @override
  void dispose() {
    disconnect();
  }

  void emit(TripRouteEvent event) {
    _onEvent?.call(event);
  }
}

DriverTripRecord _sampleTrip() {
  return DriverTripRecord(
    id: 11,
    rotaBaseId: 4,
    rotaNome: 'Rota Centro',
    dataOperacao: DateTime(2026, 4, 21),
    status: TripTrackingStatus.planned,
    rotaVersao: 2,
    etaMinutosAteProximaParada: 12,
    etaAtualizadoEm: DateTime(2026, 4, 21, 7, 30),
    motorista: const TripTrackingDriverRecord(id: 7, nome: 'Motorista QA'),
    proximaParada: const DriverTripStopRecord(
      id: 80,
      ordem: 1,
      status: TripTrackingStopStatus.pending,
      etaMinutos: 12,
      confirmadoPresenca: false,
      proximaParada: true,
      passageiroId: 30,
      passageiroNome: 'Ana',
      menorDeIdade: false,
    ),
    paradas: const [
      DriverTripStopRecord(
        id: 80,
        ordem: 1,
        status: TripTrackingStopStatus.pending,
        etaMinutos: 12,
        confirmadoPresenca: false,
        proximaParada: true,
        passageiroId: 30,
        passageiroNome: 'Ana',
        menorDeIdade: false,
      ),
      DriverTripStopRecord(
        id: 81,
        ordem: 2,
        status: TripTrackingStopStatus.pending,
        etaMinutos: 20,
        confirmadoPresenca: false,
        proximaParada: false,
        passageiroId: 31,
        passageiroNome: 'Bruno',
        menorDeIdade: true,
      ),
    ],
    rotaGeografica: null,
  );
}

extension _DriverTripRecordCopyForTest on DriverTripRecord {
  DriverTripRecord copyWithForTest({
    int? id,
    TripTrackingStatus? status,
    String? horarioPartida,
  }) {
    return DriverTripRecord(
      id: id ?? this.id,
      rotaBaseId: rotaBaseId,
      rotaNome: rotaNome,
      dataOperacao: dataOperacao,
      status: status ?? this.status,
      rotaVersao: rotaVersao,
      etaMinutosAteProximaParada: etaMinutosAteProximaParada,
      etaAtualizadoEm: etaAtualizadoEm,
      motorista: motorista,
      proximaParada: proximaParada,
      paradas: paradas,
      rotaGeografica: rotaGeografica,
      horarioPartida: horarioPartida ?? this.horarioPartida,
      servicoId: servicoId,
      servicoNome: servicoNome,
      tipoOperacao: tipoOperacao,
    );
  }
}

void main() {
  test(
    'state expõe loading, loaded, empty e error para a fronteira da UI',
    () async {
      final loadingPresenter = MotoristaSessionPresenter(
        tripGateway: const _FakeDriverTripGateway(),
      );

      expect(loadingPresenter.state.status, MotoristaSessionStatus.loading);
      expect(loadingPresenter.state.trip, isNull);
      expect(loadingPresenter.state.dayTrips, isEmpty);
      expect(loadingPresenter.state.errorMessage, isNull);

      final loadedPresenter = MotoristaSessionPresenter(
        tripGateway: _FakeDriverTripGateway(trip: _sampleTrip()),
      );
      await loadedPresenter.loadTrip(7);

      expect(loadedPresenter.state.status, MotoristaSessionStatus.loaded);
      expect(loadedPresenter.state.trip?.id, 11);
      expect(loadedPresenter.state.dayTrips.map((trip) => trip.id), [11]);
      expect(loadedPresenter.state.editableStops.length, 2);

      final emptyPresenter = MotoristaSessionPresenter(
        tripGateway: const _FakeDriverTripGateway(dayTrips: []),
      );
      await emptyPresenter.loadTrip(7);

      expect(emptyPresenter.state.status, MotoristaSessionStatus.empty);
      expect(emptyPresenter.state.trip, isNull);
      expect(emptyPresenter.state.dayTrips, isEmpty);

      final errorPresenter = MotoristaSessionPresenter(
        tripGateway: _FakeDriverTripGateway(failure: StateError('offline')),
      );
      await errorPresenter.loadTrip(7);

      expect(errorPresenter.state.status, MotoristaSessionStatus.error);
      expect(
        errorPresenter.state.errorMessage,
        'Nao foi possivel carregar a operacao do motorista.',
      );
    },
  );

  test('estado inicial começa vazio com loading ativo', () {
    final presenter = MotoristaSessionPresenter(
      tripGateway: const _FakeDriverTripGateway(),
    );

    expect(presenter.loading, isTrue);
    expect(presenter.errorMessage, isNull);
    expect(presenter.trip, isNull);
    expect(presenter.editableStops, isEmpty);
  });

  test('loadTrip atualiza o estado e notifica listeners', () async {
    final presenter = MotoristaSessionPresenter(
      tripGateway: _FakeDriverTripGateway(trip: _sampleTrip()),
    );
    var notificationCount = 0;
    presenter.addListener(() => notificationCount++);

    await presenter.loadTrip(7);

    expect(presenter.loading, isFalse);
    expect(presenter.errorMessage, isNull);
    expect(presenter.trip?.id, 11);
    expect(presenter.dayTrips.map((trip) => trip.id), [11]);
    expect(presenter.editableStops.length, 2);
    expect(notificationCount, greaterThanOrEqualTo(1));
  });

  test(
    'loadTrip escolhe viagem planejada quando agenda tem concluida antes',
    () async {
      final completedTrip = _sampleTrip().copyWithForTest(
        id: 10,
        status: TripTrackingStatus.completed,
        horarioPartida: '07:00',
      );
      final plannedTrip = _sampleTrip().copyWithForTest(
        id: 12,
        status: TripTrackingStatus.planned,
        horarioPartida: '13:00',
      );
      final presenter = MotoristaSessionPresenter(
        tripGateway: _FakeDriverTripGateway(
          dayTrips: [completedTrip, plannedTrip],
        ),
      );

      await presenter.loadTrip(7);

      expect(presenter.dayTrips.map((trip) => trip.id), [10, 12]);
      expect(presenter.trip?.id, 12);
    },
  );

  test(
    'loadTrip respeita viagem preferida por deep link na agenda do dia',
    () async {
      final activeTrip = _sampleTrip().copyWithForTest(
        id: 11,
        status: TripTrackingStatus.inProgress,
        horarioPartida: '07:00',
      );
      final linkedTrip = _sampleTrip().copyWithForTest(
        id: 42,
        status: TripTrackingStatus.planned,
        horarioPartida: '13:00',
      );
      final presenter = MotoristaSessionPresenter(
        tripGateway: _FakeDriverTripGateway(dayTrips: [activeTrip, linkedTrip]),
      );

      await presenter.loadTrip(7, preferredTripId: 42);

      expect(presenter.dayTrips.map((trip) => trip.id), [11, 42]);
      expect(presenter.trip?.id, 42);
    },
  );

  test('connectRouteEvents repassa evento realtime e notifica', () async {
    final presenter = MotoristaSessionPresenter(
      tripGateway: _FakeDriverTripGateway(trip: _sampleTrip()),
    );
    final gateway = _FakeTripRouteEventGateway();
    var notificationCount = 0;
    presenter.addListener(() => notificationCount++);

    await presenter.loadTrip(7);
    notificationCount = 0;

    presenter.connectRouteEvents(gateway, 66, 'Bearer token');
    gateway.emit(
      TripRouteEvent(
        tipo: 'STATUS_ATUALIZADO',
        viagemId: 11,
        motoristaId: 7,
        dataOperacao: DateTime(2026, 4, 21),
        status: TripTrackingStatus.inProgress,
        rotaVersao: 2,
        totalParadas: 2,
        paradasPendentes: 2,
        paradasConcluidas: 0,
        proximaParadaId: 80,
        proximaParadaOrdem: 1,
        proximoPassageiroId: 30,
        proximoPassageiroNome: 'Ana',
        proximaParadaStatus: TripTrackingStopStatus.pending,
        etaMinutosAteProximaParada: 8,
        etaAtualizadoEm: DateTime(2026, 4, 21, 7, 40),
        latitudeMotorista: null,
        longitudeMotorista: null,
        localizacaoAtualizadaEm: null,
        atualizadoEm: DateTime(2026, 4, 21, 7, 40),
      ),
    );

    expect(presenter.trip?.status, TripTrackingStatus.inProgress);
    expect(presenter.trip?.etaMinutosAteProximaParada, 8);
    expect(notificationCount, greaterThanOrEqualTo(1));
  });
}
