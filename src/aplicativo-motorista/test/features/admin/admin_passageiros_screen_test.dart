import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/admin/data/admin_passageiro_portal_service.dart';
import 'package:vanrides_frontend_dev/features/admin/data/admin_vinculo_portal_service.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_motorista_record.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_passageiro_create_input.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_passageiro_record.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_passageiro_servico_record.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_passageiro_update_input.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_portal_session.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_responsavel_record.dart';
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

  testWidgets('renderiza o estado real do portal de passageiros', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1400, 1200));

    await tester.pumpWidget(
      MaterialApp(
        home: AdminPassageirosScreen(
          session: session,
          gateway: _FakeAdminPassageiroGateway(),
          onboardingGateway: FakePassengerOnboardingGateway(),
        ),
      ),
    );

    await tester.pumpAndSettle();

    expect(find.text('Passageiros'), findsOneWidget);
    expect(find.text('Cadastrar Passageiro'), findsOneWidget);
    expect(find.text('Filtrar'), findsNothing);
    expect(find.text('DOCUMENTAÇÃO OK'), findsNothing);
    expect(find.text('COM SERVIÇO'), findsOneWidget);
    expect(find.text('SEM SERVIÇO'), findsOneWidget);
    expect(find.text('PERFIL COMPLETO'), findsOneWidget);
    expect(find.text('100%'), findsOneWidget);
    expect(find.text('Nenhuma ficha comum pendente'), findsOneWidget);
    expect(find.text('Lista de Passageiros'), findsOneWidget);
    expect(find.text('Ficha do passageiro'), findsOneWidget);
    expect(find.text('Atividades Recentes'), findsNothing);
    expect(find.text('Aluno Portal'), findsWidgets);
  });

  testWidgets('cadastra passageiro sem exigir motorista inicial', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1400, 1400));
    final gateway = _FakeAdminPassageiroGateway();

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: AdminPassageirosScreen(
            session: session,
            gateway: gateway,
            onboardingGateway: FakePassengerOnboardingGateway(),
          ),
        ),
      ),
    );

    await tester.pumpAndSettle();

    await tester.tap(find.text('Cadastrar Passageiro'));
    await tester.pumpAndSettle();

    expect(find.text('Definir pela viagem do dia'), findsOneWidget);
    expect(find.text('Serviços liberados no cadastro'), findsOneWidget);
    expect(find.text('07:30 Ida • Ida manhã'), findsOneWidget);
    expect(find.text('17:40 Volta • Volta tarde'), findsOneWidget);

    await tester.tap(find.text('07:30 Ida • Ida manhã'));
    await tester.tap(find.text('17:40 Volta • Volta tarde'));
    await tester.pumpAndSettle();

    await tester.enterText(
      find.widgetWithText(TextFormField, 'Nome completo'),
      'Aluno Sem Motorista',
    );
    await tester.enterText(
      find.widgetWithText(TextFormField, 'E-mail'),
      'sem.motorista@vanrides.dev',
    );
    await tester.enterText(
      find.widgetWithText(TextFormField, 'Telefone'),
      '51999999997',
    );
    await tester.enterText(
      find.widgetWithText(TextFormField, 'Data de nascimento'),
      '12/08/1998',
    );
    await tester.enterText(
      find.widgetWithText(TextFormField, 'Endereço'),
      'Rua B',
    );
    await tester.enterText(
      find.widgetWithText(TextFormField, 'Bairro'),
      'Centro',
    );
    await tester.enterText(
      find.widgetWithText(TextFormField, 'Cidade'),
      'Porto Alegre',
    );
    await tester.enterText(find.widgetWithText(TextFormField, 'Estado'), 'RS');
    await tester.enterText(
      find.widgetWithText(TextFormField, 'CEP'),
      '90000-001',
    );
    await tester.enterText(
      find.widgetWithText(TextFormField, 'Senha'),
      'Portal!123',
    );
    await tester.enterText(
      find.widgetWithText(TextFormField, 'Confirmar senha'),
      'Portal!123',
    );

    await tester.ensureVisible(find.text('Salvar passageiro'));
    await tester.tap(find.text('Salvar passageiro'));
    await tester.pumpAndSettle();

    expect(gateway.createdInput?.motoristaId, isNull);
    expect(gateway.createdInput?.dataNascimento, '1998-08-12');
    expect(gateway.createdInput?.menorDeIdade, isFalse);
    expect(gateway.createdInput?.servicoIds, [10, 11]);
    expect(gateway.updatedPassageiroId, isNull);
    expect(gateway.updatedServicoIds, isNull);
    expect(find.text('Selecione um motorista'), findsNothing);
    expect(
      find.text('Passageiro criado com sucesso no portal.'),
      findsOneWidget,
    );
  });

  testWidgets('edita servicos liberados pela ficha do passageiro', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1400, 1400));
    final gateway = _FakeAdminPassageiroGateway();
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

    await tester.ensureVisible(find.text('Editar serviços'));
    await tester.tap(find.text('Editar serviços'));
    await tester.pumpAndSettle();

    expect(find.text('Serviços liberados para Aluno Portal'), findsOneWidget);
    expect(find.text('Ida manhã'), findsWidgets);
    expect(find.text('Volta tarde'), findsWidgets);

    await tester.tap(find.text('Salvar serviços'));
    await tester.pumpAndSettle();

    expect(gateway.updatedPassageiroId, 1);
    expect(gateway.updatedServicoIds, [10, 11]);
    expect(
      find.text('Serviços do passageiro atualizados com sucesso.'),
      findsOneWidget,
    );
    expect(find.text('Revisar viagens'), findsOneWidget);

    await tester.tap(find.text('Revisar viagens'));
    await tester.pump();

    expect(reviewIntent?.sourceLabel, 'PASSAGEIRO');
    expect(reviewIntent?.title, 'Revise as viagens de Aluno Portal');
  });

  testWidgets('gerencia responsáveis pela ficha do passageiro', (tester) async {
    await tester.binding.setSurfaceSize(const Size(1400, 1500));
    final passageiroGateway = _FakeAdminPassageiroGateway();
    final linkGateway = _FakeResponsavelPassageiroLinkGateway();

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: AdminPassageirosScreen(
            session: session,
            gateway: passageiroGateway,
            linkGateway: linkGateway,
            onboardingGateway: FakePassengerOnboardingGateway(),
          ),
        ),
      ),
    );

    await tester.pumpAndSettle();

    expect(find.text('Responsáveis vinculados'), findsOneWidget);
    await tester.ensureVisible(find.text('Responsáveis vinculados'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Responsáveis vinculados'));
    await tester.pumpAndSettle();

    expect(find.text('Responsavel Portal'), findsOneWidget);

    await tester.ensureVisible(find.text('Gerenciar responsáveis'));
    await tester.tap(find.text('Gerenciar responsáveis'));
    await tester.pumpAndSettle();

    expect(find.text('Responsáveis de Aluno Portal'), findsOneWidget);
    expect(find.text('Responsavel Novo'), findsOneWidget);

    await tester.tap(find.text('Vincular responsável'));
    await tester.pumpAndSettle();

    expect(linkGateway.linkedResponsavelId, 8);
    expect(linkGateway.linkedPassageiroId, 1);
    expect(passageiroGateway.fetchPassageirosCalls, greaterThan(1));
    expect(find.text('Responsável vinculado ao passageiro.'), findsOneWidget);

    await tester.ensureVisible(find.text('Gerenciar responsáveis'));
    await tester.tap(find.text('Gerenciar responsáveis'));
    await tester.pumpAndSettle();

    await tester.tap(find.byTooltip('Remover vínculo').first);
    await tester.pumpAndSettle();

    expect(linkGateway.unlinkedResponsavelId, 7);
    expect(linkGateway.unlinkedPassageiroId, 1);
    expect(find.text('Vínculo do responsável removido.'), findsOneWidget);
  });

  testWidgets('mostra entrada de passageiros no contexto da entidade', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1400, 1500));
    final onboardingGateway = FakePassengerOnboardingGateway();

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: AdminPassageirosScreen(
            session: session,
            gateway: _FakeAdminPassageiroGateway(),
            onboardingGateway: onboardingGateway,
          ),
        ),
      ),
    );

    await tester.pumpAndSettle();

    expect(find.text('Entrada de passageiros'), findsOneWidget);
    expect(find.text('Solicitações de acesso'), findsWidgets);
    expect(find.text('Códigos de convite'), findsWidgets);
    expect(find.text('Aluno Solicitante'), findsOneWidget);
    expect(find.text('VAN-PAX-2026'), findsOneWidget);
    expect(onboardingGateway.fetchAccessRequestsCalls, greaterThanOrEqualTo(1));
  });

  testWidgets('nao oculta entradas quando ha mais de quatro itens', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1400, 1800));
    final onboardingGateway = FakePassengerOnboardingGateway.withMany(count: 5);

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: AdminPassageirosScreen(
            session: session,
            gateway: _FakeAdminPassageiroGateway(),
            onboardingGateway: onboardingGateway,
          ),
        ),
      ),
    );

    await tester.pumpAndSettle();

    expect(find.text('Aluno Solicitante 5'), findsOneWidget);
    expect(find.text('VAN-PAX-005'), findsOneWidget);

    await tester.ensureVisible(find.text('Aluno Solicitante 5'));
    await tester.tap(find.text('Aluno Solicitante 5'));
    await tester.pumpAndSettle();

    expect(find.text('Revisar solicitação'), findsOneWidget);
    await tester.tap(find.widgetWithText(TextButton, 'Cancelar'));
    await tester.pumpAndSettle();
  });

  testWidgets(
    'abre detalhe de convite pela tela de passageiros e copia codigo',
    (tester) async {
      await tester.binding.setSurfaceSize(const Size(1400, 1500));
      final onboardingGateway = FakePassengerOnboardingGateway();

      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: AdminPassageirosScreen(
              session: session,
              gateway: _FakeAdminPassageiroGateway(),
              onboardingGateway: onboardingGateway,
            ),
          ),
        ),
      );

      await tester.pumpAndSettle();

      await tester.ensureVisible(find.text('VAN-PAX-2026'));
      await tester.tap(find.text('VAN-PAX-2026'));
      await tester.pumpAndSettle();

      expect(onboardingGateway.openedInviteId, 44);
      expect(find.text('Detalhes do convite'), findsOneWidget);

      await tester.tap(find.widgetWithText(TextButton, 'Copiar código'));
      await tester.pumpAndSettle();

      expect(find.text('Detalhes do convite'), findsNothing);
    },
  );

  testWidgets('aprova solicitação pela tela de passageiros', (tester) async {
    await tester.binding.setSurfaceSize(const Size(1400, 1500));
    final passageiroGateway = _FakeAdminPassageiroGateway();
    final onboardingGateway = FakePassengerOnboardingGateway();

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: AdminPassageirosScreen(
            session: session,
            gateway: passageiroGateway,
            onboardingGateway: onboardingGateway,
          ),
        ),
      ),
    );

    await tester.pumpAndSettle();

    await tester.ensureVisible(find.text('Aluno Solicitante'));
    await tester.tap(find.text('Aluno Solicitante'));
    await tester.pumpAndSettle();

    expect(find.text('Revisar solicitação'), findsOneWidget);
    expect(find.text('Serviços liberados'), findsOneWidget);

    await tester.tap(find.text('Ida manhã').last);
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(FilledButton, 'Aprovar vínculo'));
    await tester.pumpAndSettle();

    expect(onboardingGateway.approvedRequestId, 31);
    expect(onboardingGateway.approvedServicoIds, [10]);
    expect(passageiroGateway.fetchPassageirosCalls, greaterThan(1));
    expect(
      find.text('Solicitação aprovada e passageiro atualizado.'),
      findsOneWidget,
    );
  });
}

class _FakeAdminPassageiroGateway implements AdminPassageiroGateway {
  AdminPassageiroCreateInput? createdInput;
  int? updatedPassageiroId;
  List<int>? updatedServicoIds;
  var fetchPassageirosCalls = 0;

  @override
  Future<AdminPassageiroRecord> createPassageiro(
    AdminPassageiroCreateInput input,
  ) async {
    createdInput = input;
    return _detail;
  }

  @override
  Future<AdminPassageiroRecord> fetchPassageiroDetail(int passageiroId) async =>
      _detail;

  @override
  Future<List<PassengerAvailableServiceRecord>> fetchAvailableServices({
    required int passageiroId,
    required DateTime data,
  }) async => const [];

  @override
  Future<List<AdminPassageiroRecord>> fetchPassageiros() async {
    fetchPassageirosCalls++;
    return const [_summary];
  }

  @override
  Future<List<AdminMotoristaRecord>> fetchMotoristas() async {
    return const [
      AdminMotoristaRecord(
        id: 7,
        nome: 'Motorista Portal',
        email: 'portal@vanrides.dev',
        telefone: '51999999999',
      ),
    ];
  }

  @override
  Future<List<AdminServicoRecorrenteRecord>> fetchServicos() async => const [
    AdminServicoRecorrenteRecord(
      id: 10,
      nome: 'Ida manhã',
      tipoOperacao: 'IDA',
      quantidadePassageiros: 4,
      passageiroIds: [1, 21, 22, 23, 24],
      ativo: true,
      geracaoAutomaticaAtiva: true,
      politicaPresenca: 'INCLUIR_POR_PADRAO',
      diasOperacao: ['SEG', 'TER', 'QUA'],
      horarioPartida: '07:30',
    ),
    AdminServicoRecorrenteRecord(
      id: 11,
      nome: 'Volta tarde',
      tipoOperacao: 'VOLTA',
      quantidadePassageiros: 3,
      passageiroIds: [1, 21, 22, 23],
      ativo: true,
      geracaoAutomaticaAtiva: true,
      politicaPresenca: 'EXIGE_CONFIRMACAO',
      diasOperacao: ['SEG', 'TER', 'QUA'],
      horarioPartida: '17:40',
    ),
  ];

  @override
  Future<AdminPassageiroRecord> reassignMotorista(
    int passageiroId,
    int motoristaId,
  ) async => _detail;

  @override
  Future<AdminPassageiroRecord> updatePassageiro(
    int passageiroId,
    AdminPassageiroUpdateInput input,
  ) async => _detail;

  @override
  Future<List<AdminPassageiroServicoRecord>> updatePassageiroServicos({
    required int passageiroId,
    required List<int> servicoIds,
  }) async {
    updatedPassageiroId = passageiroId;
    updatedServicoIds = servicoIds;
    return const [];
  }
}

class _FakeResponsavelPassageiroLinkGateway
    implements AdminResponsavelPassageiroLinkGateway {
  int? linkedResponsavelId;
  int? linkedPassageiroId;
  int? unlinkedResponsavelId;
  int? unlinkedPassageiroId;

  @override
  Future<List<AdminPassageiroRecord>> fetchPassageiros() async => const [
    _detail,
  ];

  @override
  Future<List<AdminResponsavelRecord>> fetchResponsaveis() async => const [
    _guardian,
    _guardianNovo,
  ];

  @override
  Future<void> linkResponsavelPassageiro({
    required int responsavelId,
    required int passageiroId,
  }) async {
    linkedResponsavelId = responsavelId;
    linkedPassageiroId = passageiroId;
  }

  @override
  Future<void> unlinkResponsavelPassageiro({
    required int responsavelId,
    required int passageiroId,
  }) async {
    unlinkedResponsavelId = responsavelId;
    unlinkedPassageiroId = passageiroId;
  }
}

const _summary = AdminPassageiroRecord(
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
  dataNascimento: '2010-01-01',
  motoristaId: 7,
  motoristaNome: 'Motorista Portal',
  responsavelCount: 1,
  responsaveis: [_guardian],
);

const _detail = AdminPassageiroRecord(
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
  motorista: AdminMotoristaRecord(
    id: 7,
    nome: 'Motorista Portal',
    email: 'portal@vanrides.dev',
    telefone: '51999999999',
  ),
  responsavelCount: 1,
  responsaveis: [_guardian],
);

const _guardian = AdminResponsavelRecord(
  id: 7,
  nome: 'Responsavel Portal',
  email: 'responsavel@vanrides.dev',
  telefone: '51999999997',
  passageiroCount: 1,
  acessoConfigurado: true,
);

const _guardianNovo = AdminResponsavelRecord(
  id: 8,
  nome: 'Responsavel Novo',
  email: 'novo@vanrides.dev',
  telefone: '51999999996',
  passageiroCount: 0,
  acessoConfigurado: true,
);
