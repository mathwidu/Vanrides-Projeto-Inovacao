import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_passageiro_record.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_servico_recorrente_record.dart';
import 'package:vanrides_frontend_dev/features/admin/presentation/widgets/admin_vinculos_service_widgets.dart';

void main() {
  testWidgets('painel renderiza direitos por servico e aciona edicao', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(900, 900));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    AdminPassageiroRecord? editedPassageiro;

    await tester.pumpWidget(
      _wrap(
        AdminVinculosPassageiroServicosPanel(
          passageiros: const [_passageiroComServico],
          servicos: const [_servicoIda, _servicoVolta],
          onEdit: (passageiro) async {
            editedPassageiro = passageiro;
          },
        ),
      ),
    );

    expect(find.text('Direitos por serviço'), findsOneWidget);
    expect(find.text('Aluno Portal'), findsOneWidget);
    expect(find.text('1 de 2 serviços liberados'), findsOneWidget);
    expect(find.text('07:30 Ida • Linha Escola'), findsOneWidget);

    await tester.tap(find.byTooltip('Editar serviços de Aluno Portal'));
    await tester.pump();

    expect(editedPassageiro?.id, 21);
  });

  testWidgets(
    'painel mostra tag de sem acesso quando passageiro nao tem servico',
    (tester) async {
      await tester.binding.setSurfaceSize(const Size(900, 900));
      addTearDown(() => tester.binding.setSurfaceSize(null));

      await tester.pumpWidget(
        _wrap(
          AdminVinculosPassageiroServicosPanel(
            passageiros: const [_passageiroSemServico],
            servicos: const [_servicoIda],
            onEdit: (_) async {},
          ),
        ),
      );

      expect(find.text('Aluno Sem Linha'), findsOneWidget);
      expect(find.text('Sem serviço liberado'), findsOneWidget);
      expect(find.text('Sem acesso operacional'), findsOneWidget);
    },
  );

  testWidgets('dialogo salva IDs selecionados na ordem dos servicos', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(900, 900));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    List<int>? selectedIds;

    await tester.pumpWidget(
      MaterialApp(
        home: Builder(
          builder: (context) {
            return Scaffold(
              body: Center(
                child: ElevatedButton(
                  onPressed: () async {
                    selectedIds = await showDialog<List<int>>(
                      context: context,
                      builder:
                          (context) =>
                              const AdminVinculosPassageiroServicosDialog(
                                passageiro: _passageiroComServico,
                                servicos: [_servicoIda, _servicoVolta],
                                initialServicoIds: {12},
                              ),
                    );
                  },
                  child: const Text('Abrir serviços'),
                ),
              ),
            );
          },
        ),
      ),
    );

    await tester.tap(find.text('Abrir serviços'));
    await tester.pumpAndSettle();

    expect(find.text('Serviços liberados para Aluno Portal'), findsOneWidget);

    await tester.tap(find.text('Linha Escola'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Salvar serviços'));
    await tester.pumpAndSettle();

    expect(selectedIds, [11, 12]);
  });
}

Widget _wrap(Widget child) {
  return MaterialApp(
    home: Scaffold(
      body: SingleChildScrollView(child: SizedBox(width: 900, child: child)),
    ),
  );
}

const _passageiroComServico = AdminPassageiroRecord(
  id: 21,
  nome: 'Aluno Portal',
  email: 'aluno@vanrides.dev',
  telefone: '51999999999',
  menorDeIdade: true,
);

const _passageiroSemServico = AdminPassageiroRecord(
  id: 22,
  nome: 'Aluno Sem Linha',
  email: 'sem.linha@vanrides.dev',
  telefone: '51988888888',
  menorDeIdade: true,
);

const _servicoIda = AdminServicoRecorrenteRecord(
  id: 11,
  nome: 'Linha Escola',
  tipoOperacao: 'IDA',
  quantidadePassageiros: 1,
  passageiroIds: [21],
  ativo: true,
  geracaoAutomaticaAtiva: true,
  politicaPresenca: 'INCLUIR_POR_PADRAO',
  diasOperacao: ['MONDAY'],
  horarioPartida: '07:30',
);

const _servicoVolta = AdminServicoRecorrenteRecord(
  id: 12,
  nome: 'Linha Volta',
  tipoOperacao: 'VOLTA',
  quantidadePassageiros: 2,
  passageiroIds: [30, 31],
  ativo: true,
  geracaoAutomaticaAtiva: true,
  politicaPresenca: 'INCLUIR_POR_PADRAO',
  diasOperacao: ['MONDAY'],
  horarioPartida: '17:40',
);
