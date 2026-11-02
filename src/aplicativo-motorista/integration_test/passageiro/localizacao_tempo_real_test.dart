import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:google_maps_flutter/google_maps_flutter.dart';
import 'package:intl/date_symbol_data_local.dart';

import 'package:vanrides_frontend_dev/features/tracking/data/trip_live_location_service.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_live_location_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_route_geometry_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/trip_live_map_panel.dart';
import 'package:vanrides_frontend_dev/services/websocket_service.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/ver_localizacao_screen.dart';

import '../_support/e2e_support.dart';
import '../_support/flows.dart';

String _buildFakeAccessToken(int companyId) {
  final header = base64Url.encode(utf8.encode('{"alg":"none","typ":"JWT"}'));
  final payload = base64Url.encode(
    utf8.encode(jsonEncode({'companyId': companyId})),
  );
  return '$header.$payload.signature';
}

class _FakeTripLiveLocationGateway implements TripLiveLocationGateway {
  const _FakeTripLiveLocationGateway(this.record);

  final TripLiveLocationRecord? record;

  @override
  Future<TripLiveLocationRecord?> fetchLatestLocation(int motoristaId) async {
    await Future<void>.delayed(Duration.zero);
    return record;
  }
}

class _FakeRealtimeWebSocketService implements WebSocketServiceBase {
  bool _connected = false;
  int connectCalls = 0;
  String? lastAuthorizationHeader;
  int? lastCompanyId;
  String? lastTopicSuffix;
  LocationMessageHandler? _onLocationReceived;

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
    _connected = true;
    connectCalls += 1;
    lastAuthorizationHeader = authorizationHeader;
    lastCompanyId = companyId;
    lastTopicSuffix = topicSuffix;
  }

  @override
  void disconnect() {
    _connected = false;
  }

  @override
  void dispose() {
    disconnect();
  }

  void emitLocation({
    required int motoristaId,
    required double latitude,
    required double longitude,
  }) {
    _onLocationReceived?.call({
      'motoristaId': motoristaId,
      'latitude': latitude,
      'longitude': longitude,
    });
  }

  @override
  void sendLocation(
    double latitude,
    double longitude,
    int motoristaId, {
    int? companyId,
  }) {
    emitLocation(
      motoristaId: motoristaId,
      latitude: latitude,
      longitude: longitude,
    );
  }
}

void registerPassageiroLocalizacaoTempoRealTests() {
  testWidgets(
    'Passageiro localização (feliz): tela map-first sai do estado sem sinal e recebe update em tempo real',
    (tester) async {
      if (!kVanRidesE2eMode) {
        throw TestFailure('Rode com VANRIDES_E2E_TEST_MODE=true.');
      }

      final ws = _FakeRealtimeWebSocketService();

      await tester.pumpWidget(
        MaterialApp(
          home: VerLocalizacaoScreen(
            motoristaId: 7,
            email: 'passageiro@vanrides.dev',
            senha: 'Teste@1234',
            accessToken: _buildFakeAccessToken(16),
            trip: TripTrackingRecord(
              id: 88,
              rotaNome: 'Rota QA Map First',
              dataOperacao: DateTime(2026, 3, 13),
              status: TripTrackingStatus.planned,
              rotaVersao: 1,
              etaMinutosAtePassageiro: null,
              etaAtualizadoEm: null,
              motorista: const TripTrackingDriverRecord(
                id: 7,
                nome: 'Motorista QA',
              ),
              paradaDoPassageiro: const TripTrackingPassengerStopRecord(
                id: 801,
                ordem: 1,
                status: TripTrackingStopStatus.pending,
                etaMinutos: null,
                confirmadoPresenca: false,
                proximaParada: true,
                passageiroId: 55,
                passageiroNome: 'Passageiro QA',
                menorDeIdade: true,
              ),
              rotaGeografica: const TripRouteGeometryRecord(
                provider: 'STATIC_GEOMETRY_V1',
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
                    paradaId: 801,
                    passageiroId: 55,
                    nome: 'Passageiro QA',
                    latitude: -23.55,
                    longitude: -46.63,
                    ordem: 1,
                    proximaParada: true,
                    paradaDoPassageiro: true,
                  ),
                ],
                legs: [],
                filaTotalParadas: 1,
                posicaoPassageiroNaFila: 1,
                paradasAntesDoPassageiro: 0,
              ),
            ),
            liveLocationGateway: const _FakeTripLiveLocationGateway(null),
            liveLocationWebSocketService: ws,
          ),
        ),
      );

      await tester.pump();
      await pumpUntil(tester, find.text('Localizacao do Motorista'));
      await pumpUntil(tester, find.text('Aguardando sinal'));
      expect(find.text('Aguardando sinal'), findsWidgets);

      expect(find.byKey(VerLocalizacaoScreen.refreshButtonKey), findsOneWidget);
      expect(find.byType(TripLiveMapPanel), findsOneWidget);
      expect(ws.connectCalls, 1);
      expect(ws.lastCompanyId, 16);
      expect(ws.isConnected, isTrue);

      ws.emitLocation(
        motoristaId: 7,
        latitude: -23.55052,
        longitude: -46.633308,
      );

      await pumpUntilGone(tester, find.text('Aguardando sinal'));
      await pumpUntil(tester, find.text('Sinal ao vivo'));
      expect(find.byType(GoogleMap), findsOneWidget);

      ws.emitLocation(motoristaId: 7, latitude: -23.551, longitude: -46.634);

      await tester.pump(const Duration(milliseconds: 300));
      expect(find.text('Sinal ao vivo'), findsOneWidget);
    },
    timeout: const Timeout(Duration(minutes: 3)),
  );
}

void main() {
  ensureBinding();
  setUpAll(() async {
    await initializeDateFormatting('pt_BR');
  });
  registerPassageiroLocalizacaoTempoRealTests();
}
