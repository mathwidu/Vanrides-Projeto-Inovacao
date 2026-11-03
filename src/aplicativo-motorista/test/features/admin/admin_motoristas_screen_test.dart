import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/admin/data/admin_driver_link_portal_service.dart';
import 'package:vanrides_frontend_dev/features/admin/data/admin_motorista_assignment_service.dart';
import 'package:vanrides_frontend_dev/features/admin/data/admin_motorista_portal_service.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_company_link_flow_models.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_motorista_create_input.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_motorista_record.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_portal_session.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_servico_recorrente_record.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_viagem_record.dart';
import 'package:vanrides_frontend_dev/features/admin/presentation/admin_motoristas_screen.dart';

class _FakeGateway implements AdminMotoristaGateway {
  _FakeGateway(this._motoristas);

  final List<AdminMotoristaRecord> _motoristas;
  AdminMotoristaCreateInput? lastCreateInput;

  @override
  Future<AdminMotoristaRecord> createMotorista(
    AdminMotoristaCreateInput input,
  ) async {
    lastCreateInput = input;
    final created = AdminMotoristaRecord(
      id: 3,
      nome: input.nome,
      email: input.email,
      telefone: input.telefone,
    );
    _motoristas.insert(0, created);
    return created;
  }

  @override
  Future<List<AdminMotoristaRecord>> fetchMotoristas() async => _motoristas;

  @override
  Future<AdminMotoristaRecord> editMotorista(
    int motoristaId, {
    required String nome,
    required String email,
    required String telefone,
  }) async {
    final idx = _motoristas.indexWhere((m) => m.id == motoristaId);
    final updated = AdminMotoristaRecord(
      id: motoristaId,
      nome: nome,
      email: email,
      telefone: telefone,
    );
    if (idx >= 0) _motoristas[idx] = updated;
    return updated;
  }

  @override
  Future<void> deleteMotorista(int motoristaId) async {
    _motoristas.removeWhere((m) => m.id == motoristaId);
  }
}

class _FakeDriverLinkGateway implements AdminDriverLinkGateway {
  int? approvedRequestId;
  int createdInviteCount = 0;

  @override
  Future<List<AdminDriverInviteRecord>> fetchDriverInvites() async {
    return const [
      AdminDriverInviteRecord(
        id: 44,
        companyId: 1,
        companyName: 'Portal Empresa',
        shareUrl: 'vanrides://driver-invite/token-44',
        status: 'ACTIVE',
      ),
    ];
  }

  @override
  Future<AdminDriverInviteRecord> createDriverInvite({
    DateTime? expiresAt,
  }) async {
    createdInviteCount += 1;
    return const AdminDriverInviteRecord(
      id: 45,
      companyId: 1,
      companyName: 'Portal Empresa',
      shareUrl: 'vanrides://driver-invite/token-45',
      status: 'ACTIVE',
    );
  }

  @override
  Future<AdminDriverInviteRecord> cancelDriverInvite(int id) async {
    return AdminDriverInviteRecord(
      id: id,
      companyId: 1,
      companyName: 'Portal Empresa',
      status: 'CANCELLED',
    );
  }

  @override
  Future<List<AdminDriverLinkRequestRecord>> fetchDriverLinkRequests() async {
    return const [
      AdminDriverLinkRequestRecord(
        id: 88,
        motoristaId: 31,
        motoristaNome: 'Motorista App',
        motoristaEmail: 'motorista.app@vanrides.dev',
        motoristaTelefone: '51999990009',
        companyId: 1,
        companyName: 'Portal Empresa',
        status: 'PENDING_APPROVAL',
        emailVerified: true,
      ),
    ];
  }

  @override
  Future<AdminDriverLinkRequestRecord> approveDriverLinkRequest({
    required int requestId,
    String? reason,
  }) async {
    approvedRequestId = requestId;
    return AdminDriverLinkRequestRecord(
      id: requestId,
      motoristaId: 31,
      motoristaNome: 'Motorista App',
      motoristaEmail: 'motorista.app@vanrides.dev',
      motoristaTelefone: '51999990009',
      companyId: 1,
      companyName: 'Portal Empresa',
      status: 'APPROVED',
      emailVerified: true,
    );
  }

  @override
  Future<AdminDriverLinkRequestRecord> rejectDriverLinkRequest({
    required int requestId,
    String? reason,
  }) async {
    return AdminDriverLinkRequestRecord(
      id: requestId,
      motoristaId: 31,
      motoristaNome: 'Motorista App',
      motoristaEmail: 'motorista.app@vanrides.dev',
      motoristaTelefone: '51999990009',
      companyId: 1,
      companyName: 'Portal Empresa',
      status: 'REJECTED',
      emailVerified: true,
    );
  }
}

class _FakeAssignmentGateway implements AdminMotoristaAssignmentGateway {
  const _FakeAssignmentGateway({
    this.servicos = const [],
    this.viagens = const [],
  });

  final List<AdminServicoRecorrenteRecord> servicos;
  final List<AdminViagemRecord> viagens;

  @override
  Future<List<AdminServicoRecorrenteRecord>> fetchServicos() async => servicos;

  @override
  Future<List<AdminViagemRecord>> fetchViagens({DateTime? data}) async =>
      viagens;
}

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

  testWidgets('renderiza lista e detalhe do motorista selecionado', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1440, 1200));

    final gateway = _FakeGateway([
      const AdminMotoristaRecord(
        id: 1,
        nome: 'Motorista A',
        email: 'a@vanrides.dev',
        telefone: '51999990001',
      ),
      const AdminMotoristaRecord(
        id: 2,
        nome: 'Motorista B',
        email: 'b@vanrides.dev',
        telefone: '51999990002',
      ),
    ]);

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: AdminMotoristasScreen(session: session, gateway: gateway),
        ),
      ),
    );

    await tester.pumpAndSettle();

    expect(find.text('Motoristas'), findsOneWidget);
    expect(find.text('Cadastrar manualmente'), findsOneWidget);
    expect(find.text('Adicionar motorista'), findsNothing);
    expect(find.text('Motorista A'), findsWidgets);
    expect(find.text('Pronto para escala'), findsWidgets);
    expect(find.text('Relatórios Operacionais'), findsNothing);
  });

  testWidgets('ficha mostra servicos e viagens atribuidos ao motorista', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1440, 1200));

    const motorista = AdminMotoristaRecord(
      id: 1,
      nome: 'Motorista A',
      email: 'a@vanrides.dev',
      telefone: '51999990001',
    );
    final gateway = _FakeGateway([motorista]);
    final assignments = _FakeAssignmentGateway(
      servicos: const [
        AdminServicoRecorrenteRecord(
          id: 12,
          nome: 'Linha Escola',
          tipoOperacao: 'IDA',
          motoristaPadrao: motorista,
          quantidadePassageiros: 4,
          passageiroIds: [10, 11, 12, 13],
          ativo: true,
          geracaoAutomaticaAtiva: true,
          politicaPresenca: 'EXIGE_CONFIRMACAO',
          diasOperacao: ['SEG', 'TER'],
          horarioPartida: '07:00',
        ),
      ],
      viagens: [
        AdminViagemRecord(
          id: 50,
          rotaNome: 'Linha Escola',
          servicoNome: 'Linha Escola',
          dataOperacao: DateTime(2026, 6, 18),
          status: 'PLANEJADA',
          motorista: motorista,
          quantidadeParadas: 4,
          horarioPartida: '07:00',
        ),
      ],
    );

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: AdminMotoristasScreen(
            session: session,
            gateway: gateway,
            assignmentGateway: assignments,
          ),
        ),
      ),
    );

    await tester.pumpAndSettle();

    expect(find.text('Operação atribuída'), findsOneWidget);
    expect(find.text('1 serviço padrão'), findsOneWidget);
    expect(find.text('1 viagem hoje'), findsOneWidget);
    expect(find.text('Linha Escola'), findsWidgets);
    expect(find.text('PLANEJADA • 07:00'), findsOneWidget);
  });

  testWidgets('permite vincular conta de motorista existente sem senha', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1440, 1200));

    final gateway = _FakeGateway([]);

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: AdminMotoristasScreen(session: session, gateway: gateway),
        ),
      ),
    );

    await tester.pumpAndSettle();

    await tester.tap(find.text('Cadastrar manualmente').first);
    await tester.pumpAndSettle();

    expect(find.textContaining('senha provis'), findsNothing);
    expect(
      find.widgetWithText(TextFormField, 'Senha provisoria (opcional)'),
      findsNothing,
    );

    await tester.enterText(find.byType(TextFormField).at(0), 'Motorista App');
    await tester.enterText(
      find.byType(TextFormField).at(1),
      'motorista.app@vanrides.dev',
    );
    await tester.enterText(find.byType(TextFormField).at(2), '51999990009');
    await tester.enterText(find.byType(TextFormField).at(3), '30/11/1988');

    await tester.tap(find.text('Salvar vínculo'));
    await tester.pumpAndSettle();

    expect(gateway.lastCreateInput?.email, 'motorista.app@vanrides.dev');
    expect(gateway.lastCreateInput?.dataNascimento, '1988-11-30');
    expect(gateway.lastCreateInput?.senha, isNull);
    expect(find.text('Motorista App'), findsWidgets);
  });

  testWidgets('cta principal cria convite por link quando gateway existe', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1440, 1400));

    final driverGateway = _FakeDriverLinkGateway();

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: AdminMotoristasScreen(
            session: session,
            gateway: _FakeGateway(const []),
            driverLinkGateway: driverGateway,
          ),
        ),
      ),
    );

    await tester.pumpAndSettle();

    expect(find.text('Convidar motorista'), findsOneWidget);
    expect(find.text('Novo link'), findsNothing);
    await tester.tap(find.text('Convidar motorista'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 350));

    expect(driverGateway.createdInviteCount, 1);
    expect(find.textContaining('Convite criado'), findsOneWidget);
  });

  testWidgets('mostra convites e aprova solicitacao de motorista', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1440, 1400));

    final driverGateway = _FakeDriverLinkGateway();

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: AdminMotoristasScreen(
            session: session,
            gateway: _FakeGateway(const []),
            driverLinkGateway: driverGateway,
          ),
        ),
      ),
    );

    await tester.pumpAndSettle();

    expect(find.text('Entrada de motoristas'), findsOneWidget);
    expect(find.text('Links ativos'), findsOneWidget);
    expect(find.text('Solicitações pendentes'), findsOneWidget);
    expect(find.text('E-mails verificados'), findsOneWidget);
    expect(find.text('vanrides://driver-invite/token-44'), findsOneWidget);
    expect(find.text('Motorista App'), findsOneWidget);

    await tester.tap(find.text('Aprovar'));
    await tester.pumpAndSettle();

    expect(driverGateway.approvedRequestId, 88);
  });
}
