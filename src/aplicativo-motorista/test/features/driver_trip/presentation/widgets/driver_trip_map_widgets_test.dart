import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/domain/driver_trip_record.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/presentation/widgets/driver_home_widgets.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_state_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/trip_live_map_panel.dart';
import 'package:vanrides_frontend_dev/l10n/app_strings.dart';
import 'package:vanrides_frontend_dev/services/websocket_service.dart';

void main() {
  testWidgets('DriverFullMapTab renders unavailable state and refresh action', (
    tester,
  ) async {
    var refreshTapped = false;

    await tester.pumpWidget(
      _mapTabApp(
        trip: null,
        errorMessage: 'Falha ao carregar mapa',
        onRefresh: () => refreshTapped = true,
      ),
    );

    expect(
      find.text(AppStrings.motorista.mapaCompletoIndisponivel),
      findsOneWidget,
    );
    expect(find.text('Falha ao carregar mapa'), findsOneWidget);

    await tester.tap(find.text(AppStrings.motorista.atualizar));
    await tester.pumpAndSettle();

    expect(refreshTapped, isTrue);
  });

  testWidgets('DriverFullMapTab renders map context for the active trip', (
    tester,
  ) async {
    await tester.pumpWidget(_mapTabApp(trip: _trip()));

    expect(find.text('Rota v2'), findsOneWidget);
    expect(find.text(AppStrings.motorista.proximaParada), findsOneWidget);
    expect(find.text('Ana Mapa'), findsOneWidget);
    expect(find.text(AppStrings.motorista.eta), findsOneWidget);
    expect(find.text('9 min'), findsOneWidget);
    expect(find.text('Transmitindo ao vivo'), findsOneWidget);
    expect(find.text('1 parada restante'), findsOneWidget);
  });
}

Widget _mapTabApp({
  required DriverTripRecord? trip,
  String? errorMessage,
  VoidCallback? onRefresh,
}) {
  return MaterialApp(
    home: Scaffold(
      body: SizedBox(
        width: 420,
        height: 760,
        child: DriverFullMapTab(
          isLoading: false,
          errorMessage: errorMessage,
          trip: trip,
          motoristaId: 7,
          email: 'driver@vanrides.dev',
          senha: 'senha',
          accessToken: null,
          liveLocationGateway: null,
          liveLocationWebSocketService: _FakeWebSocketService(),
          mapRenderMode: TripLiveMapRenderMode.placeholder,
          driverLiveLocation: null,
          onRefresh: onRefresh ?? () {},
        ),
      ),
    ),
  );
}

DriverTripRecord _trip() {
  return DriverTripRecord(
    id: 11,
    rotaBaseId: 4,
    rotaNome: 'Rota Centro',
    servicoNome: 'Linha 07:00',
    dataOperacao: DateTime(2026, 3, 13),
    status: TripTrackingStatus.inProgress,
    rotaVersao: 2,
    etaMinutosAteProximaParada: 9,
    etaAtualizadoEm: DateTime(2026, 3, 18, 23, 38),
    motorista: const TripTrackingDriverRecord(id: 7, nome: 'Motorista QA'),
    proximaParada: _nextStop,
    paradas: const [_nextStop],
    rotaGeografica: null,
    trackingState: const TripTrackingStateRecord(
      routeSnapshotStatus: TripRouteSnapshotStatus.materialized,
      locationFreshness: TripLocationFreshness.live,
      etaConfidence: TripEtaConfidence.high,
      stopsRemaining: 1,
      stopsCompleted: 0,
      routeRecomputeReason: TripRouteRecomputeReason.none,
    ),
  );
}

const _nextStop = DriverTripStopRecord(
  id: 80,
  ordem: 1,
  status: TripTrackingStopStatus.pending,
  etaMinutos: 9,
  confirmadoPresenca: false,
  proximaParada: true,
  passageiroId: 30,
  passageiroNome: 'Ana Mapa',
  menorDeIdade: false,
);

class _FakeWebSocketService implements WebSocketServiceBase {
  @override
  bool get isConnected => false;

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
