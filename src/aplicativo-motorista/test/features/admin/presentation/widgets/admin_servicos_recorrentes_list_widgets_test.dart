import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_servico_recorrente_record.dart';
import 'package:vanrides_frontend_dev/features/admin/presentation/widgets/admin_servicos_recorrentes_list_widgets.dart';

void main() {
  testWidgets('header renders service table labels', (tester) async {
    await tester.pumpWidget(
      const MaterialApp(home: Scaffold(body: AdminServicosRecorrentesHeader())),
    );

    expect(find.text('SERVIÇO'), findsOneWidget);
    expect(find.text('MOTORISTA'), findsOneWidget);
    expect(find.text('POLÍTICA'), findsOneWidget);
    expect(find.text('DIAS'), findsOneWidget);
    expect(find.text('PRÓXIMO PASSO'), findsOneWidget);
  });

  testWidgets('row renders labels and dispatches actions', (tester) async {
    var opened = 0;
    var edited = 0;
    var deleted = 0;

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: SizedBox(
            width: 1100,
            child: AdminServicoRecorrenteRow(
              servico: _servico,
              politicaPresencaLabel: 'Incluir por padrão',
              horarioPartidaLabel: '07:15',
              onOpen: () => opened++,
              onEdit: () => edited++,
              onDelete: () => deleted++,
            ),
          ),
        ),
      ),
    );

    expect(find.text('Linha Manhã'), findsOneWidget);
    expect(find.text('1 passageiros elegíveis • 07:15'), findsOneWidget);
    expect(find.text('Escala por viagem'), findsOneWidget);
    expect(find.text('Incluir por padrão'), findsOneWidget);
    expect(find.text('SEG, TER'), findsOneWidget);

    expect(find.text('Abrir'), findsOneWidget);
    expect(find.text('Editar'), findsOneWidget);
    expect(find.text('Inativar'), findsOneWidget);
    expect(find.byIcon(Icons.visibility_outlined), findsNothing);

    await tester.tap(find.text('Abrir'));
    await tester.tap(find.text('Editar'));
    await tester.tap(find.text('Inativar'));

    expect(opened, 1);
    expect(edited, 1);
    expect(deleted, 1);
  });

  testWidgets('empty state renders copy and dispatches create action', (
    tester,
  ) async {
    var created = 0;

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: AdminServicosRecorrentesEmptyState(onCreate: () => created++),
        ),
      ),
    );

    expect(find.text('Nenhum serviço recorrente cadastrado'), findsOneWidget);
    expect(find.text('Criar primeiro serviço'), findsOneWidget);

    await tester.tap(find.text('Criar primeiro serviço'));

    expect(created, 1);
  });
}

const _servico = AdminServicoRecorrenteRecord(
  id: 15,
  nome: 'Linha Manhã',
  tipoOperacao: 'IDA',
  quantidadePassageiros: 1,
  passageiroIds: [11],
  ativo: true,
  geracaoAutomaticaAtiva: true,
  politicaPresenca: 'INCLUIR_POR_PADRAO',
  diasOperacao: ['SEG', 'TER'],
);
