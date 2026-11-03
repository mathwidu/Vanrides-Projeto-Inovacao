import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_route_event.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/map_first_v2_shell.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/trip_live_map_panel.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/home_passageiro_screen.dart';

import '../support/fakes/fake_account_profile_gateway.dart';
import '../support/fakes/fake_trip_route_event_gateway.dart';
import '../support/fakes/fake_websocket_service.dart';
import '../support/passenger_home_test_support.dart';

void main() {
  setUpAll(() async {
    await initializeDateFormatting('pt_BR');
  });

  testWidgets('renderiza resumo da viagem quando tracking existe', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: HomePassageiroScreen(
          nome: 'Ana',
          idPassageiro: 21,
          motoristaId: 7,
          email: 'ana@vanrides.dev',
          senha: 'Secret123',
          accessToken: 'token.passageiro',
          trackingGateway: FakePassengerTripTrackingGateway(
            TripTrackingRecord(
              id: 30,
              rotaNome: 'Rota Escolar Manha',
              dataOperacao: DateTime(2026, 3, 12),
              status: TripTrackingStatus.inProgress,
              rotaVersao: 2,
              etaMinutosAtePassageiro: 12,
              etaAtualizadoEm: DateTime(2026, 3, 18, 23, 38),
              motorista: const TripTrackingDriverRecord(
                id: 7,
                nome: 'Motorista Portal',
              ),
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
              rotaGeografica: samplePassengerTripGeometry(),
            ),
          ),
          tripRouteEventGateway: FakeTripRouteEventGateway(),
          liveLocationGateway: const FakeTripLiveLocationGateway(null),
          liveLocationWebSocketService: FakeWebSocketService(),
          mapRenderMode: TripLiveMapRenderMode.placeholder,
        ),
      ),
    );

    await tester.pumpAndSettle();

    expect(find.text('Minha parada'), findsWidgets);
    expect(find.text('Aguardando posição da van'), findsWidgets);
    expect(find.text('Atualizar sinal'), findsWidgets);
    expect(find.text('Sem sinal da van'), findsNothing);
    expect(find.text('Chega em cerca de 12 min'), findsNothing);
    expect(find.textContaining('Você é o próximo da fila'), findsOneWidget);
    expect(find.textContaining('Motorista Portal'), findsNothing);
    expect(find.text('Chats'), findsNothing);
    final scaffold = tester.widget<Scaffold>(find.byType(Scaffold).first);
    expect(scaffold.extendBody, isTrue);
    expect(find.byKey(const Key('mapFirst.bottomNav.overlay')), findsNothing);
    expect(
      find.byKey(const Key('passenger.map.footerSheet.footer')),
      findsOneWidget,
    );
    await tester.drag(
      find.byKey(const Key('passenger.trip.sheet')),
      const Offset(0, -240),
    );
    await tester.pumpAndSettle();
    expect(
      find.byKey(const Key('passenger.trip.sheet.expanded')),
      findsOneWidget,
    );
    expect(find.textContaining('Motorista Portal'), findsWidgets);
    await tester.ensureVisible(
      find.byKey(HomePassageiroScreen.verLocalizacaoButtonKey),
    );
    await tester.pumpAndSettle();
    expect(
      find.byKey(HomePassageiroScreen.verLocalizacaoButtonKey),
      findsOneWidget,
    );
    expect(find.text('Detalhes'), findsOneWidget);
    expect(find.text('Mais'), findsNothing);
  });

  testWidgets('mostra estado vazio quando nao existe viagem hoje', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: HomePassageiroScreen(
          nome: 'Ana',
          idPassageiro: 21,
          motoristaId: 7,
          email: 'ana@vanrides.dev',
          senha: 'Secret123',
          accessToken: 'token.passageiro',
          trackingGateway: const FakePassengerTripTrackingGateway(null),
          tripRouteEventGateway: FakeTripRouteEventGateway(),
          liveLocationGateway: const FakeTripLiveLocationGateway(null),
          liveLocationWebSocketService: FakeWebSocketService(),
          mapRenderMode: TripLiveMapRenderMode.placeholder,
        ),
      ),
    );

    await tester.pumpAndSettle();

    expect(find.text('Nenhuma viagem prevista agora'), findsOneWidget);
    expect(
      find.textContaining('rota do dia e a fila de paradas'),
      findsOneWidget,
    );
  });

  testWidgets(
    'aba de presenca permite escolher servico do dia sem sair da home',
    (tester) async {
      final presenceGateway = FakeHomeServicePresenceGateway();

      await tester.pumpWidget(
        MaterialApp(
          home: HomePassageiroScreen(
            nome: 'Ana',
            idPassageiro: 21,
            motoristaId: 7,
            email: 'ana@vanrides.dev',
            senha: 'Secret123',
            accessToken: 'token.passageiro',
            trackingGateway: const FakePassengerTripTrackingGateway(null),
            servicePresenceGateway: presenceGateway,
            tripRouteEventGateway: FakeTripRouteEventGateway(),
            liveLocationGateway: const FakeTripLiveLocationGateway(null),
            liveLocationWebSocketService: FakeWebSocketService(),
            mapRenderMode: TripLiveMapRenderMode.placeholder,
          ),
        ),
      );

      await tester.pumpAndSettle();
      await tester.tap(find.text('Presença'));
      await tester.pumpAndSettle();

      expect(find.text('Presença de hoje'), findsOneWidget);
      expect(find.text('Linha Manhã'), findsOneWidget);
      expect(find.text('Linha Tarde'), findsOneWidget);

      await tester.tap(
        find.byKey(const ValueKey('service-presence.confirm.15')),
      );
      await tester.pumpAndSettle();

      expect(presenceGateway.lastStatus, 'CONFIRMED');
      expect(find.text('Vou neste horário'), findsNothing);
    },
  );

  testWidgets('mostra agenda do dia e permite alternar viagem', (tester) async {
    final manha = samplePassengerTripRecord(
      id: 30,
      servicoNome: 'Linha Manha',
      tipoOperacao: 'IDA',
      horarioPartida: '07:00',
      passageiroNome: 'Ana Manha',
    );
    final tarde = samplePassengerTripRecord(
      id: 31,
      servicoNome: 'Linha Tarde',
      tipoOperacao: 'VOLTA',
      horarioPartida: '13:00',
      passageiroNome: 'Ana Tarde',
    );

    await tester.pumpWidget(
      MaterialApp(
        home: HomePassageiroScreen(
          nome: 'Ana',
          idPassageiro: 21,
          motoristaId: 7,
          email: 'ana@vanrides.dev',
          senha: 'Secret123',
          accessToken: 'token.passageiro',
          trackingGateway: FakePassengerTripTrackingGateway(
            manha,
            dayTrips: [manha, tarde],
          ),
          tripRouteEventGateway: FakeTripRouteEventGateway(),
          liveLocationGateway: const FakeTripLiveLocationGateway(null),
          liveLocationWebSocketService: FakeWebSocketService(),
          mapRenderMode: TripLiveMapRenderMode.placeholder,
        ),
      ),
    );

    await tester.pumpAndSettle();

    expect(find.byKey(HomePassageiroScreen.agendaViagensKey), findsOneWidget);
    expect(find.text('2 viagens hoje'), findsNothing);
    expect(find.text('07:00 • Ida'), findsOneWidget);
    expect(find.text('13:00 • Volta'), findsOneWidget);
    expect(find.text('Parada 1'), findsOneWidget);
    expect(find.text('Van ainda não iniciou'), findsOneWidget);
    expect(find.text('Aguardando inicio'), findsNothing);
    expect(find.text('Agenda de hoje'), findsNothing);

    await tester.tap(
      find.byKey(HomePassageiroScreen.selecionarViagemDiaButtonKey(31)),
    );
    await tester.pumpAndSettle();

    expect(find.text('Van ainda não iniciou'), findsOneWidget);
    expect(find.text('Aguardando inicio'), findsNothing);
  });

  testWidgets('mostra tipo da operacao na viagem unica do passageiro', (
    tester,
  ) async {
    final trip = samplePassengerTripRecord(
      id: 32,
      servicoNome: 'Linha Circular',
      tipoOperacao: 'CIRCULAR',
      horarioPartida: '18:00',
      passageiroNome: 'Ana Noite',
    );

    await tester.pumpWidget(
      MaterialApp(
        home: HomePassageiroScreen(
          nome: 'Ana',
          idPassageiro: 21,
          motoristaId: 7,
          email: 'ana@vanrides.dev',
          senha: 'Secret123',
          accessToken: 'token.passageiro',
          trackingGateway: FakePassengerTripTrackingGateway(trip),
          tripRouteEventGateway: FakeTripRouteEventGateway(),
          liveLocationGateway: const FakeTripLiveLocationGateway(null),
          liveLocationWebSocketService: FakeWebSocketService(),
          mapRenderMode: TripLiveMapRenderMode.placeholder,
        ),
      ),
    );

    await tester.pumpAndSettle();

    expect(find.textContaining('Linha Circular'), findsNothing);
    expect(find.text('Circular'), findsOneWidget);
  });

  testWidgets('abre viagem do deep link quando passageiro tem agenda no dia', (
    tester,
  ) async {
    final manha = samplePassengerTripRecord(
      id: 30,
      servicoNome: 'Linha Manha',
      tipoOperacao: 'IDA',
      horarioPartida: '07:00',
      passageiroNome: 'Ana Manha',
      status: TripTrackingStatus.inProgress,
    );
    final tarde = samplePassengerTripRecord(
      id: 31,
      servicoNome: 'Linha Tarde',
      tipoOperacao: 'VOLTA',
      horarioPartida: '13:00',
      passageiroNome: 'Ana Tarde',
      status: TripTrackingStatus.planned,
    );

    await tester.pumpWidget(
      MaterialApp(
        home: HomePassageiroScreen(
          nome: 'Ana',
          idPassageiro: 21,
          motoristaId: 7,
          email: 'ana@vanrides.dev',
          senha: 'Secret123',
          accessToken: 'token.passageiro',
          initialTripId: 31,
          trackingGateway: FakePassengerTripTrackingGateway(
            manha,
            dayTrips: [manha, tarde],
          ),
          tripRouteEventGateway: FakeTripRouteEventGateway(),
          liveLocationGateway: const FakeTripLiveLocationGateway(null),
          liveLocationWebSocketService: FakeWebSocketService(),
          mapRenderMode: TripLiveMapRenderMode.placeholder,
        ),
      ),
    );

    await tester.pumpAndSettle();

    expect(find.byKey(HomePassageiroScreen.agendaViagensKey), findsOneWidget);
    expect(find.text('Van ainda não iniciou'), findsOneWidget);
    expect(find.text('Aguardando inicio'), findsNothing);
  });

  testWidgets(
    'avisa quando viagem do deep link nao esta na agenda do passageiro',
    (tester) async {
      final manha = samplePassengerTripRecord(
        id: 30,
        servicoNome: 'Linha Manha',
        tipoOperacao: 'IDA',
        horarioPartida: '07:00',
        passageiroNome: 'Ana Manha',
        status: TripTrackingStatus.inProgress,
      );
      final tarde = samplePassengerTripRecord(
        id: 31,
        servicoNome: 'Linha Tarde',
        tipoOperacao: 'VOLTA',
        horarioPartida: '13:00',
        passageiroNome: 'Ana Tarde',
        status: TripTrackingStatus.planned,
      );

      await tester.pumpWidget(
        MaterialApp(
          home: HomePassageiroScreen(
            nome: 'Ana',
            idPassageiro: 21,
            motoristaId: 7,
            email: 'ana@vanrides.dev',
            senha: 'Secret123',
            accessToken: 'token.passageiro',
            initialTripId: 99,
            trackingGateway: FakePassengerTripTrackingGateway(
              manha,
              dayTrips: [manha, tarde],
            ),
            tripRouteEventGateway: FakeTripRouteEventGateway(),
            liveLocationGateway: const FakeTripLiveLocationGateway(null),
            liveLocationWebSocketService: FakeWebSocketService(),
            mapRenderMode: TripLiveMapRenderMode.placeholder,
          ),
        ),
      );

      await tester.pumpAndSettle();

      expect(find.text('Viagem não encontrada.'), findsOneWidget);
      expect(find.text('Parada 1'), findsOneWidget);
    },
  );

  testWidgets('conta do passageiro remove placeholders perifericos', (
    tester,
  ) async {
    final manha = samplePassengerTripRecord(
      id: 30,
      servicoNome: 'Linha Manha',
      tipoOperacao: 'IDA',
      horarioPartida: '07:00',
      passageiroNome: 'Ana Manha',
    );
    final tarde = samplePassengerTripRecord(
      id: 31,
      servicoNome: 'Linha Tarde',
      tipoOperacao: 'VOLTA',
      horarioPartida: '13:00',
      passageiroNome: 'Ana Tarde',
    );

    await tester.pumpWidget(
      MaterialApp(
        home: HomePassageiroScreen(
          nome: 'Ana',
          idPassageiro: 21,
          motoristaId: 7,
          email: 'ana@vanrides.dev',
          senha: 'Secret123',
          accessToken: 'token.passageiro',
          accountProfileGateway: const FakeAccountProfileGateway.passenger(),
          trackingGateway: FakePassengerTripTrackingGateway(
            manha,
            dayTrips: [manha, tarde],
          ),
          tripRouteEventGateway: FakeTripRouteEventGateway(),
          liveLocationGateway: const FakeTripLiveLocationGateway(null),
          liveLocationWebSocketService: FakeWebSocketService(),
          mapRenderMode: TripLiveMapRenderMode.placeholder,
        ),
      ),
    );

    await tester.pumpAndSettle();
    await tester.tap(find.byKey(MapFirstBottomNavItems.itemKey(2)));
    await tester.pumpAndSettle();

    expect(find.text('2 viagens hoje'), findsNothing);
    expect(find.text('Ana Perfil Real'), findsOneWidget);
    expect(find.text('ana.real@vanrides.dev'), findsWidgets);
    expect(find.text('E-mail verificado'), findsOneWidget);
    expect(find.text('20/05/2012'), findsOneWidget);
    await tester.drag(find.byType(ListView), const Offset(0, -320));
    await tester.pumpAndSettle();
    expect(find.text('51999990021'), findsOneWidget);
    expect(find.text('Feevale'), findsOneWidget);
    expect(find.text('Chats'), findsNothing);
    expect(find.text('Future Chat'), findsNothing);
    expect(find.text('Payment Methods'), findsNothing);
    expect(find.text('Trip History'), findsNothing);
    expect(find.text('Help Center'), findsNothing);
  });

  testWidgets('recarrega tracking quando recebe rota atualizada', (
    tester,
  ) async {
    final routeEvents = FakeTripRouteEventGateway();
    var fetchCount = 0;

    final gateway = ReloadingTripTrackingGateway(() {
      fetchCount += 1;
      if (fetchCount == 1) {
        return samplePassengerTripRecord(
          id: 30,
          servicoNome: 'Linha Manha',
          tipoOperacao: 'IDA',
          horarioPartida: '07:30',
          passageiroNome: 'Ana',
          status: TripTrackingStatus.inProgress,
        );
      }

      return samplePassengerTripRecord(
        id: 30,
        servicoNome: 'Linha Manha',
        tipoOperacao: 'IDA',
        horarioPartida: '07:30',
        passageiroNome: 'Ana',
        status: TripTrackingStatus.inProgress,
      );
    });

    await tester.pumpWidget(
      MaterialApp(
        home: HomePassageiroScreen(
          nome: 'Ana',
          idPassageiro: 21,
          motoristaId: 7,
          email: 'ana@vanrides.dev',
          senha: 'Secret123',
          accessToken: 'eyJhbGciOiJIUzUxMiJ9.eyJjb21wYW55SWQiOjF9.sig',
          trackingGateway: gateway,
          tripRouteEventGateway: routeEvents,
          liveLocationGateway: const FakeTripLiveLocationGateway(null),
          liveLocationWebSocketService: FakeWebSocketService(),
          mapRenderMode: TripLiveMapRenderMode.placeholder,
        ),
      ),
    );

    await tester.pumpAndSettle();
    expect(find.textContaining('Motorista Portal'), findsNothing);

    routeEvents.emit(
      TripRouteEvent(
        tipo: 'ROTA_ATUALIZADA',
        viagemId: 30,
        motoristaId: 7,
        dataOperacao: DateTime(2026, 3, 12),
        status: TripTrackingStatus.inProgress,
        rotaVersao: 2,
        totalParadas: 1,
        paradasPendentes: 1,
        paradasConcluidas: 0,
        proximaParadaId: 300,
        proximaParadaOrdem: 1,
        proximoPassageiroId: 21,
        proximoPassageiroNome: 'Ana',
        proximaParadaStatus: TripTrackingStopStatus.pending,
        etaMinutosAteProximaParada: 12,
        etaAtualizadoEm: DateTime(2026, 3, 18, 23, 44),
        latitudeMotorista: null,
        longitudeMotorista: null,
        localizacaoAtualizadaEm: null,
        atualizadoEm: DateTime(2026, 3, 18, 23, 44),
      ),
    );

    await tester.pump();
    await tester.pumpAndSettle();

    expect(fetchCount, greaterThan(1));
    expect(find.text('Aguardando posição da van'), findsWidgets);
    expect(find.text('Atualizar sinal'), findsWidgets);
    expect(find.text('Sem sinal da van'), findsNothing);
  });

  testWidgets('mostra aviso operacional para o passageiro da proxima parada', (
    tester,
  ) async {
    final routeEvents = FakeTripRouteEventGateway();
    final trip = samplePassengerTripRecord(
      id: 30,
      servicoNome: 'Linha Manha',
      tipoOperacao: 'IDA',
      horarioPartida: '07:30',
      passageiroNome: 'Ana',
      status: TripTrackingStatus.inProgress,
    );

    await tester.pumpWidget(
      MaterialApp(
        home: HomePassageiroScreen(
          nome: 'Ana',
          idPassageiro: 21,
          motoristaId: 7,
          email: 'ana@vanrides.dev',
          senha: 'Secret123',
          accessToken: 'eyJhbGciOiJIUzUxMiJ9.eyJjb21wYW55SWQiOjF9.sig',
          trackingGateway: FakePassengerTripTrackingGateway(trip),
          tripRouteEventGateway: routeEvents,
          liveLocationGateway: const FakeTripLiveLocationGateway(null),
          liveLocationWebSocketService: FakeWebSocketService(),
          mapRenderMode: TripLiveMapRenderMode.placeholder,
        ),
      ),
    );

    await tester.pumpAndSettle();

    routeEvents.emit(
      TripRouteEvent(
        tipo: 'VIAGEM_ATUALIZADA',
        viagemId: 30,
        motoristaId: 7,
        dataOperacao: DateTime(2026, 3, 12),
        status: TripTrackingStatus.inProgress,
        rotaVersao: 2,
        totalParadas: 1,
        paradasPendentes: 1,
        paradasConcluidas: 0,
        proximaParadaId: 300,
        proximaParadaOrdem: 1,
        proximoPassageiroId: 21,
        proximoPassageiroNome: 'Ana',
        proximaParadaStatus: TripTrackingStopStatus.pending,
        etaMinutosAteProximaParada: 2,
        etaAtualizadoEm: DateTime(2026, 3, 18, 23, 44),
        latitudeMotorista: -29.7,
        longitudeMotorista: -51.1,
        localizacaoAtualizadaEm: DateTime(2026, 3, 18, 23, 44),
        atualizadoEm: DateTime(2026, 3, 18, 23, 44),
        avisosOperacionais: const [
          TripOperationalAlertRecord(
            tipo: TripOperationalAlertType.driverApproaching,
            severidade: TripOperationalAlertSeverity.urgent,
            titulo: 'Van chegando',
            mensagem: 'Você é o próximo. Fique pronto para embarcar.',
            passageiroId: 21,
            paradaId: 300,
            paradaOrdem: 1,
          ),
        ],
      ),
    );

    await tester.pump();
    await tester.pumpAndSettle();

    expect(find.text('Van chegando'), findsWidgets);
    expect(
      find.text('Você é o próximo. Fique pronto para embarcar.'),
      findsOneWidget,
    );
  });
}
