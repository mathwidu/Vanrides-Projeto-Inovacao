import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/domain/driver_trip_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/trip_map_scene_shell.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_exception.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/presentation/home_motorista_screen.dart';
import 'package:vanrides_frontend_dev/features/navigation/domain/driver_navigation_action_queue.dart';
import 'package:vanrides_frontend_dev/features/navigation/presentation/widgets/driver_navigation_panel.dart';

import '../../support/fakes/fake_location_gateway.dart';
import '../../support/fakes/fake_websocket_service.dart';
import 'home_motorista_test_support.dart';

void main() {
  setUpAll(() async {
    await initializeDateFormatting('pt_BR');
  });

  testWidgets('renderiza modo de corrida com foco na proxima parada', (
    tester,
  ) async {
    await setPhoneViewport(tester);
    final gateway = FakeDriverTripGateway(
      sampleDriverTripRecord(
        id: 11,
        passageiroNome: 'Joao',
        horarioPartida: '07:00',
        status: TripTrackingStatus.inProgress,
      ),
    );

    await pumpHomeMotorista(tester, tripGateway: gateway);
    await tester.pumpAndSettle();

    expect(find.text('9 min'), findsWidgets);
    expect(find.text('Proximo passageiro'), findsOneWidget);
    expect(
      find.byKey(HomeMotoristaScreen.seguirNavegacaoButtonKey),
      findsOneWidget,
    );
    expect(
      find.byKey(HomeMotoristaScreen.abrirMapaCompletoButtonKey),
      findsNothing,
    );
    expect(
      find.byKey(HomeMotoristaScreen.concluirParadaButtonKey),
      findsOneWidget,
    );
    expect(
      find.byKey(HomeMotoristaScreen.encerrarCorridaButtonKey),
      findsOneWidget,
    );
    expect(find.byKey(HomeMotoristaScreen.editarRotaButtonKey), findsNothing);
    expect(
      find.byKey(HomeMotoristaScreen.noShowParadaButtonKey),
      findsOneWidget,
    );
    expect(find.text('Joao'), findsWidgets);
    expect(find.text('Em rota'), findsOneWidget);
    expect(find.text('Mapa'), findsWidgets);

    final shell = tester.widget<TripMapSceneShell>(
      find.byType(TripMapSceneShell),
    );
    expect(shell.sheetColor, isNot(Colors.white));
  });

  testWidgets('home ativa usa fonte local de route plan validado no painel', (
    tester,
  ) async {
    await setPhoneViewport(tester);
    final gateway = FakeDriverTripGateway(
      sampleDriverTripRecord(
        id: 11,
        passageiroNome: 'Joao',
        horarioPartida: '07:00',
        status: TripTrackingStatus.inProgress,
      ),
    );
    final routePlanSource = FakeNavigationRoutePlanSource(
      sampleRoutePlanResponse(
        coverageStatus: 'validated',
        offlinePackStatus: 'ready',
      ),
    );

    await pumpHomeMotorista(
      tester,
      tripGateway: gateway,
      navigationRoutePlanSource: routePlanSource,
    );
    await tester.pumpAndSettle();

    expect(routePlanSource.requestedTripIds, [11]);
    expect(find.text('Siga pela Avenida Ipiranga.'), findsOneWidget);
    expect(find.text('Use o fallback para seguir com seguranca'), findsNothing);
  });

  testWidgets('home ativa usa route plan recebido no payload da viagem', (
    tester,
  ) async {
    await setPhoneViewport(tester);
    final gateway = FakeDriverTripGateway(
      sampleDriverTripRecord(
        id: 11,
        passageiroNome: 'Joao',
        horarioPartida: '07:00',
        status: TripTrackingStatus.inProgress,
        navigationRoutePlan: sampleRoutePlanResponse(
          coverageStatus: 'validada',
          offlinePackStatus: 'ready',
        ),
      ),
    );

    await pumpHomeMotorista(tester, tripGateway: gateway);
    await tester.pumpAndSettle();

    expect(find.text('Siga pela Avenida Ipiranga.'), findsOneWidget);
    expect(find.text('Use o fallback para seguir com seguranca'), findsNothing);
  });

  testWidgets(
    'abre Maps com navegacao restante quando a viagem esta em execucao',
    (tester) async {
      await setPhoneViewport(tester);
      final launcher = RecordingNavigationLauncher();
      final gateway = FakeDriverTripGateway(
        sampleDriverTripRecord(
          id: 11,
          passageiroNome: 'Joao',
          horarioPartida: '07:00',
          status: TripTrackingStatus.inProgress,
          rotaGeografica: sampleDriverGeometry(
            navigationUrl: 'https://maps.google.com/?q=original',
            remainingNavigationUrl: 'https://maps.google.com/?q=restante',
          ),
        ),
      );

      await pumpHomeMotorista(
        tester,
        tripGateway: gateway,
        navigationLauncher: launcher,
      );

      await tester.pumpAndSettle();
      await scrollToAndEnsureVisible(
        tester,
        find.byKey(HomeMotoristaScreen.seguirNavegacaoButtonKey),
      );
      await invokeButton(
        tester,
        find.byKey(HomeMotoristaScreen.seguirNavegacaoButtonKey),
      );

      expect(launcher.openedUrls, ['https://maps.google.com/?q=restante']);
    },
  );

  testWidgets(
    'salva confirmacao de embarque na fila local quando gateway falha offline',
    (tester) async {
      await setPhoneViewport(tester);
      final store = FakeNavigationActionQueueStore();
      final gateway = FakeDriverTripGateway(
        sampleDriverTripRecord(
          id: 11,
          passageiroNome: 'Joao',
          horarioPartida: '07:00',
          status: TripTrackingStatus.inProgress,
        ),
        completeStopError: StateError('offline'),
      );

      await pumpHomeMotorista(
        tester,
        tripGateway: gateway,
        navigationActionQueueStore: store,
      );

      await tester.pumpAndSettle();
      await scrollToAndEnsureVisible(
        tester,
        find.byKey(HomeMotoristaScreen.concluirParadaButtonKey),
      );
      await invokeButton(
        tester,
        find.byKey(HomeMotoristaScreen.concluirParadaButtonKey),
      );

      expect(store.queue.pendingCount, 1);
      expect(store.queue.actions.single.id, '11:111:completeStop');
      expect(
        store.queue.actions.single.type,
        DriverNavigationQueuedActionType.completeStop,
      );
      expect(find.text('1 acao local pendente'), findsOneWidget);
    },
  );

  testWidgets('erro de negocio nao entra na fila local de navegacao', (
    tester,
  ) async {
    await setPhoneViewport(tester);
    final store = FakeNavigationActionQueueStore();
    final gateway = FakeDriverTripGateway(
      sampleDriverTripRecord(
        id: 11,
        passageiroNome: 'Joao',
        horarioPartida: '07:00',
        status: TripTrackingStatus.inProgress,
      ),
      completeStopError: const TripTrackingException(
        'Parada ja resolvida no servidor.',
      ),
    );

    await pumpHomeMotorista(
      tester,
      tripGateway: gateway,
      navigationActionQueueStore: store,
    );

    await tester.pumpAndSettle();
    await scrollToAndEnsureVisible(
      tester,
      find.byKey(HomeMotoristaScreen.concluirParadaButtonKey),
    );
    await invokeButton(
      tester,
      find.byKey(HomeMotoristaScreen.concluirParadaButtonKey),
    );

    expect(store.queue.pendingCount, 0);
    expect(find.text('Parada ja resolvida no servidor.'), findsOneWidget);
    expect(find.text('1 acao local pendente'), findsNothing);
  });

  testWidgets(
    'envia fila local persistida automaticamente ao abrir home do motorista',
    (tester) async {
      await setPhoneViewport(tester);
      final queuedActionCreatedAt = DateTime.utc(2026, 7, 7, 12);
      final initialQueue = const DriverNavigationActionQueue.empty().enqueue(
        tripId: 11,
        stopId: 111,
        stopName: 'Joao',
        type: DriverNavigationQueuedActionType.completeStop,
        createdAt: queuedActionCreatedAt,
      );
      final store = FakeNavigationActionQueueStore(initialQueue);
      final activeTrip = sampleDriverTripRecord(
        id: 11,
        passageiroNome: 'Joao',
        horarioPartida: '07:00',
        status: TripTrackingStatus.inProgress,
      );
      final gateway = FakeDriverTripGateway(
        activeTrip,
        completedStopRecord: activeTrip,
      );

      await pumpHomeMotorista(
        tester,
        tripGateway: gateway,
        navigationActionQueueStore: store,
      );

      await tester.pumpAndSettle();
      expect(gateway.lastLifecycleTripId, 11);
      expect(gateway.lastLifecycleStopId, 111);
      expect(store.queue.pendingCount, 0);
      expect(find.text('1 acao local pendente'), findsNothing);
    },
  );

  testWidgets(
    'tenta auto-sync no foreground e mantem enviar agora quando falha',
    (tester) async {
      await setPhoneViewport(tester);
      final queuedActionCreatedAt = DateTime.utc(2026, 7, 7, 12);
      final initialQueue = const DriverNavigationActionQueue.empty().enqueue(
        tripId: 11,
        stopId: 111,
        stopName: 'Joao',
        type: DriverNavigationQueuedActionType.completeStop,
        createdAt: queuedActionCreatedAt,
      );
      final store = FakeNavigationActionQueueStore(initialQueue);
      final activeTrip = sampleDriverTripRecord(
        id: 11,
        passageiroNome: 'Joao',
        horarioPartida: '07:00',
        status: TripTrackingStatus.inProgress,
      );
      final gateway = FakeDriverTripGateway(
        activeTrip,
        completedStopRecord: activeTrip,
        completeStopError: StateError('offline once'),
      );

      await pumpHomeMotorista(
        tester,
        tripGateway: gateway,
        navigationActionQueueStore: store,
      );

      await tester.pumpAndSettle();
      expect(gateway.lastLifecycleTripId, 11);
      expect(gateway.lastLifecycleStopId, 111);
      expect(store.queue.pendingCount, 1);
      expect(find.text('1 acao local pendente'), findsOneWidget);
      expect(find.text('Confirmar embarque de Joao'), findsOneWidget);

      await scrollToAndEnsureVisible(
        tester,
        find.byKey(DriverNavigationPanel.actionQueueRetryKey),
      );
      await invokeButton(
        tester,
        find.byKey(DriverNavigationPanel.actionQueueRetryKey),
      );

      expect(gateway.lastLifecycleTripId, 11);
      expect(gateway.lastLifecycleStopId, 111);
      expect(store.queue.pendingCount, 0);
      expect(find.text('1 acao local pendente'), findsNothing);
      expect(find.text('Acoes locais enviadas.'), findsOneWidget);
    },
  );

  testWidgets(
    'rele fila persistida e sincroniza quando app volta ao foreground',
    (tester) async {
      await setPhoneViewport(tester);
      final store = FakeNavigationActionQueueStore();
      final activeTrip = sampleDriverTripRecord(
        id: 11,
        passageiroNome: 'Joao',
        horarioPartida: '07:00',
        status: TripTrackingStatus.inProgress,
      );
      final gateway = FakeDriverTripGateway(
        activeTrip,
        completedStopRecord: activeTrip,
      );

      await pumpHomeMotorista(
        tester,
        tripGateway: gateway,
        navigationActionQueueStore: store,
      );
      await tester.pumpAndSettle();

      expect(store.queue.pendingCount, 0);
      expect(gateway.lastLifecycleTripId, isNull);

      store.queue = const DriverNavigationActionQueue.empty().enqueue(
        tripId: 11,
        stopId: 111,
        stopName: 'Joao',
        type: DriverNavigationQueuedActionType.completeStop,
        createdAt: DateTime.utc(2026, 7, 7, 12),
      );

      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
      await tester.pumpAndSettle();

      expect(gateway.lastLifecycleTripId, 11);
      expect(gateway.lastLifecycleStopId, 111);
      expect(store.queue.pendingCount, 0);
      expect(find.text('1 acao local pendente'), findsNothing);
    },
  );

  testWidgets('motorista registra no-show da proxima parada', (tester) async {
    await setPhoneViewport(tester);
    const currentStop = DriverTripStopRecord(
      id: 90,
      ordem: 2,
      status: TripTrackingStopStatus.pending,
      etaMinutos: 9,
      confirmadoPresenca: true,
      proximaParada: true,
      passageiroId: 40,
      passageiroNome: 'Joao',
      menorDeIdade: false,
    );
    const nextStop = DriverTripStopRecord(
      id: 91,
      ordem: 3,
      status: TripTrackingStopStatus.pending,
      etaMinutos: 11,
      confirmadoPresenca: true,
      proximaParada: true,
      passageiroId: 41,
      passageiroNome: 'Maria',
      menorDeIdade: true,
    );

    final activeTrip = DriverTripRecord(
      id: 11,
      rotaBaseId: 4,
      rotaNome: 'Rota Centro',
      dataOperacao: DateTime(2026, 3, 13),
      status: TripTrackingStatus.inProgress,
      rotaVersao: 2,
      etaMinutosAteProximaParada: 9,
      etaAtualizadoEm: DateTime(2026, 3, 18, 23, 38),
      motorista: const TripTrackingDriverRecord(id: 7, nome: 'Motorista QA'),
      proximaParada: currentStop,
      paradas: const [
        DriverTripStopRecord(
          id: 80,
          ordem: 1,
          status: TripTrackingStopStatus.completed,
          etaMinutos: 0,
          confirmadoPresenca: true,
          proximaParada: false,
          passageiroId: 30,
          passageiroNome: 'Ana',
          menorDeIdade: true,
        ),
        currentStop,
        nextStop,
      ],
      rotaGeografica: sampleDriverGeometry(),
    );
    final noShowTrip = DriverTripRecord(
      id: 11,
      rotaBaseId: 4,
      rotaNome: 'Rota Centro',
      dataOperacao: DateTime(2026, 3, 13),
      status: TripTrackingStatus.inProgress,
      rotaVersao: 2,
      etaMinutosAteProximaParada: 11,
      etaAtualizadoEm: DateTime(2026, 3, 18, 23, 40),
      motorista: const TripTrackingDriverRecord(id: 7, nome: 'Motorista QA'),
      proximaParada: nextStop,
      paradas: const [
        DriverTripStopRecord(
          id: 80,
          ordem: 1,
          status: TripTrackingStopStatus.completed,
          etaMinutos: 0,
          confirmadoPresenca: true,
          proximaParada: false,
          passageiroId: 30,
          passageiroNome: 'Ana',
          menorDeIdade: true,
        ),
        DriverTripStopRecord(
          id: 90,
          ordem: 2,
          status: TripTrackingStopStatus.noShow,
          etaMinutos: 0,
          confirmadoPresenca: true,
          proximaParada: false,
          passageiroId: 40,
          passageiroNome: 'Joao',
          menorDeIdade: false,
        ),
        nextStop,
      ],
      rotaGeografica: sampleDriverGeometry(),
    );
    final gateway = FakeDriverTripGateway(
      activeTrip,
      noShowStopRecord: noShowTrip,
    );

    await pumpHomeMotorista(tester, tripGateway: gateway);
    await tester.pumpAndSettle();
    await scrollToAndEnsureVisible(
      tester,
      find.byKey(HomeMotoristaScreen.noShowParadaButtonKey),
    );
    await invokeButton(
      tester,
      find.byKey(HomeMotoristaScreen.noShowParadaButtonKey),
    );

    expect(find.text('Marcar passageiro como nao embarcou?'), findsOneWidget);
    expect(gateway.lastLifecycleTripId, isNull);

    await tester.tap(find.text('Confirmar'));
    await tester.pumpAndSettle();

    expect(gateway.lastLifecycleTripId, 11);
    expect(gateway.lastLifecycleStopId, 90);
    expect(find.text('Maria'), findsWidgets);
  });

  testWidgets(
    'iniciar corrida ativa sinal ao vivo e ancora motorista no mapa',
    (tester) async {
      await setPhoneViewport(tester);
      const nextStop = DriverTripStopRecord(
        id: 80,
        ordem: 1,
        status: TripTrackingStopStatus.pending,
        etaMinutos: 9,
        confirmadoPresenca: false,
        proximaParada: true,
        passageiroId: 30,
        passageiroNome: 'Ana',
        menorDeIdade: true,
      );
      const stops = [nextStop];

      final plannedRecord = DriverTripRecord(
        id: 11,
        rotaBaseId: 4,
        rotaNome: 'Rota Centro',
        dataOperacao: DateTime(2026, 3, 13),
        status: TripTrackingStatus.planned,
        rotaVersao: 2,
        etaMinutosAteProximaParada: 9,
        etaAtualizadoEm: DateTime(2026, 3, 18, 23, 38),
        motorista: const TripTrackingDriverRecord(id: 7, nome: 'Motorista QA'),
        proximaParada: nextStop,
        paradas: stops,
        rotaGeografica: sampleDriverGeometry(),
      );
      final startedRecord = DriverTripRecord(
        id: 11,
        rotaBaseId: 4,
        rotaNome: 'Rota Centro',
        dataOperacao: DateTime(2026, 3, 13),
        status: TripTrackingStatus.inProgress,
        rotaVersao: 2,
        etaMinutosAteProximaParada: 9,
        etaAtualizadoEm: DateTime(2026, 3, 18, 23, 38),
        motorista: const TripTrackingDriverRecord(id: 7, nome: 'Motorista QA'),
        proximaParada: nextStop,
        paradas: stops,
        rotaGeografica: sampleDriverGeometry(),
      );
      final gateway = FakeDriverTripGateway(
        plannedRecord,
        startedRecord: startedRecord,
      );
      final locationGateway = FakeLocationGateway();
      final websocket = FakeWebSocketService();

      await pumpHomeMotorista(
        tester,
        accessToken: jwtWithCompanyId(66),
        tripGateway: gateway,
        locationGateway: locationGateway,
        locationSharingWebSocketService: websocket,
      );

      await tester.pumpAndSettle();
      await scrollToAndEnsureVisible(
        tester,
        find.byKey(HomeMotoristaScreen.iniciarCorridaButtonKey),
      );
      await invokeButton(
        tester,
        find.byKey(HomeMotoristaScreen.iniciarCorridaButtonKey),
      );

      expect(gateway.lastLifecycleTripId, 11);
      expect(websocket.connectCalls, 1);
      expect(websocket.lastCompanyId, 66);
      expect(websocket.sentLocations, isNotEmpty);
    },
  );
}
