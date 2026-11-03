import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:vanrides_frontend_dev/features/tracking/data/trip_live_location_service.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_live_location_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_route_geometry_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/map_first_trip_tracking_view.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/trip_live_map_panel.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/trip_tracking_presenter.dart';
import 'package:vanrides_frontend_dev/services/websocket_service.dart';

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

TripTrackingRecord _sampleTripWithoutGeometry() {
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
    rotaGeografica: null,
  );
}

Widget _buildSubject({
  TripTrackingRecord? trip,
  VoidCallback? onPrimaryAction,
  VoidCallback? onSecondaryAction,
  String? secondaryActionLabel,
}) {
  return MaterialApp(
    home: Scaffold(
      body: SingleChildScrollView(
        child: MapFirstTripTrackingView(
          trip: trip ?? _sampleTrip(),
          audienceLabel: 'PASSAGEIRO',
          headline: 'Sua van está a caminho',
          subheadline: 'Rota Escolar Manhã · Motorista Portal',
          nextEventLabel: 'Próxima parada',
          secondaryStatusLabel: 'Em andamento',
          primaryActionLabel: 'Ver detalhe',
          onPrimaryAction: onPrimaryAction,
          onSecondaryAction: onSecondaryAction,
          secondaryActionLabel: secondaryActionLabel,
          liveLocationGateway: const _FakeTripLiveLocationGateway(null),
          liveLocationWebSocketService: _FakeWebSocketService(),
          mapRenderMode: TripLiveMapRenderMode.placeholder,
        ),
      ),
    ),
  );
}

Future<void> _pumpTrackingView(
  WidgetTester tester, {
  TripTrackingRecord? trip,
  VoidCallback? onPrimaryAction,
  VoidCallback? onSecondaryAction,
  String? secondaryActionLabel,
}) async {
  await tester.binding.setSurfaceSize(const Size(1440, 1200));
  addTearDown(() => tester.binding.setSurfaceSize(null));

  await tester.pumpWidget(
    _buildSubject(
      trip: trip,
      onPrimaryAction: onPrimaryAction,
      onSecondaryAction: onSecondaryAction,
      secondaryActionLabel: secondaryActionLabel,
    ),
  );
  await tester.pumpAndSettle();
}

void main() {
  setUpAll(() async {
    await initializeDateFormatting('pt_BR', null);
  });

  testWidgets('renderiza headline e subheadline da viagem', (tester) async {
    await _pumpTrackingView(tester);

    expect(find.text('Sua van está a caminho'), findsOneWidget);
    expect(find.text('Rota Escolar Manhã · Motorista Portal'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('exibe label do status da viagem no overlay', (tester) async {
    final statusLabel =
        presentTripStatus(TripTrackingStatus.inProgress).label;

    await _pumpTrackingView(tester);

    expect(find.text(statusLabel), findsAtLeastNWidgets(1));
    expect(tester.takeException(), isNull);
  });

  testWidgets('botao de acao primaria dispara callback', (tester) async {
    var pressed = false;

    await _pumpTrackingView(
      tester,
      onPrimaryAction: () => pressed = true,
    );

    await tester.scrollUntilVisible(find.text('Ver detalhe'), 200);
    await tester.pumpAndSettle();
    await tester.tap(find.text('Ver detalhe'));
    await tester.pumpAndSettle();

    expect(pressed, isTrue);
    expect(tester.takeException(), isNull);
  });

  testWidgets('exibe acao secundaria quando fornecida', (tester) async {
    await _pumpTrackingView(
      tester,
      secondaryActionLabel: 'Expandir mapa',
    );

    await tester.scrollUntilVisible(find.text('Expandir mapa'), 200);
    await tester.pumpAndSettle();
    expect(find.text('Expandir mapa'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('sem viagem sem rota geografica nao lanca excecao', (
    tester,
  ) async {
    await _pumpTrackingView(
      tester,
      trip: _sampleTripWithoutGeometry(),
    );

    expect(find.text('Sua van está a caminho'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
