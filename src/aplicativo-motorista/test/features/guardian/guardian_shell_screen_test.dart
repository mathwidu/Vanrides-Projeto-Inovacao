import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:vanrides_frontend_dev/features/account/data/account_profile_service.dart';
import 'package:vanrides_frontend_dev/features/account/domain/account_profile.dart';
import 'package:vanrides_frontend_dev/features/guardian/data/guardian_localizacao_service.dart';
import 'package:vanrides_frontend_dev/features/guardian/data/guardian_presenca_service.dart';
import 'package:vanrides_frontend_dev/features/guardian/data/guardian_passageiro_service.dart';
import 'package:vanrides_frontend_dev/features/guardian/domain/guardian_linked_passageiro_record.dart';
import 'package:vanrides_frontend_dev/features/guardian/domain/guardian_localizacao_snapshot.dart';
import 'package:vanrides_frontend_dev/features/guardian/domain/guardian_presenca_record.dart';
import 'package:vanrides_frontend_dev/features/guardian/domain/guardian_session.dart';
import 'package:vanrides_frontend_dev/features/guardian/presentation/guardian_localizacao_screen.dart';
import 'package:vanrides_frontend_dev/features/guardian/presentation/guardian_shell_screen.dart';
import 'package:vanrides_frontend_dev/features/tracking/data/trip_tracking_service.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_route_geometry_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/widgets/vanrides_map_tokens.dart';
import 'package:vanrides_frontend_dev/features/auth/presentation/login_screen.dart';
import 'package:vanrides_frontend_dev/l10n/app_strings.dart';
import 'package:vanrides_frontend_dev/services/websocket_service.dart';

import '../../support/fakes/fake_account_profile_gateway.dart';
import '../../support/fakes/fake_trip_route_event_gateway.dart';

class _FakeGuardianGateway implements GuardianPassageiroGateway {
  const _FakeGuardianGateway(this.records);

  final List<GuardianLinkedPassageiroRecord> records;

  @override
  Future<List<GuardianLinkedPassageiroRecord>> fetchLinkedPassageiros() async {
    await Future<void>.delayed(Duration.zero);
    return records;
  }
}

class _FakeGuardianPresencaGateway implements GuardianPresencaGateway {
  const _FakeGuardianPresencaGateway(this.records);

  final List<GuardianPresencaRecord> records;

  @override
  Future<void> confirmarPresenca({
    required int passageiroId,
    required DateTime dataPresenca,
  }) async {}

  @override
  Future<List<GuardianPresencaRecord>> fetchPresencasByPassageiro(
    int passageiroId,
  ) async {
    await Future<void>.delayed(Duration.zero);
    return records;
  }

  @override
  Future<void> removerPresenca(int presencaId) async {}
}

class _FakeGuardianLocalizacaoGateway implements GuardianLocalizacaoGateway {
  const _FakeGuardianLocalizacaoGateway(this.records);

  final List<GuardianLocalizacaoSnapshot> records;

  @override
  Future<List<GuardianLocalizacaoSnapshot>> fetchHistoricoPorMotorista(
    int motoristaId,
  ) async {
    await Future<void>.delayed(Duration.zero);
    return records;
  }
}

class _FakeGuardianWebSocketService implements WebSocketServiceBase {
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

class _FakeGuardianTripTrackingGateway implements TripTrackingGateway {
  const _FakeGuardianTripTrackingGateway(this.record);

  final TripTrackingRecord? record;

  @override
  Future<TripTrackingRecord?> fetchCurrentTrip(int passageiroId) async {
    await Future<void>.delayed(Duration.zero);
    return record;
  }

  @override
  Future<List<TripTrackingRecord>> fetchDayTrips(int passageiroId) async {
    await Future<void>.delayed(Duration.zero);
    return record == null ? const [] : [record!];
  }
}

class _FailingAccountProfileGateway implements AccountProfileGateway {
  const _FailingAccountProfileGateway(this.message);

  final String message;

  @override
  Future<AccountProfile> fetchMe() async {
    await Future<void>.delayed(Duration.zero);
    throw AccountProfileException(message);
  }

  @override
  Future<AccountProfile> updateMe({
    required String nome,
    required String telefone,
    String? dataNascimento,
  }) async {
    throw AccountProfileException(message);
  }
}

const _guardianShellRouteGeometry = TripRouteGeometryRecord(
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
      paradaId: 300,
      passageiroId: 10,
      nome: 'Ana',
      latitude: -30.1,
      longitude: -51.2,
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

void main() {
  setUpAll(() async {
    await initializeDateFormatting('pt_BR');
  });

  testWidgets('renderiza shell do responsavel com destinos principais', (
    tester,
  ) async {
    const session = GuardianSession(
      guardianId: 7,
      guardianName: 'Mae da Ana',
      companyId: 1,
      email: 'mae@vanrides.dev',
      accessToken: 'token.guardian',
    );

    await tester.pumpWidget(
      MaterialApp(
        home: GuardianShellScreen(
          session: session,
          homeGateway: const _FakeGuardianGateway([
            GuardianLinkedPassageiroRecord(
              id: 10,
              nome: 'Ana',
              email: 'ana@vanrides.dev',
              telefone: '51999990000',
              endereco: 'Rua A',
              bairro: 'Centro',
              cidade: 'Porto Alegre',
              estado: 'RS',
              cep: '90000-000',
              menorDeIdade: true,
              motoristaId: 3,
              motoristaNome: 'Motorista Portal',
            ),
          ]),
          presencaGateway: _FakeGuardianPresencaGateway([
            GuardianPresencaRecord(
              id: 91,
              passageiroId: 10,
              dataPresenca: DateTime(2026, 3, 12),
              confirmado: true,
            ),
          ]),
        ),
      ),
    );

    await tester.pumpAndSettle();

    expect(find.textContaining('Bem-vindo, Mae da Ana'), findsOneWidget);
    expect(find.text(AppStrings.guardian.inicio), findsOneWidget);
    expect(find.text(AppStrings.guardian.presencas), findsOneWidget);
    expect(find.text(AppStrings.guardian.localizacao), findsOneWidget);
    expect(find.text(AppStrings.guardian.conta), findsOneWidget);
  });

  testWidgets('permite navegar para a aba de conta', (tester) async {
    const session = GuardianSession(
      guardianId: 7,
      guardianName: 'Mae da Ana',
      companyId: 1,
      email: 'mae@vanrides.dev',
      accessToken: 'token.guardian',
      backendRoleLabel: 'Responsavel',
    );

    await tester.pumpWidget(
      const MaterialApp(
        home: GuardianShellScreen(
          session: session,
          homeGateway: _FakeGuardianGateway([]),
        ),
      ),
    );

    await tester.pumpAndSettle();

    await tester.tap(find.text(AppStrings.guardian.conta));
    await tester.pumpAndSettle();

    await tester.scrollUntilVisible(find.text('Passageiros vinculados'), 220);
    await tester.pumpAndSettle();

    expect(find.text('Passageiros vinculados'), findsOneWidget);
    expect(find.text('Perfil backend'), findsNothing);
    expect(find.text('Nenhum passageiro vinculado'), findsOneWidget);

    final root = tester.widget<Container>(
      find.byKey(const Key('guardian.account.darkSurface')),
    );
    expect(root.color, VanRidesMapTokens.backgroundDeep);

    await tester.scrollUntilVisible(
      find.byKey(GuardianShellScreen.logoutButtonKey),
      220,
    );
    await tester.pumpAndSettle();

    final logout = tester.widget<FilledButton>(
      find.byKey(GuardianShellScreen.logoutButtonKey),
    );
    final background = logout.style?.backgroundColor?.resolve(<WidgetState>{});
    final foreground = logout.style?.foregroundColor?.resolve(<WidgetState>{});
    expect(background, isNot(Colors.white));
    expect(foreground, VanRidesMapTokens.textPrimary);
  });

  testWidgets('aba de conta usa perfil real do responsavel quando disponivel', (
    tester,
  ) async {
    const session = GuardianSession(
      guardianId: 7,
      guardianName: 'Mae da Ana',
      companyId: 1,
      email: 'mae@vanrides.dev',
      accessToken: 'token.guardian',
      backendRoleLabel: 'Responsavel',
    );

    await tester.pumpWidget(
      const MaterialApp(
        home: GuardianShellScreen(
          session: session,
          homeGateway: _FakeGuardianGateway([]),
          accountProfileGateway: FakeAccountProfileGateway.guardian(),
        ),
      ),
    );

    await tester.pumpAndSettle();

    await tester.tap(find.text(AppStrings.guardian.conta));
    await tester.pumpAndSettle();

    expect(find.text('Marina Perfil Real'), findsOneWidget);
    expect(find.text('marina.real@vanrides.dev'), findsWidgets);
    expect(find.text('Responsável'), findsWidgets);
    expect(find.text('E-mail verificado'), findsOneWidget);
    expect(find.text('12/04/1988'), findsOneWidget);
    expect(
      find.byKey(const Key('account.profile.avatarImage')),
      findsOneWidget,
    );
    expect(
      find.byKey(const Key('guardian.account.editProfileButton')),
      findsOneWidget,
    );
    expect(find.text('51999990077'), findsOneWidget);

    await tester.drag(find.byType(Scrollable).last, const Offset(0, -520));
    await tester.pumpAndSettle();

    expect(find.text('Feevale'), findsWidgets);
  });

  testWidgets('aba de conta mantém sessão quando perfil real falha', (
    tester,
  ) async {
    const session = GuardianSession(
      guardianId: 7,
      guardianName: 'Mae da Ana',
      companyId: 1,
      email: 'mae@vanrides.dev',
      accessToken: 'token.guardian',
      backendRoleLabel: 'Responsavel',
    );

    await tester.pumpWidget(
      const MaterialApp(
        home: GuardianShellScreen(
          session: session,
          homeGateway: _FakeGuardianGateway([]),
          accountProfileGateway: _FailingAccountProfileGateway(
            'Token expirado',
          ),
        ),
      ),
    );

    await tester.pumpAndSettle();

    await tester.tap(find.text(AppStrings.guardian.conta));
    await tester.pumpAndSettle();

    expect(find.text('Mae da Ana'), findsOneWidget);
    expect(find.text('mae@vanrides.dev'), findsWidgets);
    expect(find.text('Token expirado'), findsOneWidget);
    expect(find.text('Dados do login em uso'), findsNothing);
  });

  testWidgets('abre a aba de presencas real do responsavel', (tester) async {
    const session = GuardianSession(
      guardianId: 7,
      guardianName: 'Mae da Ana',
      companyId: 1,
      email: 'mae@vanrides.dev',
      accessToken: 'token.guardian',
    );

    await tester.pumpWidget(
      MaterialApp(
        home: GuardianShellScreen(
          session: session,
          homeGateway: const _FakeGuardianGateway([
            GuardianLinkedPassageiroRecord(
              id: 10,
              nome: 'Ana',
              email: 'ana@vanrides.dev',
              telefone: '51999990000',
              endereco: 'Rua A',
              bairro: 'Centro',
              cidade: 'Porto Alegre',
              estado: 'RS',
              cep: '90000-000',
              menorDeIdade: true,
              motoristaId: 3,
              motoristaNome: 'Motorista Portal',
            ),
          ]),
          presencaGateway: _FakeGuardianPresencaGateway([
            GuardianPresencaRecord(
              id: 90,
              passageiroId: 10,
              dataPresenca: DateTime(2026, 3, 12),
              confirmado: true,
            ),
          ]),
        ),
      ),
    );

    await tester.pumpAndSettle();
    await tester.tap(find.text(AppStrings.guardian.presencas));
    await tester.pumpAndSettle();

    expect(
      find.text(AppStrings.guardian.presencasDoResponsavel),
      findsOneWidget,
    );
    expect(find.text(AppStrings.guardian.selecionarPassageiro), findsNothing);
    expect(find.text('Ana'), findsWidgets);
  });

  testWidgets('permite sair e voltar para o login', (tester) async {
    SharedPreferences.setMockInitialValues({
      'session.userId': 7,
      'session.displayName': 'Mae da Ana',
      'session.email': 'mae@vanrides.dev',
      'session.tipoUsuario': 'Responsavel',
      'session.accessToken': 'token.guardian',
      'session.refreshToken': 'refresh.guardian',
    });
    const session = GuardianSession(
      guardianId: 7,
      guardianName: 'Mae da Ana',
      companyId: 1,
      email: 'mae@vanrides.dev',
      accessToken: 'token.guardian',
    );

    await tester.pumpWidget(
      const MaterialApp(
        home: GuardianShellScreen(
          session: session,
          homeGateway: _FakeGuardianGateway([]),
          presencaGateway: _FakeGuardianPresencaGateway([]),
        ),
      ),
    );

    await tester.pumpAndSettle();
    await tester.tap(find.text(AppStrings.guardian.conta));
    await tester.pumpAndSettle();
    await tester.scrollUntilVisible(
      find.byKey(GuardianShellScreen.logoutButtonKey),
      220,
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(GuardianShellScreen.logoutButtonKey));
    await tester.pumpAndSettle();

    final prefs = await SharedPreferences.getInstance();
    expect(prefs.getString('session.accessToken'), isNull);
    expect(prefs.getString('session.refreshToken'), isNull);
    expect(prefs.getInt('session.userId'), isNull);
    expect(find.byType(LoginScreen), findsOneWidget);
    expect(find.text('Entrar'), findsOneWidget);
  });

  testWidgets('abre a aba de localizacao real do responsavel', (tester) async {
    const session = GuardianSession(
      guardianId: 7,
      guardianName: 'Mae da Ana',
      companyId: 1,
      email: 'mae@vanrides.dev',
      accessToken: 'token.guardian',
    );

    await tester.pumpWidget(
      MaterialApp(
        home: GuardianShellScreen(
          session: session,
          homeGateway: const _FakeGuardianGateway([
            GuardianLinkedPassageiroRecord(
              id: 10,
              nome: 'Ana',
              email: 'ana@vanrides.dev',
              telefone: '51999990000',
              endereco: 'Rua A',
              bairro: 'Centro',
              cidade: 'Porto Alegre',
              estado: 'RS',
              cep: '90000-000',
              menorDeIdade: true,
              motoristaId: 3,
              motoristaNome: 'Motorista Portal',
            ),
          ]),
          localizacaoGateway: _FakeGuardianLocalizacaoGateway([
            GuardianLocalizacaoSnapshot(
              latitude: -30.1,
              longitude: -51.2,
              dataHora: DateTime.now(),
            ),
          ]),
          trackingGateway: _FakeGuardianTripTrackingGateway(
            TripTrackingRecord(
              id: 30,
              rotaNome: 'Rota Escolar Manha',
              dataOperacao: DateTime(2026, 3, 12),
              status: TripTrackingStatus.inProgress,
              rotaVersao: 2,
              etaMinutosAtePassageiro: 12,
              etaAtualizadoEm: DateTime(2026, 3, 18, 23, 38),
              motorista: const TripTrackingDriverRecord(
                id: 3,
                nome: 'Motorista Portal',
              ),
              paradaDoPassageiro: const TripTrackingPassengerStopRecord(
                id: 300,
                ordem: 1,
                status: TripTrackingStopStatus.pending,
                etaMinutos: 12,
                confirmadoPresenca: true,
                proximaParada: true,
                passageiroId: 10,
                passageiroNome: 'Ana',
                menorDeIdade: true,
              ),
              rotaGeografica: _guardianShellRouteGeometry,
            ),
          ),
          tripRouteEventGateway: FakeTripRouteEventGateway(),
          webSocketService: _FakeGuardianWebSocketService(),
        ),
      ),
    );

    await tester.pumpAndSettle();
    await tester.tap(find.text(AppStrings.guardian.localizacao));
    await tester.pumpAndSettle();

    expect(find.byType(GuardianLocalizacaoScreen), findsOneWidget);
  });
}
