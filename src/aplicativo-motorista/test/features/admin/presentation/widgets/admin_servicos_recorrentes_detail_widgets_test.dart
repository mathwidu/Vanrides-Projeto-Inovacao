import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_motorista_record.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_passageiro_record.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_servico_recorrente_record.dart';
import 'package:vanrides_frontend_dev/features/admin/presentation/widgets/admin_servicos_recorrentes_detail_widgets.dart';

void main() {
  testWidgets('Admin service detail widgets render operational cockpit', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1440, 1200));

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: SingleChildScrollView(
            child: Column(
              children: [
                const AdminServicoDetailHero(servico: _readyService),
                AdminServicoReadinessSurface(
                  servico: _readyService,
                  today: _friday,
                ),
                const AdminServicoChangeImpactNotice(),
                const AdminServicoRulesSurface(servico: _readyService),
                AdminServicoEligiblePassengersSurface(
                  passageiros: const [_passageiroMenor],
                  totalIds: _readyService.passageiroIds.length,
                ),
              ],
            ),
          ),
        ),
      ),
    );

    expect(find.text('Ativo'), findsOneWidget);
    expect(find.text('Volta'), findsWidgets);
    expect(find.text('Pronto'), findsOneWidget);
    expect(find.text('Escola Central'), findsWidgets);
    expect(find.text('07:30 • SEG, TER, QUA, SEX'), findsOneWidget);
    expect(find.text('Motorista Portal'), findsWidgets);
    expect(find.text('Prontidão operacional'), findsOneWidget);
    expect(find.text('Pode montar viagem hoje'), findsOneWidget);
    expect(find.text('Tudo pronto para gerar a viagem de hoje.'), findsWidgets);
    expect(
      find.text(
        'Alterações neste serviço valem para as próximas montagens. Se a viagem de hoje já foi criada, ajuste em Viagens do dia.',
      ),
      findsOneWidget,
    );
    expect(find.text('Regra operacional'), findsOneWidget);
    expect(find.text('Exige confirmação'), findsOneWidget);
    expect(find.text('12 lugares'), findsOneWidget);
    expect(find.text('Rota base #44'), findsOneWidget);
    expect(find.text('Aluno Portal'), findsOneWidget);
    expect(find.text('Menor'), findsOneWidget);
    expect(find.text('Endereço cadastrado'), findsOneWidget);
  });

  testWidgets('Admin service detail widgets render empty readiness fallbacks', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1440, 1200));

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: SingleChildScrollView(
            child: Column(
              children: [
                const AdminServicoDetailHero(servico: _emptyService),
                AdminServicoReadinessSurface(
                  servico: _emptyService,
                  today: _friday,
                ),
                const AdminServicoRulesSurface(servico: _emptyService),
                const AdminServicoEligiblePassengersSurface(
                  passageiros: [],
                  totalIds: 0,
                ),
              ],
            ),
          ),
        ),
      ),
    );

    expect(find.text('Sem agenda'), findsOneWidget);
    expect(find.text('Destino não publicado'), findsWidgets);
    expect(find.text('--:-- • sem dias definidos'), findsOneWidget);
    expect(find.text('Escala por viagem'), findsWidgets);
    expect(find.text('Sem elegibilidade'), findsOneWidget);
    expect(find.text('Sem dias'), findsOneWidget);
    expect(find.text('Ajuste antes de montar hoje'), findsOneWidget);
    expect(
      find.text('Escolha um motorista padrão ou escale na viagem do dia.'),
      findsOneWidget,
    );
    expect(
      find.text('Adicione ao menos um passageiro elegível.'),
      findsOneWidget,
    );
    expect(
      find.text('Informe destino final ou vincule uma rota-base.'),
      findsOneWidget,
    );
    expect(
      find.text('Hoje (SEX) não está nos dias de operação.'),
      findsOneWidget,
    );
    expect(find.text('Não informada'), findsWidgets);
    expect(find.text('0 vínculos'), findsOneWidget);
    expect(
      find.text('Nenhum passageiro encontrado para os vínculos deste serviço.'),
      findsOneWidget,
    );
  });

  testWidgets('Admin service detail widgets flag exceeded capacity', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1440, 1200));

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: SingleChildScrollView(
            child: AdminServicoReadinessSurface(
              servico: _overCapacityService,
              today: _friday,
            ),
          ),
        ),
      ),
    );

    expect(find.text('Capacidade excedida'), findsOneWidget);
    expect(
      find.text('Reduza elegíveis ou aumente a capacidade planejada.'),
      findsOneWidget,
    );
  });
}

const _motorista = AdminMotoristaRecord(
  id: 7,
  nome: 'Motorista Portal',
  email: 'motorista@vanrides.dev',
  telefone: '51999999999',
);

const _passageiroMenor = AdminPassageiroRecord(
  id: 11,
  nome: 'Aluno Portal',
  email: 'aluno@vanrides.dev',
  telefone: '51999999998',
  endereco: 'Rua Principal, 100',
  menorDeIdade: true,
);

const _readyService = AdminServicoRecorrenteRecord(
  id: 15,
  rotaBaseLegadaId: 44,
  nome: 'Linha Manhã',
  tipoOperacao: 'VOLTA',
  motoristaPadrao: _motorista,
  quantidadePassageiros: 1,
  passageiroIds: [11],
  ativo: true,
  geracaoAutomaticaAtiva: true,
  politicaPresenca: 'EXIGE_CONFIRMACAO',
  diasOperacao: ['SEG', 'TER', 'QUA', 'SEX'],
  horarioPartida: '07:30',
  destinoFinalNome: 'Escola Central',
  destinoFinalEndereco: 'Rua Principal, 100',
  destinoFinalLatitude: -30.01,
  destinoFinalLongitude: -51.22,
  capacidadePlanejada: 12,
);

const _emptyService = AdminServicoRecorrenteRecord(
  id: 16,
  nome: 'Linha sem escala',
  tipoOperacao: 'IDA',
  quantidadePassageiros: 0,
  passageiroIds: [],
  ativo: true,
  geracaoAutomaticaAtiva: true,
  politicaPresenca: 'INCLUIR_POR_PADRAO',
  diasOperacao: [],
);

const _overCapacityService = AdminServicoRecorrenteRecord(
  id: 17,
  rotaBaseLegadaId: 44,
  nome: 'Linha lotada',
  tipoOperacao: 'IDA',
  motoristaPadrao: _motorista,
  quantidadePassageiros: 3,
  passageiroIds: [11, 12, 13],
  ativo: true,
  geracaoAutomaticaAtiva: true,
  politicaPresenca: 'INCLUIR_POR_PADRAO',
  diasOperacao: ['SEX'],
  horarioPartida: '07:30',
  destinoFinalNome: 'Escola Central',
  capacidadePlanejada: 2,
);

final _friday = DateTime(2026, 6, 19);
