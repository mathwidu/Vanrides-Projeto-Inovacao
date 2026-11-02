import 'package:vanrides_frontend_dev/features/service_presence/data/service_presence_service.dart';
import 'package:vanrides_frontend_dev/features/service_presence/domain/passenger_available_service_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/data/trip_live_location_service.dart';
import 'package:vanrides_frontend_dev/features/tracking/data/trip_tracking_service.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_route_geometry_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_live_location_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_record.dart';

class FakePassengerTripTrackingGateway implements TripTrackingGateway {
  const FakePassengerTripTrackingGateway(this.record, {this.dayTrips});

  final TripTrackingRecord? record;
  final List<TripTrackingRecord>? dayTrips;

  @override
  Future<TripTrackingRecord?> fetchCurrentTrip(int passageiroId) async {
    await Future<void>.delayed(Duration.zero);
    return record;
  }

  @override
  Future<List<TripTrackingRecord>> fetchDayTrips(int passageiroId) async {
    await Future<void>.delayed(Duration.zero);
    final trips = dayTrips;
    if (trips != null) {
      return trips;
    }
    return record == null ? const [] : [record!];
  }
}

class FakeTripLiveLocationGateway implements TripLiveLocationGateway {
  const FakeTripLiveLocationGateway(this.record);

  final TripLiveLocationRecord? record;

  @override
  Future<TripLiveLocationRecord?> fetchLatestLocation(int motoristaId) async {
    await Future<void>.delayed(Duration.zero);
    return record;
  }
}

class ReloadingTripTrackingGateway implements TripTrackingGateway {
  ReloadingTripTrackingGateway(this.factory);

  final TripTrackingRecord Function() factory;

  @override
  Future<TripTrackingRecord?> fetchCurrentTrip(int passageiroId) async {
    await Future<void>.delayed(Duration.zero);
    return factory();
  }

  @override
  Future<List<TripTrackingRecord>> fetchDayTrips(int passageiroId) async {
    await Future<void>.delayed(Duration.zero);
    return [factory()];
  }
}

class FakeHomeServicePresenceGateway implements ServicePresenceGateway {
  String? lastStatus;
  bool confirmed = false;

  @override
  Future<void> clearPresence(int presencaId) async {
    confirmed = false;
  }

  @override
  Future<List<PassengerAvailableServiceRecord>> fetchAvailableServices({
    required int passageiroId,
    required DateTime data,
  }) async {
    await Future<void>.delayed(Duration.zero);
    return [
      PassengerAvailableServiceRecord(
        servicoId: 15,
        nome: 'Linha Manhã',
        tipoOperacao: 'IDA',
        dataOperacao: DateTime(data.year, data.month, data.day),
        horarioPartida: '07:30',
        destinoFinalNome: 'Escola Central',
        politicaPresenca: 'EXIGE_CONFIRMACAO',
        presencaId: confirmed ? 99 : null,
        presencaStatus: confirmed ? 'CONFIRMED' : 'UNKNOWN',
      ),
      PassengerAvailableServiceRecord(
        servicoId: 16,
        nome: 'Linha Tarde',
        tipoOperacao: 'VOLTA',
        dataOperacao: DateTime(data.year, data.month, data.day),
        horarioPartida: '17:45',
        destinoFinalNome: 'Casa',
        politicaPresenca: 'EXIGE_CONFIRMACAO',
        presencaId: null,
        presencaStatus: 'UNKNOWN',
      ),
    ];
  }

  @override
  Future<void> setPresenceStatus({
    required PassengerAvailableServiceRecord service,
    required int passageiroId,
    required String status,
  }) async {
    lastStatus = status;
    confirmed = status == 'CONFIRMED';
  }
}

TripRouteGeometryRecord samplePassengerTripGeometry() {
  return const TripRouteGeometryRecord(
    provider: 'GOOGLE_DIRECTIONS_V1',
    polyline: '',
    navegacao: TripNavigationRecord(
      provider: 'GOOGLE_MAPS_HANDOFF',
      url: 'https://maps.google.com/?q=feevale',
      origem: 'Origem',
      destino: 'Destino',
      quantidadeWaypoints: 1,
    ),
    waypoints: [
      TripRouteWaypointRecord(
        tipo: 'STOP',
        paradaId: 300,
        passageiroId: 21,
        nome: 'Ana',
        latitude: -29.68,
        longitude: -51.13,
        ordem: 1,
        proximaParada: true,
        paradaDoPassageiro: true,
      ),
    ],
    legs: [],
    filaTotalParadas: 1,
    posicaoPassageiroNaFila: 1,
    paradasAntesDoPassageiro: 0,
  );
}

TripTrackingRecord samplePassengerTripRecord({
  required int id,
  required String servicoNome,
  required String tipoOperacao,
  required String horarioPartida,
  required String passageiroNome,
  TripTrackingStatus status = TripTrackingStatus.planned,
}) {
  return TripTrackingRecord(
    id: id,
    rotaNome: 'Rota Escolar',
    servicoId: id + 100,
    servicoNome: servicoNome,
    tipoOperacao: tipoOperacao,
    dataOperacao: DateTime(2026, 3, 20),
    status: status,
    rotaVersao: 1,
    etaMinutosAtePassageiro: 12,
    etaAtualizadoEm: DateTime(2026, 3, 20, 7, 30),
    motorista: const TripTrackingDriverRecord(id: 7, nome: 'Motorista Portal'),
    paradaDoPassageiro: TripTrackingPassengerStopRecord(
      id: id + 300,
      ordem: 1,
      status: TripTrackingStopStatus.pending,
      etaMinutos: 12,
      confirmadoPresenca: true,
      proximaParada: true,
      passageiroId: 21,
      passageiroNome: passageiroNome,
      menorDeIdade: true,
    ),
    rotaGeografica: samplePassengerTripGeometry(),
    horarioPartida: horarioPartida,
  );
}
