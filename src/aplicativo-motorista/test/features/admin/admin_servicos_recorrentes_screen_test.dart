import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/admin/data/admin_servicos_recorrentes_portal_service.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_motorista_record.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_passageiro_record.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_portal_session.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_servico_recorrente_input.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_servico_recorrente_record.dart';
import 'package:vanrides_frontend_dev/features/admin/presentation/admin_servicos_recorrentes_screen.dart';
import 'package:vanrides_frontend_dev/features/admin/presentation/admin_viagens_review_intent.dart';
import 'package:vanrides_frontend_dev/features/admin/presentation/widgets/admin_servico_recorrente_form.dart';

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

  testWidgets('renderiza listagem de servicos recorrentes', (tester) async {
    await tester.binding.setSurfaceSize(const Size(1440, 1200));

    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: AdminServicosRecorrentesScreen(
            session: session,
            gateway: _FakeServicosGatewayComDados(),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Serviços recorrentes'), findsOneWidget);
    expect(find.text('Novo serviço'), findsOneWidget);
    expect(find.text('Linha Manhã'), findsOneWidget);
    expect(find.text('Escala por viagem'), findsOneWidget);
    expect(find.text('SERVIÇOS ATIVOS'), findsOneWidget);
    expect(find.text('Serviço sem motorista fixo'), findsOneWidget);
    expect(
      find.text(
        'Revise os serviços que precisam de escala antes de montar o dia.',
      ),
      findsOneWidget,
    );
    expect(find.text('Abrir Linha Manhã'), findsOneWidget);
    expect(find.text('PRÓXIMO PASSO'), findsOneWidget);

    await tester.tap(find.text('Abrir Linha Manhã'));
    await tester.pumpAndSettle();

    expect(find.text('Prontidão operacional'), findsOneWidget);
    expect(find.text('Exige escala no dia'), findsOneWidget);
  });

  testWidgets('abre cockpit operacional do servico recorrente', (tester) async {
    await tester.binding.setSurfaceSize(const Size(1440, 1400));

    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: AdminServicosRecorrentesScreen(
            session: session,
            gateway: _FakeServicosGatewayComDados(),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Abrir'));
    await tester.pumpAndSettle();

    expect(find.text('Prontidão operacional'), findsOneWidget);
    expect(find.text('Regra operacional'), findsOneWidget);
    expect(find.text('Passageiros elegíveis'), findsOneWidget);
    expect(find.text('Exige escala no dia'), findsOneWidget);
    expect(find.text('Pronto com escala diária'), findsNothing);
    expect(
      find.text(
        'Alterações neste serviço valem para as próximas montagens. Se a viagem de hoje já foi criada, ajuste em Viagens do dia.',
      ),
      findsOneWidget,
    );
    expect(find.text('Aluno Portal'), findsOneWidget);
    expect(find.text('Voltar'), findsOneWidget);
  });

  testWidgets('servico recorrente encaminha montagem do dia para viagens', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1440, 1400));
    AdminViagensReviewIntent? reviewIntent;

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: AdminServicosRecorrentesScreen(
            session: session,
            gateway: const _FakeServicosGatewayComDados(),
            onOpenViagensReview: (intent) => reviewIntent = intent,
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Abrir'));
    await tester.pumpAndSettle();

    expect(find.text('Montar viagem de hoje'), findsOneWidget);

    await tester.tap(find.text('Montar viagem de hoje'));
    await tester.pump();

    expect(reviewIntent?.sourceLabel, 'SERVIÇO');
    expect(reviewIntent?.title, 'Revise Linha Manhã nas viagens');
    expect(
      reviewIntent?.description,
      contains('Atualize o dia e abra as viagens impactadas'),
    );
  });

  testWidgets('cria servico recorrente com passageiros elegiveis', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1440, 1400));
    final gateway = _FakeServicosGatewayVazio();

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: AdminServicosRecorrentesScreen(
            session: session,
            gateway: gateway,
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Criar primeiro serviço'));
    await tester.pumpAndSettle();

    expect(find.text('0 de 1 elegíveis'), findsOneWidget);
    expect(find.text('Gera viagem diária'), findsOneWidget);
    expect(find.text('Escala por viagem'), findsOneWidget);

    await tester.enterText(find.byType(TextFormField).first, 'Linha Tarde');
    await tester.enterText(find.byType(TextFormField).at(1), '17:40');
    await tester.pumpAndSettle();

    expect(find.text('17:40 Ida'), findsOneWidget);

    await tester.tap(find.text('Aluno Portal'));
    await tester.pumpAndSettle();

    expect(find.text('1 de 1 elegíveis'), findsOneWidget);

    await tester.tap(find.text('Salvar serviço'));
    await tester.pumpAndSettle();

    expect(gateway.createdInput?.nome, 'Linha Tarde');
    expect(gateway.createdInput?.passageiroIds, [11]);
    expect(find.text('Linha Tarde'), findsOneWidget);
  });

  testWidgets('edita elegibilidade do servico com busca de passageiro', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1440, 1400));
    final gateway = _FakeServicosGatewayEditavel();
    AdminViagensReviewIntent? reviewIntent;

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: AdminServicosRecorrentesScreen(
            session: session,
            gateway: gateway,
            onOpenViagensReview: (intent) => reviewIntent = intent,
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Editar'));
    await tester.pumpAndSettle();

    expect(find.text('1 de 2 passageiros elegíveis'), findsOneWidget);

    await tester.enterText(
      find.byKey(AdminServicoRecorrenteForm.searchPassengerFieldKey),
      'Bia',
    );
    await tester.pumpAndSettle();

    expect(find.text('Bia Portal'), findsOneWidget);
    expect(find.text('Aluno Portal'), findsNothing);

    await tester.tap(find.text('Selecionar visíveis'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Salvar serviço'));
    await tester.pumpAndSettle();

    expect(gateway.editedInput?.passageiroIds, [11, 12]);
    expect(gateway.editedInput?.nome, 'Linha Manhã');
    expect(gateway.editedInput?.destinoFinalNome, 'Escola Central');
    expect(gateway.editedInput?.destinoFinalEndereco, 'Rua Principal, 100');
    expect(gateway.editedInput?.destinoFinalLatitude, -30.01);
    expect(gateway.editedInput?.destinoFinalLongitude, -51.22);
    expect(find.text('Revisar viagens'), findsOneWidget);

    await tester.tap(find.text('Revisar viagens'));
    await tester.pump();

    expect(reviewIntent?.sourceLabel, 'SERVIÇO');
    expect(reviewIntent?.title, 'Revise Linha Manhã nas viagens');
  });
}

class _FakeServicosGatewayComDados implements AdminServicosRecorrentesGateway {
  const _FakeServicosGatewayComDados();

  @override
  Future<AdminServicoRecorrenteRecord> createServico(
    AdminServicoRecorrenteInput input,
  ) async => _servico;

  @override
  Future<void> deleteServico(int servicoId) async {}

  @override
  Future<AdminServicoRecorrenteRecord> editServico(
    int servicoId,
    AdminServicoRecorrenteInput input,
  ) async => _servico;

  @override
  Future<List<AdminMotoristaRecord>> fetchMotoristas() async => const [
    _motorista,
  ];

  @override
  Future<List<AdminPassageiroRecord>> fetchPassageiros() async => const [
    _passageiro,
  ];

  @override
  Future<List<AdminServicoRecorrenteRecord>> fetchServicos() async => const [
    _servico,
  ];
}

class _FakeServicosGatewayVazio implements AdminServicosRecorrentesGateway {
  AdminServicoRecorrenteInput? createdInput;
  List<AdminServicoRecorrenteRecord> servicos = const [];

  @override
  Future<AdminServicoRecorrenteRecord> createServico(
    AdminServicoRecorrenteInput input,
  ) async {
    createdInput = input;
    final created = AdminServicoRecorrenteRecord(
      id: 99,
      nome: input.nome,
      tipoOperacao: input.tipoOperacao,
      motoristaPadrao: null,
      quantidadePassageiros: input.passageiroIds.length,
      passageiroIds: input.passageiroIds,
      ativo: true,
      geracaoAutomaticaAtiva: input.geracaoAutomaticaAtiva,
      politicaPresenca: input.politicaPresenca,
      diasOperacao: input.diasOperacao,
    );
    servicos = [created];
    return created;
  }

  @override
  Future<void> deleteServico(int servicoId) async {}

  @override
  Future<AdminServicoRecorrenteRecord> editServico(
    int servicoId,
    AdminServicoRecorrenteInput input,
  ) async => servicos.first;

  @override
  Future<List<AdminMotoristaRecord>> fetchMotoristas() async => const [
    _motorista,
  ];

  @override
  Future<List<AdminPassageiroRecord>> fetchPassageiros() async => const [
    _passageiro,
  ];

  @override
  Future<List<AdminServicoRecorrenteRecord>> fetchServicos() async => servicos;
}

class _FakeServicosGatewayEditavel implements AdminServicosRecorrentesGateway {
  AdminServicoRecorrenteInput? editedInput;
  List<AdminServicoRecorrenteRecord> servicos = const [_servico];

  @override
  Future<AdminServicoRecorrenteRecord> createServico(
    AdminServicoRecorrenteInput input,
  ) async => _servico;

  @override
  Future<void> deleteServico(int servicoId) async {}

  @override
  Future<AdminServicoRecorrenteRecord> editServico(
    int servicoId,
    AdminServicoRecorrenteInput input,
  ) async {
    editedInput = input;
    final updated = AdminServicoRecorrenteRecord(
      id: servicoId,
      nome: input.nome,
      tipoOperacao: input.tipoOperacao,
      motoristaPadrao: _servico.motoristaPadrao,
      quantidadePassageiros: input.passageiroIds.length,
      passageiroIds: input.passageiroIds,
      ativo: _servico.ativo,
      geracaoAutomaticaAtiva: input.geracaoAutomaticaAtiva,
      politicaPresenca: input.politicaPresenca,
      diasOperacao: input.diasOperacao,
      horarioPartida: input.horarioPartida,
      destinoFinalNome: input.destinoFinalNome,
      destinoFinalEndereco: input.destinoFinalEndereco,
      destinoFinalLatitude: input.destinoFinalLatitude,
      destinoFinalLongitude: input.destinoFinalLongitude,
      capacidadePlanejada: input.capacidadePlanejada,
    );
    servicos = [updated];
    return updated;
  }

  @override
  Future<List<AdminMotoristaRecord>> fetchMotoristas() async => const [
    _motorista,
  ];

  @override
  Future<List<AdminPassageiroRecord>> fetchPassageiros() async => const [
    _passageiro,
    _passageiroBia,
  ];

  @override
  Future<List<AdminServicoRecorrenteRecord>> fetchServicos() async => servicos;
}

const _motorista = AdminMotoristaRecord(
  id: 7,
  nome: 'Motorista Portal',
  email: 'motorista@vanrides.dev',
  telefone: '51999999999',
);

const _passageiro = AdminPassageiroRecord(
  id: 11,
  nome: 'Aluno Portal',
  email: 'aluno@vanrides.dev',
  telefone: '51999999998',
  menorDeIdade: true,
);

const _passageiroBia = AdminPassageiroRecord(
  id: 12,
  nome: 'Bia Portal',
  email: 'bia@vanrides.dev',
  telefone: '51999999997',
  menorDeIdade: false,
);

const _servico = AdminServicoRecorrenteRecord(
  id: 15,
  nome: 'Linha Manhã',
  tipoOperacao: 'IDA',
  motoristaPadrao: null,
  quantidadePassageiros: 1,
  passageiroIds: [11],
  ativo: true,
  geracaoAutomaticaAtiva: true,
  politicaPresenca: 'INCLUIR_POR_PADRAO',
  diasOperacao: ['SEG', 'TER', 'QUA', 'QUI', 'SEX'],
  destinoFinalNome: 'Escola Central',
  destinoFinalEndereco: 'Rua Principal, 100',
  destinoFinalLatitude: -30.01,
  destinoFinalLongitude: -51.22,
);
