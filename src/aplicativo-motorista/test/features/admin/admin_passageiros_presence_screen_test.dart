import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/admin/data/admin_passageiro_portal_service.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_motorista_record.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_passageiro_create_input.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_passageiro_record.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_passageiro_servico_record.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_passageiro_update_input.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_portal_session.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_servico_recorrente_record.dart';
import 'package:vanrides_frontend_dev/features/admin/presentation/admin_passageiros_screen.dart';
import 'package:vanrides_frontend_dev/features/admin/presentation/admin_viagens_review_intent.dart';
import 'package:vanrides_frontend_dev/features/service_presence/domain/passenger_available_service_record.dart';

import 'fakes/fake_passenger_onboarding_gateway.dart';

void main() {
  const session = AdminPortalSession(
    adminId: 1,
    adminName: 'Admin Portal',
    companyId: 1,
    companyName: 'Portal Empresa',
    companySlug: 'vanrides-default',
    email: 'admin@vanrides.dev',
    accessToken: 'token.admin',
  );

  testWidgets('exibe presenca de hoje na ficha do passageiro', (tester) async {
    await tester.binding.setSurfaceSize(const Size(1400, 1450));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final gateway = _PresenceGateway();
    AdminViagensReviewIntent? reviewIntent;

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: AdminPassageirosScreen(
            session: session,
            gateway: gateway,
            onboardingGateway: FakePassengerOnboardingGateway(),
            onOpenViagensReview: (intent) => reviewIntent = intent,
          ),
        ),
      ),
    );

    await tester.pumpAndSettle();

    expect(gateway.fetchedPresencePassageiroId, 1);
    expect(find.text('Presença de hoje'), findsOneWidget);
    expect(find.text('1 vou'), findsOneWidget);
    expect(find.text('1 sem decisão'), findsOneWidget);
    expect(find.text('07:30 • Ida • Vou'), findsOneWidget);

    await tester.ensureVisible(find.text('Ver viagens de hoje'));
    await tester.tap(find.text('Ver viagens de hoje'));
    await tester.pump();

    expect(reviewIntent?.sourceLabel, 'PASSAGEIRO');
    expect(reviewIntent?.title, 'Revise as viagens de Aluno Portal');
  });

  testWidgets('presenca vazia orienta revisar viagens e servicos', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1400, 1450));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: AdminPassageirosScreen(
            session: session,
            gateway: _PresenceGateway(services: const []),
            onboardingGateway: FakePassengerOnboardingGateway(),
            onOpenViagensReview: (_) {},
          ),
        ),
      ),
    );

    await tester.pumpAndSettle();

    expect(find.text('Presença de hoje'), findsOneWidget);
    expect(find.text('Sem serviços hoje'), findsOneWidget);
    expect(
      find.text(
        'Revise as viagens de hoje ou os serviços liberados antes de montar a lista.',
      ),
      findsOneWidget,
    );
  });
}

class _PresenceGateway implements AdminPassageiroGateway {
  _PresenceGateway({this.services});

  final List<PassengerAvailableServiceRecord>? services;
  int? fetchedPresencePassageiroId;

  @override
  Future<List<PassengerAvailableServiceRecord>> fetchAvailableServices({
    required int passageiroId,
    required DateTime data,
  }) async {
    fetchedPresencePassageiroId = passageiroId;
    return services ?? _presenceServices;
  }

  @override
  Future<AdminPassageiroRecord> fetchPassageiroDetail(int passageiroId) async =>
      _passenger;

  @override
  Future<List<AdminPassageiroRecord>> fetchPassageiros() async => const [
    _passenger,
  ];

  @override
  Future<List<AdminMotoristaRecord>> fetchMotoristas() async => const [];

  @override
  Future<List<AdminServicoRecorrenteRecord>> fetchServicos() async => const [];

  @override
  Future<AdminPassageiroRecord> createPassageiro(
    AdminPassageiroCreateInput input,
  ) async => _passenger;

  @override
  Future<AdminPassageiroRecord> updatePassageiro(
    int passageiroId,
    AdminPassageiroUpdateInput input,
  ) async => _passenger;

  @override
  Future<AdminPassageiroRecord> reassignMotorista(
    int passageiroId,
    int motoristaId,
  ) async => _passenger;

  @override
  Future<List<AdminPassageiroServicoRecord>> updatePassageiroServicos({
    required int passageiroId,
    required List<int> servicoIds,
  }) async => const [];
}

const _passenger = AdminPassageiroRecord(
  id: 1,
  nome: 'Aluno Portal',
  email: 'aluno@vanrides.dev',
  telefone: '51999999998',
  endereco: 'Rua A',
  bairro: 'Centro',
  cidade: 'Porto Alegre',
  estado: 'RS',
  cep: '90000-000',
  menorDeIdade: true,
  motoristaId: 7,
  motoristaNome: 'Motorista Portal',
);

final _presenceServices = [
  PassengerAvailableServiceRecord(
    servicoId: 10,
    nome: 'Ida manhã',
    tipoOperacao: 'IDA',
    dataOperacao: DateTime(2026, 6, 18),
    horarioPartida: '07:30',
    politicaPresenca: 'INCLUIR_POR_PADRAO',
    presencaId: 91,
    presencaStatus: 'CONFIRMED',
  ),
  PassengerAvailableServiceRecord(
    servicoId: 11,
    nome: 'Volta tarde',
    tipoOperacao: 'VOLTA',
    dataOperacao: DateTime(2026, 6, 18),
    horarioPartida: '17:40',
    politicaPresenca: 'EXIGE_CONFIRMACAO',
    presencaStatus: 'UNKNOWN',
  ),
];
