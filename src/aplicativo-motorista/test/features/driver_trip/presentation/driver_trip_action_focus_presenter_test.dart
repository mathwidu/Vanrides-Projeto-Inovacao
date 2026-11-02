import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/domain/driver_trip_record.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/presentation/driver_trip_action_focus_presenter.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_route_geometry_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_state_record.dart';

void main() {
  test('recomenda navegar quando handoff de Maps esta pronto', () {
    final display = presentDriverTripActionFocus(_trip())!;

    expect(display.title, 'Agora: ir para Aluno A');
    expect(display.actionLabel, 'Ir para proxima parada');
    expect(display.summary, 'Parada 1 • 9 min • Maps pronto');
    expect(display.routeLabel, 'Rota validada');
    expect(display.signalLabel, 'Sinal ao vivo');
  });

  test('recomenda confirmar embarque quando nao ha handoff de Maps', () {
    final display = presentDriverTripActionFocus(_trip(withNavigation: false))!;

    expect(display.title, 'Agora: confirmar embarque');
    expect(display.actionLabel, 'Confirmar embarque');
    expect(display.summary, 'Aluno A • Parada 1 • Sem navegacao pronta');
    expect(display.routeLabel, 'Rota validada');
  });

  test('mantem acao operacional com alerta quando sinal esta degradado', () {
    final display =
        presentDriverTripActionFocus(
          _trip(freshness: TripLocationFreshness.stale),
        )!;

    expect(display.title, 'Agora: ir para Aluno A');
    expect(display.signalLabel, 'Sinal antigo');
    expect(
      display.helper,
      'Sinal degradado; siga a acao quando estiver seguro.',
    );
  });

  test('nao cria foco operacional quando nao existe proxima parada', () {
    final display = presentDriverTripActionFocus(_trip(stop: null));

    expect(display, isNull);
  });
}

DriverTripRecord _trip({
  DriverTripStopRecord? stop = _stop,
  bool withNavigation = true,
  TripLocationFreshness freshness = TripLocationFreshness.live,
}) {
  return DriverTripRecord(
    id: 95,
    rotaBaseId: 4,
    rotaNome: 'Rota Centro',
    servicoNome: 'Linha Manha',
    tipoOperacao: 'IDA',
    dataOperacao: DateTime(2026, 3, 13),
    status: TripTrackingStatus.inProgress,
    rotaVersao: 2,
    etaMinutosAteProximaParada: 9,
    etaAtualizadoEm: null,
    motorista: null,
    proximaParada: stop,
    paradas: stop == null ? const [] : const [_stop],
    rotaGeografica: TripRouteGeometryRecord(
      provider: 'GOOGLE_DIRECTIONS_V1',
      polyline: '',
      navegacao:
          withNavigation
              ? const TripNavigationRecord(
                provider: 'GOOGLE_MAPS_HANDOFF',
                url: 'https://maps.google.com/?q=proxima',
                origem: 'Motorista',
                destino: 'Aluno A',
                quantidadeWaypoints: 1,
              )
              : null,
      waypoints: const [],
      legs: const [],
      filaTotalParadas: stop == null ? 0 : 1,
      posicaoPassageiroNaFila: null,
      paradasAntesDoPassageiro: null,
      routeSnapshotStatus: TripRouteSnapshotStatus.materialized,
    ),
    trackingState: TripTrackingStateRecord(
      routeSnapshotStatus: TripRouteSnapshotStatus.materialized,
      locationFreshness: freshness,
      etaConfidence: TripEtaConfidence.high,
      stopsRemaining: stop == null ? 0 : 1,
      stopsCompleted: stop == null ? 1 : 0,
      routeRecomputeReason: TripRouteRecomputeReason.none,
    ),
    horarioPartida: '07:30',
  );
}

const _stop = DriverTripStopRecord(
  id: 11,
  ordem: 1,
  status: TripTrackingStopStatus.pending,
  etaMinutos: 9,
  confirmadoPresenca: true,
  proximaParada: true,
  passageiroId: 42,
  passageiroNome: 'Aluno A',
  menorDeIdade: false,
);
