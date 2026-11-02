import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/app/session/app_user_role.dart';
import 'package:vanrides_frontend_dev/app/session/app_session_user.dart';
import 'package:vanrides_frontend_dev/features/deep_linking/data/deep_link_resolver.dart';
import 'package:vanrides_frontend_dev/features/deep_linking/domain/deep_link_models.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/data/driver_trip_service.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/domain/driver_trip_record.dart';
import 'package:vanrides_frontend_dev/features/guardian/data/guardian_localizacao_service.dart';
import 'package:vanrides_frontend_dev/features/guardian/data/guardian_passageiro_service.dart';
import 'package:vanrides_frontend_dev/features/guardian/domain/guardian_linked_passageiro_record.dart';
import 'package:vanrides_frontend_dev/features/guardian/domain/guardian_localizacao_snapshot.dart';
import 'package:vanrides_frontend_dev/features/guardian/domain/guardian_session.dart';
import 'package:vanrides_frontend_dev/features/guardian/presentation/guardian_localizacao_screen.dart';
import 'package:vanrides_frontend_dev/features/tracking/data/trip_tracking_service.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_route_geometry_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/passenger_trip_details_screen.dart';
import 'package:vanrides_frontend_dev/features/deep_linking/presentation/deep_link_navigation_handler.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/presentation/home_motorista_screen.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/home_passageiro_screen.dart';
import 'package:vanrides_frontend_dev/features/auth/presentation/login_screen.dart';

import '../../support/fakes/fake_location_gateway.dart';
import '../../support/fakes/fake_trip_route_event_gateway.dart';
import '../../support/fakes/fake_websocket_service.dart';

class _FakeDriverTripGateway implements DriverTripGateway {
  @override
  Future<DriverTripRecord?> fetchTodayTrip(int motoristaId) async => null;

  @override
  Future<List<DriverTripRecord>> fetchDayTrips(int motoristaId) async =>
      const [];

  @override
  Future<DriverTripRecord> reorderTrip(int tripId, List<int> stopIds) {
    throw UnimplementedError();
  }

  @override
  Future<DriverTripRecord> startTrip(int tripId) {
    throw UnimplementedError();
  }

  @override
  Future<DriverTripRecord> completeStop(int tripId, int stopId) {
    throw UnimplementedError();
  }

  @override
  Future<DriverTripRecord> skipStop(int tripId, int stopId) {
    throw UnimplementedError();
  }

  @override
  Future<DriverTripRecord> noShowStop(int tripId, int stopId) {
    throw UnimplementedError();
  }

  @override
  Future<DriverTripRecord> endTrip(int tripId) {
    throw UnimplementedError();
  }
}

class _FakePassengerTripTrackingGateway implements TripTrackingGateway {
  @override
  Future<TripTrackingRecord?> fetchCurrentTrip(int passageiroId) async {
    return _sampleTrip();
  }

  @override
  Future<List<TripTrackingRecord>> fetchDayTrips(int passageiroId) async {
    return [_sampleTrip()];
  }
}

class _FakeGuardianPassageiroGateway implements GuardianPassageiroGateway {
  @override
  Future<List<GuardianLinkedPassageiroRecord>> fetchLinkedPassageiros() async {
    return const [
      GuardianLinkedPassageiroRecord(
        id: 21,
        nome: 'Ana Passageira',
        email: 'ana@test.dev',
        telefone: '51999990000',
        endereco: 'Rua A',
        bairro: 'Centro',
        cidade: 'Porto Alegre',
        estado: 'RS',
        cep: '90000-000',
        menorDeIdade: true,
        motoristaId: 7,
        motoristaNome: 'Motorista QA',
      ),
    ];
  }
}

class _FakeGuardianLocalizacaoGateway implements GuardianLocalizacaoGateway {
  @override
  Future<List<GuardianLocalizacaoSnapshot>> fetchHistoricoPorMotorista(
    int motoristaId,
  ) async {
    return const [];
  }
}

const _driverSession = AppSessionUser(
  userId: 7,
  displayName: 'Motorista QA',
  email: 'motorista@test.dev',
  password: 'senha123',
  role: AppUserRole.driver,
  backendRoleLabel: 'Motorista',
  accessToken: 'token.driver',
);

const _passengerSession = AppSessionUser(
  userId: 21,
  displayName: 'Ana Passageira',
  email: 'ana@test.dev',
  password: 'senha123',
  role: AppUserRole.passenger,
  backendRoleLabel: 'Passageiro',
  motoristaId: 7,
  accessToken: 'token.passenger',
);

const _passengerSessionWithoutDriver = AppSessionUser(
  userId: 21,
  displayName: 'Ana Passageira',
  email: 'ana@test.dev',
  password: 'senha123',
  role: AppUserRole.passenger,
  backendRoleLabel: 'Passageiro',
  accessToken: 'token.passenger',
);

const _guardianSession = AppSessionUser(
  userId: 77,
  displayName: 'Responsavel QA',
  email: 'responsavel@test.dev',
  password: 'senha123',
  role: AppUserRole.guardian,
  backendRoleLabel: 'Responsavel',
  companyId: 88,
  accessToken: 'token.guardian',
);

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
    rotaGeografica: const TripRouteGeometryRecord(
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
    ),
  );
}

void main() {
  Widget buildApp(GlobalKey<NavigatorState> navigatorKey) {
    return MaterialApp(
      navigatorKey: navigatorKey,
      home: const Scaffold(body: SizedBox.shrink()),
    );
  }

  testWidgets('driver + viagem empurra HomeMotoristaScreen', (tester) async {
    final navigatorKey = GlobalKey<NavigatorState>();
    final handler = DeepLinkNavigationHandler(
      navigatorKey: navigatorKey,
      currentSession: () => _driverSession,
      driverViagemScreenBuilder: (session, target) {
        return HomeMotoristaScreen(
          nome: session.displayName,
          motoristaId: session.userId,
          email: session.email,
          senha: session.password,
          accessToken: session.accessToken,
          tripGateway: _FakeDriverTripGateway(),
          tripRouteEventGateway: FakeTripRouteEventGateway(),
          locationGateway: FakeLocationGateway(),
          locationSharingWebSocketService: FakeWebSocketService(),
          liveLocationWebSocketService: FakeWebSocketService(),
        );
      },
    );

    await tester.pumpWidget(buildApp(navigatorKey));

    handler.handle(const DeepLinkAllowed(ViagemDeepLink(tripId: 42)));
    await tester.pumpAndSettle();

    expect(find.byType(HomeMotoristaScreen), findsOneWidget);
  });

  testWidgets('passenger + viagem nao exige motorista fixo na sessao', (
    tester,
  ) async {
    final navigatorKey = GlobalKey<NavigatorState>();
    final handler = DeepLinkNavigationHandler(
      navigatorKey: navigatorKey,
      currentSession: () => _passengerSessionWithoutDriver,
      passengerViagemScreenBuilder: (session, target) {
        return HomePassageiroScreen(
          nome: session.displayName,
          idPassageiro: session.userId,
          motoristaId: session.motoristaId,
          email: session.email,
          senha: session.password,
          accessToken: session.accessToken,
          initialTripId: target.tripId,
          trackingGateway: _FakePassengerTripTrackingGateway(),
        );
      },
    );

    await tester.pumpWidget(buildApp(navigatorKey));

    handler.handle(const DeepLinkAllowed(ViagemDeepLink(tripId: 42)));
    await tester.pump();
    await tester.pumpAndSettle();

    expect(find.byType(HomePassageiroScreen), findsOneWidget);
    final screen = tester.widget<HomePassageiroScreen>(
      find.byType(HomePassageiroScreen),
    );
    expect(screen.motoristaId, isNull);
    expect(screen.initialTripId, 42);
  });

  testWidgets('passenger + rastreio empurra PassengerTripDetailsScreen', (
    tester,
  ) async {
    final navigatorKey = GlobalKey<NavigatorState>();
    final handler = DeepLinkNavigationHandler(
      navigatorKey: navigatorKey,
      currentSession: () => _passengerSession,
      passengerRastreioScreenBuilder: (session, target) {
        return PassengerTripDetailsScreen.fromSession(
          passageiroId: target.passageiroId,
          email: session.email,
          senha: session.password,
          accessToken: session.accessToken,
          trackingGateway: _FakePassengerTripTrackingGateway(),
        );
      },
    );

    await tester.pumpWidget(buildApp(navigatorKey));

    handler.handle(const DeepLinkAllowed(RastreioDeepLink(passageiroId: 21)));
    await tester.pump();
    await tester.pumpAndSettle();

    expect(find.byType(PassengerTripDetailsScreen), findsOneWidget);
    expect(find.text('Detalhes da viagem'), findsOneWidget);
  });

  testWidgets(
    'guardian + viagem empurra GuardianLocalizacaoScreen com viagem em foco',
    (tester) async {
      final navigatorKey = GlobalKey<NavigatorState>();
      final handler = DeepLinkNavigationHandler(
        navigatorKey: navigatorKey,
        currentSession: () => _guardianSession,
        guardianViagemScreenBuilder: (session, target) {
          return Scaffold(
            body: GuardianLocalizacaoScreen(
              session: GuardianSession(
                guardianId: session.userId,
                guardianName: session.displayName,
                companyId: session.companyId!,
                email: session.email,
                accessToken: session.accessToken!,
              ),
              initialTripId: target.tripId,
              passageiroGateway: _FakeGuardianPassageiroGateway(),
              trackingGateway: _FakePassengerTripTrackingGateway(),
              localizacaoGateway: _FakeGuardianLocalizacaoGateway(),
              tripRouteEventGateway: FakeTripRouteEventGateway(),
              webSocketService: FakeWebSocketService(),
            ),
          );
        },
      );

      await tester.pumpWidget(buildApp(navigatorKey));

      handler.handle(const DeepLinkAllowed(ViagemDeepLink(tripId: 42)));
      await tester.pump();
      await tester.pumpAndSettle();

      final screenFinder = find.byType(GuardianLocalizacaoScreen);
      expect(screenFinder, findsOneWidget);
      final screen = tester.widget<GuardianLocalizacaoScreen>(screenFinder);
      expect(screen.initialTripId, 42);
    },
  );

  testWidgets('convite anônimo cai no login com código', (tester) async {
    final navigatorKey = GlobalKey<NavigatorState>();
    final handler = DeepLinkNavigationHandler(
      navigatorKey: navigatorKey,
      currentSession: () => null,
    );

    await tester.pumpWidget(buildApp(navigatorKey));

    handler.handle(
      const DeepLinkAllowed(ConviteDeepLink(codigo: 'INV-2026-ABCD')),
    );
    await tester.pump();
    await tester.pumpAndSettle();

    expect(find.byType(LoginScreen), findsOneWidget);
    expect(find.textContaining('INV-2026-ABCD'), findsOneWidget);
  });

  testWidgets('driver invite anônimo abre tela dedicada de motorista', (
    tester,
  ) async {
    final navigatorKey = GlobalKey<NavigatorState>();
    final handler = DeepLinkNavigationHandler(
      navigatorKey: navigatorKey,
      currentSession: () => null,
      driverInviteScreenBuilder: (session, target) {
        return Scaffold(body: Text('driver-token:${target.token}'));
      },
    );

    await tester.pumpWidget(buildApp(navigatorKey));

    handler.handle(
      const DeepLinkAllowed(DriverInviteDeepLink(token: 'driver-token-123')),
    );
    await tester.pump();
    await tester.pumpAndSettle();

    expect(find.text('driver-token:driver-token-123'), findsOneWidget);
  });

  testWidgets('handler unsupported exibe snackbar de link desconhecido', (
    tester,
  ) async {
    final navigatorKey = GlobalKey<NavigatorState>();
    final handler = DeepLinkNavigationHandler(
      navigatorKey: navigatorKey,
      currentSession: () => null,
    );

    await tester.pumpWidget(buildApp(navigatorKey));

    handler.handle(
      const DeepLinkUnsupported(UnknownDeepLink(rawUri: 'vanrides://foo')),
    );
    await tester.pumpAndSettle();

    expect(find.text('Link desconhecido.'), findsOneWidget);
  });

  testWidgets('handler role mismatch exibe snackbar de perfil incorreto', (
    tester,
  ) async {
    final navigatorKey = GlobalKey<NavigatorState>();
    final handler = DeepLinkNavigationHandler(
      navigatorKey: navigatorKey,
      currentSession: () => null,
    );

    await tester.pumpWidget(buildApp(navigatorKey));

    handler.handle(
      const DeepLinkRoleMismatch(
        target: ViagemDeepLink(tripId: 42),
        userRole: AppUserRole.unknown,
      ),
    );
    await tester.pumpAndSettle();

    expect(
      find.text('Este link não está disponível para o seu perfil.'),
      findsOneWidget,
    );
  });
}
