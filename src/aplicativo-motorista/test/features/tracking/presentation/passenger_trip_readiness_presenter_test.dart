import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_state_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/passenger_trip_readiness_presenter.dart';

void main() {
  test('resume ETA confiavel quando rota e sinal estao bons', () {
    final display = presentPassengerTripReadiness(_trip());

    expect(display.title, 'Van a caminho da sua parada');
    expect(display.etaLabel, '12 min até sua parada');
    expect(display.routeLabel, 'Rota validada');
    expect(display.confidenceLabel, 'ETA confiável');
    expect(
      display.summary,
      '12 min até sua parada • Rota validada • ETA confiável',
    );
  });

  test('nao promete precisao quando a rota ainda esta em preparo', () {
    final display = presentPassengerTripReadiness(
      _trip(
        routeSnapshotStatus: TripRouteSnapshotStatus.planned,
        etaConfidence: TripEtaConfidence.medium,
      ),
    );

    expect(display.title, 'Previsão em observação');
    expect(display.etaLabel, '12 min em observação');
    expect(display.routeLabel, 'Rota em preparo');
    expect(display.confidenceLabel, 'ETA em observação');
  });

  test('preserva fallback quando tracking e rota estao indisponiveis', () {
    final display = presentPassengerTripReadiness(
      _trip(
        etaMinutosAtePassageiro: null,
        routeSnapshotStatus: TripRouteSnapshotStatus.unavailable,
        freshness: TripLocationFreshness.unavailable,
        etaConfidence: TripEtaConfidence.unavailable,
      ),
    );

    expect(display.title, 'Aguardando sinal da van');
    expect(display.etaLabel, 'ETA indisponível');
    expect(display.routeLabel, 'Sem rota validada');
    expect(display.confidenceLabel, 'Sem ETA confiável');
    expect(
      display.summary,
      'ETA indisponível • Sem rota validada • Sem ETA confiável',
    );
  });
}

TripTrackingRecord _trip({
  int? etaMinutosAtePassageiro = 12,
  TripRouteSnapshotStatus routeSnapshotStatus =
      TripRouteSnapshotStatus.materialized,
  TripLocationFreshness freshness = TripLocationFreshness.live,
  TripEtaConfidence etaConfidence = TripEtaConfidence.high,
}) {
  return TripTrackingRecord(
    id: 130,
    rotaNome: 'Rota Escolar',
    servicoId: 230,
    servicoNome: 'Linha Manha',
    tipoOperacao: 'IDA',
    dataOperacao: DateTime(2026, 7, 6),
    status: TripTrackingStatus.inProgress,
    rotaVersao: 1,
    etaMinutosAtePassageiro: etaMinutosAtePassageiro,
    etaAtualizadoEm: DateTime(2026, 7, 6, 7, 20),
    motorista: const TripTrackingDriverRecord(id: 7, nome: 'Motorista Portal'),
    paradaDoPassageiro: const TripTrackingPassengerStopRecord(
      id: 330,
      ordem: 2,
      status: TripTrackingStopStatus.pending,
      etaMinutos: 12,
      confirmadoPresenca: true,
      proximaParada: false,
      passageiroId: 21,
      passageiroNome: 'Ana Escola',
      menorDeIdade: true,
    ),
    rotaGeografica: null,
    horarioPartida: '07:30',
    trackingState: TripTrackingStateRecord(
      routeSnapshotStatus: routeSnapshotStatus,
      locationFreshness: freshness,
      etaConfidence: etaConfidence,
      stopsRemaining: 3,
      stopsCompleted: 1,
      stopsBeforePassenger: 2,
      routeRecomputeReason: TripRouteRecomputeReason.none,
    ),
  );
}
