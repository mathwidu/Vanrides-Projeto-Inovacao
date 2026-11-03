import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/domain/driver_trip_record.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/presentation/widgets/driver_home_navigation_panel.dart';
import 'package:vanrides_frontend_dev/features/navigation/data/driver_navigation_route_plan_contract.dart';
import 'package:vanrides_frontend_dev/features/navigation/domain/driver_navigation_action_queue.dart';
import 'package:vanrides_frontend_dev/features/navigation/domain/driver_navigation_voice.dart';
import 'package:vanrides_frontend_dev/features/navigation/presentation/widgets/driver_navigation_panel.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_record.dart';

void main() {
  testWidgets('repeat uses voice gateway and mute blocks the next repeat', (
    tester,
  ) async {
    final gateway = _RecordingVoiceGateway();

    await tester.pumpWidget(
      _app(
        DriverHomeNavigationPanel(
          trip: _trip(),
          canOpenMaps: true,
          openingNavigation: false,
          runningLifecycleAction: false,
          voiceGateway: gateway,
          onCompleteStop: () {},
          onOpenNavigation: () {},
        ),
      ),
    );

    await tester.tap(find.byKey(DriverNavigationPanel.repeatInstructionKey));
    await tester.pump();

    expect(gateway.spoken, ['Use o fallback para seguir com seguranca']);

    await tester.tap(find.byKey(DriverNavigationPanel.voiceMuteKey));
    await tester.pump();
    await tester.tap(find.byKey(DriverNavigationPanel.repeatInstructionKey));
    await tester.pump();

    expect(gateway.spoken, ['Use o fallback para seguir com seguranca']);
  });

  testWidgets('repeat uses the voice factory when no gateway is injected', (
    tester,
  ) async {
    final gateway = _RecordingVoiceGateway();
    var factoryCalls = 0;

    await tester.pumpWidget(
      _app(
        DriverHomeNavigationPanel(
          trip: _trip(),
          canOpenMaps: true,
          openingNavigation: false,
          runningLifecycleAction: false,
          voiceGatewayFactory: () {
            factoryCalls += 1;
            return gateway;
          },
          onCompleteStop: () {},
          onOpenNavigation: () {},
        ),
      ),
    );

    await tester.tap(find.byKey(DriverNavigationPanel.repeatInstructionKey));
    await tester.pump();

    expect(factoryCalls, 1);
    expect(gateway.spoken, ['Use o fallback para seguir com seguranca']);
  });

  testWidgets('navigation panel exposes skip and no-show callbacks', (
    tester,
  ) async {
    var skipTaps = 0;
    var noShowTaps = 0;

    await tester.pumpWidget(
      _app(
        DriverHomeNavigationPanel(
          trip: _trip(),
          canOpenMaps: true,
          openingNavigation: false,
          runningLifecycleAction: false,
          voiceGateway: _RecordingVoiceGateway(),
          onCompleteStop: () {},
          onOpenNavigation: () {},
          onSkipStop: () => skipTaps++,
          onNoShowStop: () => noShowTaps++,
        ),
      ),
    );

    await tester.tap(find.byKey(DriverNavigationPanel.skipStopActionKey));
    await tester.tap(find.byKey(DriverNavigationPanel.noShowActionKey));
    await tester.pump();

    expect(skipTaps, 1);
    expect(noShowTaps, 1);
  });

  testWidgets('navigation panel surfaces local action queue summary', (
    tester,
  ) async {
    var retryTaps = 0;

    await tester.pumpWidget(
      _app(
        DriverHomeNavigationPanel(
          trip: _trip(),
          canOpenMaps: true,
          openingNavigation: false,
          runningLifecycleAction: false,
          voiceGateway: _RecordingVoiceGateway(),
          actionQueueSummary: const DriverNavigationActionQueueSummary(
            pendingCount: 1,
            statusLabel: '1 acao local pendente',
            detailLabel: 'Confirmar embarque de Ana Clara',
            helperText: 'Sera enviada quando a conexao estiver segura.',
          ),
          onCompleteStop: () {},
          onOpenNavigation: () {},
          onRetryQueuedActions: () => retryTaps++,
        ),
      ),
    );

    expect(
      find.byKey(DriverNavigationPanel.actionQueueStatusKey),
      findsOneWidget,
    );
    expect(find.text('1 acao local pendente'), findsOneWidget);
    expect(find.text('Confirmar embarque de Ana Clara'), findsOneWidget);
    expect(
      find.text('Sera enviada quando a conexao estiver segura.'),
      findsOneWidget,
    );
    await tester.tap(find.byKey(DriverNavigationPanel.actionQueueRetryKey));
    await tester.pump();

    expect(retryTaps, 1);
  });

  testWidgets('validated route plan powers internal driver guidance', (
    tester,
  ) async {
    final gateway = _RecordingVoiceGateway();

    await tester.pumpWidget(
      _app(
        DriverHomeNavigationPanel(
          trip: _trip(),
          canOpenMaps: true,
          openingNavigation: false,
          runningLifecycleAction: false,
          voiceGateway: gateway,
          routePlanResponse: _routePlanResponse(
            coverageStatus: 'validated',
            offlinePackStatus: 'ready',
          ),
          onCompleteStop: () {},
          onOpenNavigation: () {},
        ),
      ),
    );

    expect(find.text('Siga pela Avenida Ipiranga.'), findsOneWidget);
    expect(find.text('Use o fallback para seguir com seguranca'), findsNothing);

    await tester.tap(find.byKey(DriverNavigationPanel.repeatInstructionKey));
    await tester.pump();

    expect(gateway.spoken, ['Em 200 metros, siga pela Avenida Ipiranga.']);
  });

  testWidgets('fixture route plan remains external fallback in driver panel', (
    tester,
  ) async {
    final gateway = _RecordingVoiceGateway();

    await tester.pumpWidget(
      _app(
        DriverHomeNavigationPanel(
          trip: _trip(),
          canOpenMaps: true,
          openingNavigation: false,
          runningLifecycleAction: false,
          voiceGateway: gateway,
          routePlanResponse: _routePlanResponse(
            coverageStatus: 'fallback',
            offlinePackStatus: 'missing',
          ),
          onCompleteStop: () {},
          onOpenNavigation: () {},
        ),
      ),
    );

    expect(
      find.text('Use o fallback para seguir com seguranca'),
      findsOneWidget,
    );
    expect(find.text('Siga pela Avenida Ipiranga.'), findsNothing);

    await tester.tap(find.byKey(DriverNavigationPanel.repeatInstructionKey));
    await tester.pump();

    expect(gateway.spoken, ['Use o fallback para seguir com seguranca']);
  });
}

Widget _app(Widget child) {
  return MaterialApp(home: Scaffold(body: Center(child: child)));
}

DriverTripRecord _trip() {
  return DriverTripRecord(
    id: 91,
    rotaBaseId: 4,
    rotaNome: 'Rota Centro',
    servicoNome: 'Linha Manha',
    dataOperacao: DateTime(2026, 7, 5),
    status: TripTrackingStatus.inProgress,
    rotaVersao: 3,
    etaMinutosAteProximaParada: 9,
    etaAtualizadoEm: DateTime(2026, 7, 5, 7, 30),
    motorista: const TripTrackingDriverRecord(id: 7, nome: 'Motorista QA'),
    proximaParada: _stop,
    paradas: const [_stop],
    rotaGeografica: null,
  );
}

const _stop = DriverTripStopRecord(
  id: 80,
  ordem: 1,
  status: TripTrackingStopStatus.pending,
  etaMinutos: 9,
  confirmadoPresenca: true,
  proximaParada: true,
  passageiroId: 30,
  passageiroNome: 'Ana Clara',
  menorDeIdade: false,
);

DriverNavigationRoutePlanResponse _routePlanResponse({
  required String coverageStatus,
  required String offlinePackStatus,
}) {
  return DriverNavigationRoutePlanResponse.fromJson({
    'routeId': 'route-panel-test-001',
    'provider': 'fixture',
    'region': 'BR-RS',
    'vehicleType': 'van_school_safe',
    'coverageStatus': coverageStatus,
    'offlinePackStatus': offlinePackStatus,
    'qualityScore': 0.84,
    'estimatedDurationSeconds': 480,
    'estimatedDistanceMeters': 1900,
    'polyline': const [
      [-30.0346, -51.2177],
    ],
    'legs': const [
      {
        'fromStopId': 'origin',
        'toStopId': 'stop-1',
        'distanceMeters': 1900,
        'durationSeconds': 480,
      },
    ],
    'instructions': const [
      {
        'index': 0,
        'text': 'Siga pela Avenida Ipiranga.',
        'voiceText': 'Em 200 metros, siga pela Avenida Ipiranga.',
        'distanceMeters': 200,
        'maneuver': 'continue',
      },
    ],
  });
}

class _RecordingVoiceGateway implements DriverNavigationVoiceGateway {
  final List<String> spoken = [];

  @override
  Future<void> speak(String text) async {
    spoken.add(text);
  }
}
