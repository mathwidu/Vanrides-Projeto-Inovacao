import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:vanrides_frontend_dev/features/tracking/data/trip_live_location_service.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_live_location_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_route_geometry_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/trip_live_map_panel.dart';
import 'package:vanrides_frontend_dev/services/websocket_service.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/ver_localizacao_screen.dart';

class _FakeTripLiveLocationGateway implements TripLiveLocationGateway {
  const _FakeTripLiveLocationGateway(this.record);

  final TripLiveLocationRecord? record;

  @override
  Future<TripLiveLocationRecord?> fetchLatestLocation(int motoristaId) async {
    await Future<void>.delayed(Duration.zero);
    return record;
  }
}

class _FakeWebSocketService implements WebSocketServiceBase {
  @override
  bool get isConnected => true;

  @override
  LocationMessageHandler? onLocationReceived;

  @override
  void connect({
    required String authorizationHeader,
    required int companyId,
    String? topicSuffix,
  }) {}

  @override
  void disconnect() {}

  @override
  void dispose() {}

  @override
  void sendLocation(
    double latitude,
    double longitude,
    int motoristaId, {
    int? companyId,
  }) {}
}

TripRouteGeometryRecord _sampleGeometry() {
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

TripTrackingRecord _sampleTrip() {
  return TripTrackingRecord(
    id: 30,
    rotaNome: 'Rota Escolar Manha',
    dataOperacao: DateTime(2026, 3, 12),
    status: TripTrackingStatus.inProgress,
    rotaVersao: 2,
    etaMinutosAtePassageiro: 12,
    etaAtualizadoEm: DateTime(2026, 3, 18, 23, 38),
    motorista: const TripTrackingDriverRecord(id: 7, nome: 'Motorista Portal'),
    paradaDoPassageiro: const TripTrackingPassengerStopRecord(
      id: 300,
      ordem: 1,
      status: TripTrackingStopStatus.pending,
      etaMinutos: 12,
      confirmadoPresenca: true,
      proximaParada: true,
      passageiroId: 21,
      passageiroNome: 'Ana',
      menorDeIdade: true,
    ),
    rotaGeografica: _sampleGeometry(),
  );
}

void main() {
  setUpAll(() async {
    await initializeDateFormatting('pt_BR', null);
  });

  testWidgets('exibe cabecalho sem viagem ativa', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: VerLocalizacaoScreen(
          motoristaId: 1,
          email: 'pass@test.dev',
          senha: 'senha',
          trip: null,
          liveLocationGateway: const _FakeTripLiveLocationGateway(null),
          liveLocationWebSocketService: _FakeWebSocketService(),
          mapRenderMode: TripLiveMapRenderMode.placeholder,
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Mapa completo da viagem'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('exibe dados da viagem quando trip esta preenchido', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: VerLocalizacaoScreen(
          motoristaId: 1,
          email: 'pass@test.dev',
          senha: 'senha',
          trip: _sampleTrip(),
          liveLocationGateway: const _FakeTripLiveLocationGateway(null),
          liveLocationWebSocketService: _FakeWebSocketService(),
          mapRenderMode: TripLiveMapRenderMode.placeholder,
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.scrollUntilVisible(find.text('ETA atual'), 200);
    await tester.pumpAndSettle();

    expect(find.text('ETA atual'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('botao de refresh esta presente', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: VerLocalizacaoScreen(
          motoristaId: 1,
          email: 'pass@test.dev',
          senha: 'senha',
          trip: null,
          liveLocationGateway: const _FakeTripLiveLocationGateway(null),
          liveLocationWebSocketService: _FakeWebSocketService(),
          mapRenderMode: TripLiveMapRenderMode.placeholder,
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.byKey(VerLocalizacaoScreen.refreshButtonKey), findsOneWidget);
    await tester.tap(find.byKey(VerLocalizacaoScreen.refreshButtonKey));
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
  });
}
