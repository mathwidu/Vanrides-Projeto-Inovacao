import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/admin/data/admin_vinculo_portal_service.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_company_link_flow_models.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_passageiro_linked_record.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_passageiro_motorista_link_record.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_motorista_record.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_passageiro_record.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_passageiro_servico_record.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_portal_session.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_responsavel_passageiro_link_record.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_responsavel_record.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_servico_recorrente_record.dart';
import 'package:vanrides_frontend_dev/features/admin/presentation/admin_vinculos_screen.dart';

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

  testWidgets('mostra os vínculos reais do portal', (tester) async {
    await tester.binding.setSurfaceSize(const Size(1440, 1200));

    await tester.pumpWidget(
      const MaterialApp(
        home: AdminVinculosScreen(
          session: session,
          gateway: _FakeAdminVinculoGateway(),
        ),
      ),
    );

    await tester.pumpAndSettle();

    expect(find.text('Onboarding de acessos e vínculos'), findsOneWidget);
    expect(find.textContaining('tarefas da operação'), findsOneWidget);
    expect(find.text('Fila de onboarding'), findsOneWidget);
    expect(find.text('Próxima tarefa'), findsOneWidget);
    expect(find.text('Revisar 1 solicitação pendente'), findsOneWidget);
    expect(find.text('Criar convite assistido'), findsOneWidget);
    expect(find.text('1 vínculo familiar ativo'), findsOneWidget);
    expect(find.text('Diagnóstico de vínculos operacionais'), findsNothing);
    expect(find.textContaining('área avançada'), findsNothing);
    expect(
      find.textContaining('fichas de Passageiros, Motoristas e Responsáveis'),
      findsNothing,
    );
    expect(find.text('Gestão de Vínculos'), findsNothing);
    expect(find.text('Atalho motorista'), findsOneWidget);
    expect(find.text('LEGADO / APOIO OPERACIONAL'), findsOneWidget);
    expect(
      find.text('A viagem do dia define o motorista real'),
      findsOneWidget,
    );
    expect(find.text('Passageiro -> Motorista'), findsNothing);
    expect(find.text('Custódia responsável-passageiro'), findsOneWidget);
    expect(find.text('Aluno Portal'), findsWidgets);
    expect(find.text('Responsavel Portal'), findsWidgets);
    expect(find.text('Ajuste técnico responsável-passageiro'), findsOneWidget);
    expect(find.text('Solicitações de acesso'), findsOneWidget);
    expect(find.text('Códigos de convite'), findsOneWidget);
    expect(
      tester.getTopLeft(find.text('Fila de onboarding')).dy,
      lessThan(tester.getTopLeft(find.text('Solicitações de acesso')).dy),
    );
  });

  testWidgets('mostra empty states quando nao ha vinculos nem solicitacoes', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1440, 1200));

    await tester.pumpWidget(
      const MaterialApp(
        home: AdminVinculosScreen(
          session: session,
          gateway: _EmptyAdminVinculoGateway(),
        ),
      ),
    );

    await tester.pumpAndSettle();

    expect(find.text('Onboarding em dia'), findsOneWidget);
    expect(find.text('Criar primeiro convite'), findsOneWidget);
    expect(find.text('0 solicitações'), findsOneWidget);
    expect(find.text('Nenhum atalho cadastrado'), findsOneWidget);
    expect(
      find.textContaining('serviços liberados mesmo sem motorista fixo'),
      findsOneWidget,
    );
    expect(find.text('Nenhum vínculo responsável cadastrado'), findsOneWidget);
  });

  testWidgets('loading mostra que a fila de onboarding esta sendo montada', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1440, 1200));

    await tester.pumpWidget(
      const MaterialApp(
        home: AdminVinculosScreen(
          session: session,
          gateway: _SlowAdminVinculoGateway(),
        ),
      ),
    );

    expect(find.text('Carregando fila de onboarding'), findsOneWidget);
    expect(
      find.text('Buscando solicitações, convites e vínculos da empresa.'),
      findsOneWidget,
    );
    expect(find.byType(CircularProgressIndicator), findsOneWidget);
  });

  testWidgets('aprova solicitacao com servicos liberados', (tester) async {
    await tester.binding.setSurfaceSize(const Size(1440, 1200));
    final gateway = _RecordingAdminVinculoGateway();

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: AdminVinculosScreen(session: session, gateway: gateway),
        ),
      ),
    );

    await tester.pumpAndSettle();
    await tester.tap(find.text('Aluno Portal').first);
    await tester.pumpAndSettle();

    expect(find.text('Serviços liberados'), findsOneWidget);
    await tester.tap(find.text('Ida manhã'));
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(FilledButton, 'Aprovar vínculo'));
    await tester.pumpAndSettle();

    expect(gateway.approvedServicoIds, [10]);
  });

  testWidgets('edita servicos liberados de passageiro aprovado', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1440, 1200));
    final gateway = _RecordingAdminVinculoGateway();

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: AdminVinculosScreen(session: session, gateway: gateway),
        ),
      ),
    );

    await tester.pumpAndSettle();
    expect(find.text('Direitos por serviço'), findsOneWidget);

    final editButton = find.byTooltip('Editar serviços de Aluno Portal');
    await tester.ensureVisible(editButton);
    await tester.pumpAndSettle();
    await tester.tap(editButton);
    await tester.pumpAndSettle();

    expect(find.text('Serviços liberados para Aluno Portal'), findsOneWidget);
    await tester.tap(find.text('Ida manhã').last);
    await tester.tap(find.text('Volta tarde').last);
    await tester.pumpAndSettle();
    await tester.tap(find.text('Salvar serviços'));
    await tester.pumpAndSettle();

    expect(gateway.updatedPassageiroId, 11);
    expect(gateway.updatedServicoIds, [10, 11]);
  });

  testWidgets('mostra direitos de uso por servico com horario e tipo', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1440, 1200));

    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: AdminVinculosScreen(
            session: session,
            gateway: _EligibleServicesAdminVinculoGateway(),
          ),
        ),
      ),
    );

    await tester.pumpAndSettle();

    expect(find.text('2 de 2 serviços liberados'), findsOneWidget);
    expect(find.text('07:30 Ida • Ida manhã'), findsOneWidget);
    expect(find.text('17:40 Volta • Volta tarde'), findsOneWidget);
  });
}

class _FakeAdminVinculoGateway implements AdminVinculoGateway {
  const _FakeAdminVinculoGateway();

  @override
  Future<AdminAccessRequestDetailRecord> approveAccessRequest({
    required int requestId,
    int? motoristaId,
    String? reason,
    List<int>? servicoIds,
  }) async {
    return const AdminAccessRequestDetailRecord(
      id: 1,
      passageiroId: 11,
      passageiroNome: 'Aluno Portal',
      passageiroEmail: 'aluno@vanrides.dev',
      passageiroTelefone: '51999999998',
      companyName: 'Portal Empresa',
      source: 'REQUEST',
      status: 'APPROVED',
      emailVerified: true,
    );
  }

  @override
  Future<AdminInviteCodeDetailRecord> cancelInviteCode(int id) async {
    return const AdminInviteCodeDetailRecord(
      id: 91,
      code: 'VAN-2026-TESTE',
      status: 'CANCELLED',
      useCount: 0,
    );
  }

  @override
  Future<AdminInviteCodeDetailRecord> createInviteCode({
    DateTime? expiresAt,
    int? maxUses,
  }) async {
    return const AdminInviteCodeDetailRecord(
      id: 91,
      code: 'VAN-2026-TESTE',
      status: 'ACTIVE',
      useCount: 0,
    );
  }

  @override
  Future<AdminAccessRequestDetailRecord> fetchAccessRequestDetail(
    int id,
  ) async {
    return const AdminAccessRequestDetailRecord(
      id: 1,
      passageiroId: 11,
      passageiroNome: 'Aluno Portal',
      passageiroEmail: 'aluno@vanrides.dev',
      passageiroTelefone: '51999999998',
      companyName: 'Portal Empresa',
      source: 'REQUEST',
      status: 'PENDING',
      emailVerified: true,
    );
  }

  @override
  Future<List<AdminAccessRequestRecord>> fetchAccessRequests() async {
    return const [
      AdminAccessRequestRecord(
        id: 1,
        passageiroId: 11,
        passageiroNome: 'Aluno Portal',
        passageiroEmail: 'aluno@vanrides.dev',
        companyName: 'Portal Empresa',
        source: 'REQUEST',
        emailVerified: true,
      ),
    ];
  }

  @override
  Future<AdminInviteCodeDetailRecord> fetchInviteCodeDetail(int id) async {
    return const AdminInviteCodeDetailRecord(
      id: 91,
      code: 'VAN-2026-TESTE',
      status: 'ACTIVE',
      useCount: 0,
    );
  }

  @override
  Future<List<AdminInviteCodeRecord>> fetchInviteCodes() async {
    return const [
      AdminInviteCodeRecord(
        id: 91,
        code: 'VAN-2026-TESTE',
        status: 'ACTIVE',
        useCount: 0,
      ),
    ];
  }

  @override
  Future<List<AdminPassageiroMotoristaLinkRecord>>
  fetchPassageiroMotoristaLinks() async {
    return const [
      AdminPassageiroMotoristaLinkRecord(
        passageiroId: 11,
        passageiroNome: 'Aluno Portal',
        menorDeIdade: true,
      ),
    ];
  }

  @override
  Future<List<AdminPassageiroRecord>> fetchPassageiros() async => const [
    AdminPassageiroRecord(
      id: 11,
      nome: 'Aluno Portal',
      email: 'aluno@vanrides.dev',
      telefone: '51999999998',
      menorDeIdade: true,
    ),
  ];

  @override
  Future<List<AdminResponsavelPassageiroLinkRecord>>
  fetchResponsavelPassageiroLinks() async {
    return const [
      AdminResponsavelPassageiroLinkRecord(
        responsavel: AdminResponsavelRecord(
          id: 7,
          nome: 'Responsavel Portal',
          email: 'responsavel@vanrides.dev',
          telefone: '51999999997',
        ),
        passageiro: AdminPassageiroLinkedRecord(
          id: 11,
          nome: 'Aluno Portal',
          email: 'aluno@vanrides.dev',
          menorDeIdade: true,
        ),
      ),
    ];
  }

  @override
  Future<List<AdminResponsavelRecord>> fetchResponsaveis() async => const [
    AdminResponsavelRecord(
      id: 7,
      nome: 'Responsavel Portal',
      email: 'responsavel@vanrides.dev',
      telefone: '51999999997',
    ),
  ];

  @override
  Future<void> linkResponsavelPassageiro({
    required int responsavelId,
    required int passageiroId,
  }) async {}

  @override
  Future<List<AdminMotoristaRecord>> fetchMotoristas() async => const [
    AdminMotoristaRecord(
      id: 3,
      nome: 'Motorista Portal',
      email: 'motorista@vanrides.dev',
      telefone: '51999999999',
    ),
  ];

  @override
  Future<List<AdminServicoRecorrenteRecord>> fetchServicos() async => const [
    AdminServicoRecorrenteRecord(
      id: 10,
      nome: 'Ida manhã',
      tipoOperacao: 'IDA',
      quantidadePassageiros: 4,
      passageiroIds: [21, 22, 23, 24],
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
      passageiroIds: [21, 22, 23],
      ativo: true,
      geracaoAutomaticaAtiva: true,
      politicaPresenca: 'EXIGE_CONFIRMACAO',
      diasOperacao: ['SEG', 'TER', 'QUA'],
      horarioPartida: '17:40',
    ),
  ];

  @override
  Future<AdminInviteCodeDetailRecord> regenerateInviteCode(int id) async {
    return const AdminInviteCodeDetailRecord(
      id: 92,
      code: 'VAN-2026-NOVO',
      status: 'ACTIVE',
      useCount: 0,
    );
  }

  @override
  Future<AdminAccessRequestDetailRecord> rejectAccessRequest({
    required int requestId,
    String? reason,
  }) async {
    return const AdminAccessRequestDetailRecord(
      id: 1,
      passageiroId: 11,
      passageiroNome: 'Aluno Portal',
      passageiroEmail: 'aluno@vanrides.dev',
      passageiroTelefone: '51999999998',
      companyName: 'Portal Empresa',
      source: 'REQUEST',
      status: 'REJECTED',
      emailVerified: true,
    );
  }

  @override
  Future<void> unlinkPassageiroMotorista(int passageiroId) async {}

  @override
  Future<void> unlinkResponsavelPassageiro({
    required int responsavelId,
    required int passageiroId,
  }) async {}

  @override
  Future<List<AdminPassageiroServicoRecord>> updatePassageiroServicos({
    required int passageiroId,
    required List<int> servicoIds,
  }) async {
    return const [];
  }
}

class _EmptyAdminVinculoGateway extends _FakeAdminVinculoGateway {
  const _EmptyAdminVinculoGateway();

  @override
  Future<List<AdminAccessRequestRecord>> fetchAccessRequests() async =>
      const [];

  @override
  Future<List<AdminInviteCodeRecord>> fetchInviteCodes() async => const [];

  @override
  Future<List<AdminPassageiroMotoristaLinkRecord>>
  fetchPassageiroMotoristaLinks() async => const [];

  @override
  Future<List<AdminResponsavelPassageiroLinkRecord>>
  fetchResponsavelPassageiroLinks() async => const [];
}

class _SlowAdminVinculoGateway extends _FakeAdminVinculoGateway {
  const _SlowAdminVinculoGateway();

  @override
  Future<List<AdminAccessRequestRecord>> fetchAccessRequests() {
    return Completer<List<AdminAccessRequestRecord>>().future;
  }
}

class _EligibleServicesAdminVinculoGateway extends _FakeAdminVinculoGateway {
  const _EligibleServicesAdminVinculoGateway();

  @override
  Future<List<AdminServicoRecorrenteRecord>> fetchServicos() async => const [
    AdminServicoRecorrenteRecord(
      id: 10,
      nome: 'Ida manhã',
      tipoOperacao: 'IDA',
      quantidadePassageiros: 1,
      passageiroIds: [11],
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
      quantidadePassageiros: 1,
      passageiroIds: [11],
      ativo: true,
      geracaoAutomaticaAtiva: true,
      politicaPresenca: 'EXIGE_CONFIRMACAO',
      diasOperacao: ['SEG', 'TER', 'QUA'],
      horarioPartida: '17:40',
    ),
  ];
}

class _RecordingAdminVinculoGateway extends _FakeAdminVinculoGateway {
  List<int>? approvedServicoIds;
  int? updatedPassageiroId;
  List<int>? updatedServicoIds;

  @override
  Future<AdminAccessRequestDetailRecord> approveAccessRequest({
    required int requestId,
    int? motoristaId,
    String? reason,
    List<int>? servicoIds,
  }) async {
    approvedServicoIds = servicoIds;
    return super.approveAccessRequest(
      requestId: requestId,
      motoristaId: motoristaId,
      reason: reason,
      servicoIds: servicoIds,
    );
  }

  @override
  Future<List<AdminPassageiroServicoRecord>> updatePassageiroServicos({
    required int passageiroId,
    required List<int> servicoIds,
  }) async {
    updatedPassageiroId = passageiroId;
    updatedServicoIds = servicoIds;
    return super.updatePassageiroServicos(
      passageiroId: passageiroId,
      servicoIds: servicoIds,
    );
  }
}
