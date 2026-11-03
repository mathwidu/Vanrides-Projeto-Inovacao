import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/admin/data/admin_responsavel_portal_service.dart';
import 'package:vanrides_frontend_dev/features/admin/data/admin_vinculo_portal_service.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_passageiro_linked_record.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_passageiro_record.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_portal_session.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_responsavel_create_input.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_responsavel_record.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_responsavel_update_input.dart';
import 'package:vanrides_frontend_dev/features/admin/presentation/admin_responsaveis_screen.dart';

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

  testWidgets('mostra lista e detalhe reais de responsáveis', (tester) async {
    await tester.binding.setSurfaceSize(const Size(1440, 1200));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: AdminResponsaveisScreen(
            session: session,
            gateway: _FakeAdminResponsavelGateway(),
          ),
        ),
      ),
    );

    await tester.pumpAndSettle();

    expect(find.text('Responsáveis'), findsOneWidget);
    expect(find.text('Responsavel Portal'), findsWidgets);
    expect(find.text('Vínculos atuais'), findsOneWidget);
    expect(find.text('Aluno Portal'), findsWidgets);
    expect(find.text('Filho • acompanha serviços liberados'), findsOneWidget);
    expect(find.textContaining('Sem motorista'), findsNothing);
    expect(find.text('Notas Operacionais'), findsNothing);
    expect(find.text('Dados administrativos'), findsOneWidget);
    expect(find.text('responsavel@vanrides.dev'), findsWidgets);
    expect(find.text('1 passageiro vinculado'), findsOneWidget);
  });

  testWidgets('abre dialog de novo responsável pelo botão do portal', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1440, 1200));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: AdminResponsaveisScreen(
            session: session,
            gateway: _FakeAdminResponsavelGateway(),
          ),
        ),
      ),
    );

    await tester.pumpAndSettle();

    await tester.tap(find.text('Novo Responsável'));
    await tester.pumpAndSettle();

    expect(find.text('Novo responsavel'), findsOneWidget);
    expect(find.widgetWithText(TextFormField, 'Nome'), findsOneWidget);
  });

  testWidgets('recarrega lista depois de criar responsável com sucesso', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1440, 1200));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    final gateway = _FakeAdminResponsavelGateway();

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: AdminResponsaveisScreen(session: session, gateway: gateway),
        ),
      ),
    );

    await tester.pumpAndSettle();

    expect(gateway.fetchResponsaveisCalls, 1);

    await tester.tap(find.text('Novo Responsável'));
    await tester.pumpAndSettle();

    await tester.enterText(find.widgetWithText(TextFormField, 'Nome'), 'Ana');
    await tester.enterText(
      find.widgetWithText(TextFormField, 'E-mail'),
      'ana@vanrides.dev',
    );
    await tester.enterText(
      find.widgetWithText(TextFormField, 'Telefone'),
      '51999999999',
    );
    await tester.enterText(
      find.widgetWithText(TextFormField, 'Data de nascimento'),
      '10/03/1988',
    );
    await tester.tap(find.text('Salvar'));
    await tester.pumpAndSettle();

    expect(gateway.createResponsavelCalls, 1);
    expect(gateway.fetchResponsaveisCalls, 2);
    expect(find.text('Responsavel criado com sucesso.'), findsOneWidget);
  });

  testWidgets('gerencia passageiros pela ficha do responsável', (tester) async {
    await tester.binding.setSurfaceSize(const Size(1440, 1300));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    final responsavelGateway = _FakeAdminResponsavelGateway();
    final linkGateway = _FakeResponsavelPassageiroLinkGateway();

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: AdminResponsaveisScreen(
            session: session,
            gateway: responsavelGateway,
            linkGateway: linkGateway,
          ),
        ),
      ),
    );

    await tester.pumpAndSettle();

    await tester.ensureVisible(find.text('Gerenciar passageiros'));
    await tester.tap(find.text('Gerenciar passageiros'));
    await tester.pumpAndSettle();

    expect(find.text('Passageiros de Responsavel Portal'), findsOneWidget);
    expect(find.text('Aluno Novo'), findsOneWidget);

    await tester.tap(find.text('Vincular passageiro'));
    await tester.pumpAndSettle();

    expect(linkGateway.linkedResponsavelId, 7);
    expect(linkGateway.linkedPassageiroId, 12);
    expect(responsavelGateway.fetchResponsaveisCalls, greaterThan(1));
    expect(find.text('Passageiro vinculado ao responsável.'), findsOneWidget);

    await tester.ensureVisible(find.text('Gerenciar passageiros'));
    await tester.tap(find.text('Gerenciar passageiros'));
    await tester.pumpAndSettle();

    await tester.tap(find.byTooltip('Remover vínculo').first);
    await tester.pumpAndSettle();

    expect(linkGateway.unlinkedResponsavelId, 7);
    expect(linkGateway.unlinkedPassageiroId, 11);
    expect(find.text('Vínculo do passageiro removido.'), findsOneWidget);
  });
}

class _FakeAdminResponsavelGateway implements AdminResponsavelGateway {
  _FakeAdminResponsavelGateway();

  var createResponsavelCalls = 0;
  var fetchResponsaveisCalls = 0;
  final List<AdminResponsavelRecord> _responsaveis = [_summary];

  @override
  Future<AdminResponsavelRecord> createResponsavel(
    AdminResponsavelCreateInput input,
  ) async {
    createResponsavelCalls++;
    _responsaveis.insert(
      0,
      AdminResponsavelRecord(
        id: 8,
        nome: input.nome,
        email: input.email,
        telefone: input.telefone,
        passageiroCount: 0,
        acessoConfigurado: input.senha != null,
      ),
    );
    return _responsaveis.first;
  }

  @override
  Future<void> deleteResponsavel(int responsavelId) async {}

  @override
  Future<AdminResponsavelRecord> fetchResponsavelDetail(
    int responsavelId,
  ) async {
    if (responsavelId == _detail.id) {
      return _detail;
    }

    return _responsaveis.firstWhere(
      (responsavel) => responsavel.id == responsavelId,
      orElse: () => _detail,
    );
  }

  @override
  Future<List<AdminResponsavelRecord>> fetchResponsaveis() async {
    fetchResponsaveisCalls++;
    return List.unmodifiable(_responsaveis);
  }

  @override
  Future<AdminResponsavelRecord> updateResponsavel(
    int responsavelId,
    AdminResponsavelUpdateInput input,
  ) async => _detail;
}

class _FakeResponsavelPassageiroLinkGateway
    implements AdminResponsavelPassageiroLinkGateway {
  int? linkedResponsavelId;
  int? linkedPassageiroId;
  int? unlinkedResponsavelId;
  int? unlinkedPassageiroId;

  @override
  Future<List<AdminPassageiroRecord>> fetchPassageiros() async => const [
    AdminPassageiroRecord(
      id: 11,
      nome: 'Aluno Portal',
      email: 'aluno@vanrides.dev',
      telefone: '51999999998',
      menorDeIdade: true,
    ),
    AdminPassageiroRecord(
      id: 12,
      nome: 'Aluno Novo',
      email: 'novo@vanrides.dev',
      telefone: '51999999996',
      menorDeIdade: false,
    ),
  ];

  @override
  Future<List<AdminResponsavelRecord>> fetchResponsaveis() async => const [
    _detail,
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

const _summary = AdminResponsavelRecord(
  id: 7,
  nome: 'Responsavel Portal',
  email: 'responsavel@vanrides.dev',
  telefone: '51999999997',
  passageiroCount: 1,
  acessoConfigurado: true,
);

const _detail = AdminResponsavelRecord(
  id: 7,
  nome: 'Responsavel Portal',
  email: 'responsavel@vanrides.dev',
  telefone: '51999999997',
  passageiroCount: 1,
  acessoConfigurado: true,
  passageiros: [
    AdminPassageiroLinkedRecord(
      id: 11,
      nome: 'Aluno Portal',
      email: 'aluno@vanrides.dev',
      menorDeIdade: true,
    ),
  ],
);
