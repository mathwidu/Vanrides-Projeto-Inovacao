import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_passageiro_record.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_responsavel_record.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_servico_recorrente_record.dart';
import 'package:vanrides_frontend_dev/features/admin/presentation/widgets/admin_passageiros_widgets.dart';
import 'package:vanrides_frontend_dev/features/service_presence/domain/passenger_available_service_record.dart';

void main() {
  testWidgets('AdminPassageirosListPanel renders selected passenger actions', (
    tester,
  ) async {
    AdminPassageiroRecord? selected;
    var refreshed = false;
    var edited = false;
    var reassigned = false;

    await tester.binding.setSurfaceSize(const Size(1280, 900));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: SizedBox(
            width: 1280,
            child: AdminPassageirosListPanel(
              passageiros: const [_activePassenger, _pendingPassenger],
              selectedPassageiro: _activePassenger,
              onRefresh: () async => refreshed = true,
              onCreate: () async {},
              onEdit: () async => edited = true,
              onReassign: () async => reassigned = true,
              onSelect: (passageiro) => selected = passageiro,
            ),
          ),
        ),
      ),
    );

    expect(find.text('Lista de Passageiros'), findsOneWidget);
    expect(find.text('Atualizar'), findsOneWidget);
    expect(find.text('PRÓXIMO PASSO'), findsOneWidget);
    expect(find.text('Cadastrar passageiro'), findsNothing);
    expect(find.byIcon(Icons.more_vert), findsNothing);
    expect(find.text('Aluno Ativo'), findsOneWidget);
    expect(find.text('Motorista: Motorista Portal'), findsOneWidget);
    expect(
      find.text('16 anos, menor de idade • ativo@vanrides.dev'),
      findsOneWidget,
    );
    expect(find.text('ATIVO'), findsOneWidget);
    expect(find.text('2'), findsNothing);
    expect(find.text('3'), findsNothing);

    await tester.tap(find.text('Aluno Pendente'));
    await tester.pump();
    expect(selected?.id, _pendingPassenger.id);

    expect(find.text('Editar'), findsNWidgets(2));
    expect(find.text('Motorista'), findsNWidgets(2));

    await tester.tap(find.text('Atualizar'));
    await tester.tap(find.text('Editar').first);
    await tester.tap(find.text('Motorista').first);
    await tester.pump();

    expect(refreshed, isTrue);
    expect(edited, isTrue);
    expect(reassigned, isTrue);
  });

  testWidgets('AdminPassageirosListPanel shows pending passenger state', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: AdminPassageirosListPanel(
            passageiros: const [_pendingPassenger],
            selectedPassageiro: _pendingPassenger,
            onRefresh: () async {},
            onCreate: () async {},
            onEdit: () async {},
            onReassign: () async {},
            onSelect: (_) {},
          ),
        ),
      ),
    );

    expect(find.text('Sem atalho'), findsOneWidget);
    expect(find.text('Libere serviços'), findsOneWidget);
    expect(find.text('PENDENTE'), findsOneWidget);
  });

  testWidgets('AdminPassageirosListPanel keeps empty state refreshable', (
    tester,
  ) async {
    var refreshed = false;
    var created = false;

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: AdminPassageirosListPanel(
            passageiros: const [],
            selectedPassageiro: null,
            onRefresh: () async => refreshed = true,
            onCreate: () async => created = true,
            onEdit: () async {},
            onReassign: () async {},
            onSelect: (_) {},
          ),
        ),
      ),
    );

    expect(
      find.text('Nenhum passageiro encontrado para esta empresa.'),
      findsOneWidget,
    );

    expect(find.text('Cadastrar passageiro'), findsOneWidget);

    await tester.tap(find.text('Atualizar'));
    await tester.tap(find.text('Cadastrar passageiro'));
    await tester.pump();

    expect(refreshed, isTrue);
    expect(created, isTrue);
  });

  testWidgets('AdminPassageirosActivitySection reflects selected passenger', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1280, 1300));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    var editedServices = false;
    var managedGuardians = false;

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: AdminPassageirosActivitySection(
            selectedPassageiro: _pendingPassenger,
            servicos: _servicos,
            onEditServicos: () async => editedServices = true,
            onManageResponsaveis: () async => managedGuardians = true,
          ),
        ),
      ),
    );

    expect(find.text('Ficha do passageiro'), findsOneWidget);
    expect(find.text('Atividades Recentes'), findsNothing);
    expect(find.text('Próxima ação'), findsOneWidget);
    expect(find.text('Pronto para viagens recorrentes'), findsOneWidget);
    expect(find.text('Sem motorista de referência'), findsOneWidget);
    expect(
      find.text('Serviços liberados definem a viagem do dia.'),
      findsOneWidget,
    );
    expect(find.text('Serviços do passageiro'), findsOneWidget);
    expect(find.text('Ida manhã'), findsNothing);

    await tester.tap(find.text('Serviços do passageiro'));
    await tester.pumpAndSettle();

    expect(
      find.text('1 serviço recorrente liberado para presença e viagens.'),
      findsOneWidget,
    );
    expect(find.text('Ida manhã'), findsOneWidget);
    expect(find.text('07:30 • Ida • entra por padrão'), findsOneWidget);
    expect(find.text('Responsáveis vinculados'), findsOneWidget);
    expect(find.text('Responsavel Portal'), findsNothing);

    await tester.tap(find.text('Responsáveis vinculados'));
    await tester.pumpAndSettle();

    expect(
      find.text('1 responsável pode acompanhar presença, rota e comunicados.'),
      findsOneWidget,
    );
    expect(find.text('Responsavel Portal'), findsOneWidget);
    expect(find.text('pendente@vanrides.dev'), findsNothing);
    expect(find.text('51999999997'), findsNothing);

    await tester.tap(find.text('Dados administrativos'));
    await tester.pumpAndSettle();

    expect(find.text('pendente@vanrides.dev'), findsOneWidget);
    expect(find.text('51999999997'), findsOneWidget);
    expect(
      find.text('Nascimento: 01/01/1998, 28 anos (maior)'),
      findsOneWidget,
    );
    expect(find.text('1 responsável vinculado'), findsOneWidget);
    expect(find.text('Há 5 min'), findsNothing);
    expect(find.text('Notificar Responsável'), findsNothing);

    await tester.tap(find.text('Editar serviços'));
    await tester.pump();
    expect(editedServices, isTrue);

    await tester.ensureVisible(find.text('Gerenciar responsáveis'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Gerenciar responsáveis'));
    await tester.pump();
    expect(managedGuardians, isTrue);
  });

  testWidgets('AdminPassageirosActivitySection guia perfil incompleto', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1280, 1200));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    var editedProfile = false;

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: AdminPassageirosActivitySection(
            selectedPassageiro: _incompletePassenger,
            servicos: _servicos,
            onEditProfile: () async => editedProfile = true,
          ),
        ),
      ),
    );

    expect(find.text('Perfil operacional incompleto'), findsOneWidget);
    expect(find.text('Próxima ação'), findsOneWidget);
    expect(find.text('Completar cadastro'), findsOneWidget);
    expect(find.text('Informar data de nascimento'), findsOneWidget);
    expect(find.text('Liberar pelo menos um serviço'), findsOneWidget);
    expect(find.text('Editar cadastro'), findsOneWidget);

    await tester.tap(find.text('Editar cadastro'));
    await tester.pump();

    expect(editedProfile, isTrue);
  });

  testWidgets('AdminPassageirosActivitySection libera servicos pela ficha', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1280, 1100));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    var editedServices = false;

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: AdminPassageirosActivitySection(
            selectedPassageiro: _passengerWithoutServices,
            servicos: _servicos,
            onEditServicos: () async => editedServices = true,
          ),
        ),
      ),
    );

    expect(find.text('Perfil operacional incompleto'), findsOneWidget);
    expect(find.text('Liberar pelo menos um serviço'), findsOneWidget);
    expect(find.text('Liberar serviços'), findsWidgets);
    expect(
      find.text('Ação: libere um serviço recorrente nesta ficha.'),
      findsOneWidget,
    );

    await tester.tap(find.widgetWithText(OutlinedButton, 'Liberar serviços'));
    await tester.pump();

    expect(editedServices, isTrue);
  });

  testWidgets('AdminPassageirosActivitySection vincula responsavel do menor', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1280, 1200));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    var managedGuardians = false;

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: AdminPassageirosActivitySection(
            selectedPassageiro: _minorWithoutGuardian,
            servicos: _servicos,
            onManageResponsaveis: () async => managedGuardians = true,
          ),
        ),
      ),
    );

    expect(find.text('Perfil operacional incompleto'), findsOneWidget);
    expect(find.text('Vincular responsável do menor'), findsOneWidget);
    expect(find.text('Vincular responsável'), findsWidgets);
    expect(
      find.text('Ação: vincule um responsável sem sair da ficha.'),
      findsOneWidget,
    );

    await tester.tap(
      find.widgetWithText(OutlinedButton, 'Vincular responsável'),
    );
    await tester.pump();

    expect(managedGuardians, isTrue);
  });

  testWidgets('AdminPassageirosActivitySection mostra presença de hoje', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1280, 1100));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    var reviewedTrips = false;

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: AdminPassageirosActivitySection(
            selectedPassageiro: _pendingPassenger,
            servicos: _servicos,
            presenceServices: _presenceServices,
            onReviewTrips: () => reviewedTrips = true,
          ),
        ),
      ),
    );

    expect(find.text('Presença de hoje'), findsOneWidget);
    expect(find.text('1 vou'), findsOneWidget);
    expect(find.text('1 sem decisão'), findsOneWidget);
    expect(find.text('Ida manhã'), findsWidgets);
    expect(find.text('07:30 • Ida • Vou'), findsOneWidget);
    expect(find.text('Volta tarde'), findsOneWidget);
    expect(find.text('17:40 • Volta • Sem decisão'), findsOneWidget);

    await tester.tap(find.text('Ver viagens de hoje'));
    await tester.pump();

    expect(reviewedTrips, isTrue);
  });

  testWidgets('AdminPassageirosActivitySection renders neutral empty copy', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(900, 900));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: AdminPassageirosActivitySection(selectedPassageiro: null),
        ),
      ),
    );

    expect(find.text('Ficha do passageiro'), findsOneWidget);
    expect(find.text('Atividades Recentes'), findsNothing);
    expect(
      find.text(
        'Selecione um passageiro para ver vínculo, contato e endereço.',
      ),
      findsOneWidget,
    );
    expect(find.text('Aguardando seleção'), findsNothing);
    expect(
      find.text('Sem passageiro selecionado para análise documental.'),
      findsNothing,
    );
  });
}

const _activePassenger = AdminPassageiroRecord(
  id: 1,
  nome: 'Aluno Ativo',
  email: 'ativo@vanrides.dev',
  telefone: '51999999998',
  menorDeIdade: true,
  dataNascimento: '2010-01-01',
  motoristaId: 7,
  motoristaNome: 'Motorista Portal',
  responsavelCount: 1,
);

const _pendingPassenger = AdminPassageiroRecord(
  id: 2,
  nome: 'Aluno Pendente',
  email: 'pendente@vanrides.dev',
  telefone: '51999999997',
  endereco: 'Rua Portal',
  bairro: 'Centro',
  cidade: 'Igrejinha',
  estado: 'RS',
  cep: '95650-000',
  menorDeIdade: false,
  dataNascimento: '1998-01-01',
  motoristaId: null,
  motoristaNome: null,
  responsavelCount: 1,
  responsaveis: [_guardian],
);

const _incompletePassenger = AdminPassageiroRecord(
  id: 3,
  nome: 'Aluno Incompleto',
  email: 'incompleto@vanrides.dev',
  telefone: '51999999996',
  menorDeIdade: false,
  dataNascimento: null,
  endereco: 'Rua A',
  bairro: 'Centro',
  cidade: 'Igrejinha',
  estado: 'RS',
  cep: '95650-000',
  motoristaId: 7,
  motoristaNome: 'Motorista Portal',
  responsavelCount: 0,
);

const _passengerWithoutServices = AdminPassageiroRecord(
  id: 4,
  nome: 'Aluno Sem Servico',
  email: 'sem.servico@vanrides.dev',
  telefone: '51999999995',
  endereco: 'Rua B',
  bairro: 'Centro',
  cidade: 'Igrejinha',
  estado: 'RS',
  cep: '95650-000',
  menorDeIdade: false,
  dataNascimento: '1999-02-03',
  responsavelCount: 0,
);

const _minorWithoutGuardian = AdminPassageiroRecord(
  id: 5,
  nome: 'Aluno Menor',
  email: 'menor@vanrides.dev',
  telefone: '51999999994',
  endereco: 'Rua C',
  bairro: 'Centro',
  cidade: 'Igrejinha',
  estado: 'RS',
  cep: '95650-000',
  menorDeIdade: true,
  dataNascimento: '2014-02-03',
  responsavelCount: 0,
);

const _guardian = AdminResponsavelRecord(
  id: 9,
  nome: 'Responsavel Portal',
  email: 'responsavel@vanrides.dev',
  telefone: '51999999997',
  passageiroCount: 1,
  acessoConfigurado: true,
);

const _servicos = [
  AdminServicoRecorrenteRecord(
    id: 10,
    nome: 'Ida manhã',
    tipoOperacao: 'IDA',
    quantidadePassageiros: 1,
    passageiroIds: [2],
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
    quantidadePassageiros: 0,
    passageiroIds: [],
    ativo: true,
    geracaoAutomaticaAtiva: true,
    politicaPresenca: 'EXIGE_CONFIRMACAO',
    diasOperacao: ['SEG', 'TER', 'QUA'],
    horarioPartida: '17:40',
  ),
];

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
