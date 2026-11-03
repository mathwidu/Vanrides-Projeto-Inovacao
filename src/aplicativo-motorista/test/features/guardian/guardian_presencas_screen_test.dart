import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:vanrides_frontend_dev/features/guardian/data/guardian_passageiro_service.dart';
import 'package:vanrides_frontend_dev/features/guardian/data/guardian_presenca_service.dart';
import 'package:vanrides_frontend_dev/features/guardian/domain/guardian_linked_passageiro_record.dart';
import 'package:vanrides_frontend_dev/features/guardian/domain/guardian_presenca_record.dart';
import 'package:vanrides_frontend_dev/features/guardian/domain/guardian_session.dart';
import 'package:vanrides_frontend_dev/features/guardian/presentation/guardian_presencas_screen.dart';
import 'package:vanrides_frontend_dev/features/service_presence/data/service_presence_service.dart';
import 'package:vanrides_frontend_dev/features/service_presence/domain/passenger_available_service_record.dart';
import 'package:vanrides_frontend_dev/l10n/app_strings.dart';

class _FakeGuardianPassageiroGateway implements GuardianPassageiroGateway {
  const _FakeGuardianPassageiroGateway(this.records);

  final List<GuardianLinkedPassageiroRecord> records;

  @override
  Future<List<GuardianLinkedPassageiroRecord>> fetchLinkedPassageiros() async {
    await Future<void>.delayed(Duration.zero);
    return records;
  }
}

class _FakeGuardianPresencaGateway implements GuardianPresencaGateway {
  _FakeGuardianPresencaGateway(this.records);

  final List<GuardianPresencaRecord> records;
  int confirmCount = 0;
  int removeCount = 0;
  int fetchCount = 0;

  @override
  Future<void> confirmarPresenca({
    required int passageiroId,
    required DateTime dataPresenca,
  }) async {
    confirmCount++;
  }

  @override
  Future<List<GuardianPresencaRecord>> fetchPresencasByPassageiro(
    int passageiroId,
  ) async {
    fetchCount++;
    await Future<void>.delayed(Duration.zero);
    return records;
  }

  @override
  Future<void> removerPresenca(int presencaId) async {
    removeCount++;
  }
}

class _FakeServicePresenceGateway implements ServicePresenceGateway {
  _FakeServicePresenceGateway({List<PassengerAvailableServiceRecord>? services})
    : _services = services;

  final List<PassengerAvailableServiceRecord>? _services;
  String? lastStatus;
  bool absent = false;

  @override
  Future<void> clearPresence(int presencaId) async {
    absent = false;
  }

  @override
  Future<List<PassengerAvailableServiceRecord>> fetchAvailableServices({
    required int passageiroId,
    required DateTime data,
  }) async {
    final services = _services;
    if (services != null) {
      return services;
    }
    return [
      PassengerAvailableServiceRecord(
        servicoId: 31,
        nome: 'Linha Tarde',
        tipoOperacao: 'VOLTA',
        dataOperacao: DateTime(data.year, data.month, data.day),
        horarioPartida: '17:40',
        destinoFinalNome: 'Condomínio Portal',
        politicaPresenca: 'EXIGE_CONFIRMACAO',
        presencaId: absent ? 93 : null,
        presencaStatus: absent ? 'ABSENT' : 'UNKNOWN',
      ),
    ];
  }

  @override
  Future<void> setPresenceStatus({
    required PassengerAvailableServiceRecord service,
    required int passageiroId,
    required String status,
  }) async {
    lastStatus = status;
    absent = status == 'ABSENT';
  }
}

const _guardianSession = GuardianSession(
  guardianId: 7,
  guardianName: 'Mae da Ana',
  companyId: 1,
  email: 'mae@vanrides.dev',
  accessToken: 'token.guardian',
);

const _anaPassageiro = GuardianLinkedPassageiroRecord(
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
);

PassengerAvailableServiceRecord _serviceRecord({
  int id = 31,
  String nome = 'Linha Tarde',
  String status = 'UNKNOWN',
  int? presencaId,
}) {
  return PassengerAvailableServiceRecord(
    servicoId: id,
    nome: nome,
    tipoOperacao: 'VOLTA',
    dataOperacao: DateTime(2026, 7, 6),
    horarioPartida: '17:40',
    destinoFinalNome: 'Condomínio Portal',
    politicaPresenca: 'EXIGE_CONFIRMACAO',
    presencaId: presencaId,
    presencaStatus: status,
  );
}

void main() {
  setUpAll(() async {
    await initializeDateFormatting('pt_BR');
  });

  const session = GuardianSession(
    guardianId: 7,
    guardianName: 'Mae da Ana',
    companyId: 1,
    email: 'mae@vanrides.dev',
    accessToken: 'token.guardian',
  );

  Future<void> pumpServicePresenceScreen(
    WidgetTester tester, {
    required ServicePresenceGateway serviceGateway,
  }) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: GuardianPresencasScreen(
            session: _guardianSession,
            passageiroGateway: const _FakeGuardianPassageiroGateway([
              _anaPassageiro,
            ]),
            presencaGateway: _FakeGuardianPresencaGateway(const []),
            servicePresenceGateway: serviceGateway,
          ),
        ),
      ),
    );

    await tester.pumpAndSettle();
  }

  testWidgets('renderiza resumo e passageiro selecionado', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: GuardianPresencasScreen(
            session: session,
            passageiroGateway: const _FakeGuardianPassageiroGateway([
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
      ),
    );

    await tester.pumpAndSettle();

    expect(
      find.text(AppStrings.guardian.presencasDoResponsavel),
      findsOneWidget,
    );
    expect(find.text(AppStrings.guardian.selecionarPassageiro), findsNothing);
    expect(find.text('Ana'), findsWidgets);
    expect(find.text(AppStrings.guardian.comAcesso), findsOneWidget);
    expect(find.text(AppStrings.guardian.confirmadas), findsOneWidget);

    await tester.scrollUntilVisible(
      find.byKey(GuardianPresencasScreen.calendarKey),
      300,
      scrollable: find.byType(Scrollable).first,
    );
    await tester.pumpAndSettle();

    expect(find.byKey(GuardianPresencasScreen.calendarKey), findsOneWidget);
    expect(
      find.byKey(GuardianPresencasScreen.confirmarButtonKey),
      findsOneWidget,
    );
    expect(
      find.byKey(GuardianPresencasScreen.removerButtonKey),
      findsOneWidget,
    );
  });

  testWidgets('mostra estado vazio quando nao ha passageiros vinculados', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: GuardianPresencasScreen(
            session: session,
            passageiroGateway: const _FakeGuardianPassageiroGateway([]),
            presencaGateway: _FakeGuardianPresencaGateway(const []),
          ),
        ),
      ),
    );

    await tester.pumpAndSettle();

    expect(
      find.text(AppStrings.guardian.aindaNaoHaPassageiros),
      findsOneWidget,
    );
  });

  testWidgets('responsavel decide presença por serviço do passageiro', (
    tester,
  ) async {
    final serviceGateway = _FakeServicePresenceGateway();
    final legacyGateway = _FakeGuardianPresencaGateway(const []);

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: GuardianPresencasScreen(
            session: session,
            passageiroGateway: const _FakeGuardianPassageiroGateway([
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
              ),
            ]),
            presencaGateway: legacyGateway,
            servicePresenceGateway: serviceGateway,
          ),
        ),
      ),
    );

    await tester.pumpAndSettle();
    await tester.scrollUntilVisible(
      find.text('Linha Tarde'),
      300,
      scrollable: find.byType(Scrollable).first,
    );

    expect(find.text('Linha Tarde'), findsOneWidget);

    await tester.scrollUntilVisible(
      find.byKey(const ValueKey('service-presence.absent.31')),
      200,
      scrollable: find.byType(Scrollable).first,
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('service-presence.absent.31')));
    await tester.pumpAndSettle();

    expect(serviceGateway.lastStatus, 'ABSENT');
    expect(legacyGateway.fetchCount, 0);
    expect(find.text('Não vou'), findsWidgets);
  });

  testWidgets('presença por serviço mostra resumo acionavel por passageiro', (
    tester,
  ) async {
    await pumpServicePresenceScreen(
      tester,
      serviceGateway: _FakeServicePresenceGateway(),
    );

    expect(find.text('Ana precisa decidir 1 horário hoje'), findsOneWidget);
    expect(find.byKey(GuardianPresencasScreen.calendarKey), findsNothing);
    expect(
      find.byKey(const ValueKey('guardian.presencas.serviceArea')),
      findsOneWidget,
    );

    await tester.scrollUntilVisible(
      find.text('Linha Tarde'),
      240,
      scrollable: find.byType(Scrollable).first,
    );

    expect(find.text('Linha Tarde'), findsOneWidget);
    expect(find.text('Vou'), findsOneWidget);
    expect(find.text('Não vou'), findsOneWidget);
  });

  testWidgets('presença por serviço vazia mostra estado calmo da pessoa', (
    tester,
  ) async {
    await pumpServicePresenceScreen(
      tester,
      serviceGateway: _FakeServicePresenceGateway(services: const []),
    );

    expect(find.text('Nenhum horário para decidir hoje'), findsOneWidget);
    expect(
      find.text('Ana não tem horários liberados para hoje.'),
      findsOneWidget,
    );
    expect(find.byKey(GuardianPresencasScreen.calendarKey), findsNothing);
    expect(
      find.byKey(const ValueKey('guardian.presencas.serviceArea')),
      findsOneWidget,
    );
    expect(find.text('Atualizar horários'), findsOneWidget);
  });

  testWidgets('presença por serviço decidida confirma dia resolvido', (
    tester,
  ) async {
    await pumpServicePresenceScreen(
      tester,
      serviceGateway: _FakeServicePresenceGateway(
        services: [_serviceRecord(status: 'CONFIRMED', presencaId: 93)],
      ),
    );

    expect(find.text('Tudo decidido para Ana hoje'), findsOneWidget);

    await tester.scrollUntilVisible(
      find.byKey(const ValueKey('service-presence.clear.31')),
      240,
      scrollable: find.byType(Scrollable).first,
    );

    expect(find.text('Linha Tarde'), findsOneWidget);
    expect(find.text('Alterar'), findsOneWidget);
  });
}
