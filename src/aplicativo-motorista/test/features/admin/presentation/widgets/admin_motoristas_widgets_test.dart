import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_motorista_record.dart';
import 'package:vanrides_frontend_dev/features/admin/presentation/widgets/admin_motorista_detail_widgets.dart';
import 'package:vanrides_frontend_dev/features/admin/presentation/widgets/admin_motoristas_widgets.dart';

void main() {
  testWidgets('listagem renderiza motoristas e dispara selecao/refresh', (
    tester,
  ) async {
    AdminMotoristaRecord? selected;
    var refreshTaps = 0;

    await tester.pumpWidget(
      _panelApp(
        selectedMotorista: _motoristaA,
        onSelect: (motorista) => selected = motorista,
        onRefresh: () => refreshTaps++,
      ),
    );

    expect(find.text('Motoristas da empresa'), findsOneWidget);
    expect(find.text('Motorista A'), findsOneWidget);
    expect(find.text('(51) 99999-0001'), findsOneWidget);
    final emailText = tester.widget<Text>(find.text('a@vanrides.dev'));
    expect(emailText.maxLines, 1);
    expect(emailText.overflow, TextOverflow.ellipsis);
    expect(find.text('SELECIONADO'), findsOneWidget);
    expect(find.text('VINCULADO'), findsOneWidget);
    expect(find.text('Pronto para escala'), findsNWidgets(2));
    expect(find.text('Atualizar'), findsOneWidget);
    expect(find.text('PRÓXIMO PASSO'), findsOneWidget);
    expect(find.text('Ver ficha'), findsNWidgets(2));
    expect(find.byIcon(Icons.more_horiz), findsNothing);
    expect(find.byIcon(Icons.chevron_right), findsNothing);
    expect(find.text('2 motorista(s) vinculado(s) à empresa'), findsOneWidget);

    await tester.tap(find.text('Ver ficha').last);
    await tester.pumpAndSettle();

    expect(selected, _motoristaB);

    await tester.tap(find.text('Atualizar'));
    await tester.pumpAndSettle();

    expect(refreshTaps, 1);
  });

  testWidgets('listagem vazia mostra CTA de cadastro manual', (tester) async {
    var createTaps = 0;

    await tester.pumpWidget(
      _panelApp(
        motoristas: const [],
        selectedMotorista: null,
        onCreate: () => createTaps++,
      ),
    );

    expect(find.text('Nenhum motorista vinculado à empresa.'), findsOneWidget);

    await tester.tap(find.text('Cadastrar manualmente'));
    await tester.pumpAndSettle();

    expect(createTaps, 1);
  });

  testWidgets('detalhe renderiza motorista e dispara acoes', (tester) async {
    await tester.binding.setSurfaceSize(const Size(720, 900));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    var editTaps = 0;
    var deleteTaps = 0;

    await tester.pumpWidget(
      _detailApp(
        AdminMotoristaDetailPanel(
          motorista: _motoristaA,
          onEdit: () => editTaps++,
          onDelete: () => deleteTaps++,
        ),
      ),
    );

    expect(find.text('Motorista A'), findsOneWidget);
    expect(find.text('Próxima ação'), findsOneWidget);
    expect(find.text('Escalar em Serviços ou Viagens'), findsOneWidget);
    expect(find.text('Dados administrativos'), findsOneWidget);
    expect(find.text('ID: 1'), findsNothing);
    expect(find.text('Nascimento: 14/03/1992, 34 anos'), findsNothing);

    await tester.ensureVisible(find.text('Dados administrativos'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Dados administrativos'));
    await tester.pumpAndSettle();

    expect(find.text('ID: 1'), findsOneWidget);
    expect(find.text('Nascimento: 14/03/1992, 34 anos'), findsOneWidget);
    expect(find.text('Editar dados'), findsOneWidget);

    await tester.ensureVisible(find.text('Editar dados'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Editar dados'));
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.text('Remover motorista'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Remover motorista'));
    await tester.pumpAndSettle();

    expect(editTaps, 1);
    expect(deleteTaps, 1);
  });

  testWidgets('detalhe guia cadastro incompleto do motorista', (tester) async {
    await tester.binding.setSurfaceSize(const Size(720, 900));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    await tester.pumpWidget(
      _detailApp(
        AdminMotoristaDetailPanel(
          motorista: _motoristaIncomplete,
          onEdit: () {},
          onDelete: () {},
        ),
      ),
    );

    expect(find.text('Perfil operacional incompleto'), findsOneWidget);
    expect(find.text('Próxima ação'), findsOneWidget);
    expect(find.text('Completar cadastro'), findsOneWidget);
    expect(find.text('Informar nome, telefone e e-mail'), findsOneWidget);
    expect(find.text('E-mail para convite informado'), findsNothing);
    expect(find.text('Empresa vinculada ao portal'), findsOneWidget);
  });

  testWidgets('detalhe recomenda foto do motorista sem campo morto', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(720, 900));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    await tester.pumpWidget(
      _detailApp(
        AdminMotoristaDetailPanel(
          motorista: _motoristaB,
          onEdit: () {},
          onDelete: () {},
        ),
      ),
    );

    expect(find.text('Perfil operacional completo'), findsOneWidget);
    expect(find.text('Empresa vinculada ao portal'), findsOneWidget);
    expect(find.text('E-mail para convite informado'), findsOneWidget);
    expect(find.text('Foto recomendada no app'), findsOneWidget);
    expect(
      find.text(
        'Ação: orientar o motorista a atualizar a foto no app para facilitar identificação.',
      ),
      findsOneWidget,
    );
    expect(find.text('Editar cadastro'), findsNothing);
  });

  testWidgets('estado de erro permite tentar novamente', (tester) async {
    var retryTaps = 0;

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: AdminMotoristaErrorState(
            message: 'Nao foi possivel carregar motoristas.',
            onRetry: () async => retryTaps++,
          ),
        ),
      ),
    );

    expect(find.text('Nao foi possivel carregar motoristas.'), findsOneWidget);

    await tester.tap(find.text('Tentar novamente'));
    await tester.pumpAndSettle();

    expect(retryTaps, 1);
  });
}

Widget _panelApp({
  List<AdminMotoristaRecord> motoristas = _motoristas,
  AdminMotoristaRecord? selectedMotorista,
  ValueChanged<AdminMotoristaRecord>? onSelect,
  VoidCallback? onCreate,
  VoidCallback? onRefresh,
}) {
  return MaterialApp(
    home: Scaffold(
      body: SizedBox(
        width: 920,
        child: AdminMotoristasListPanel(
          motoristas: motoristas,
          selectedMotorista: selectedMotorista,
          onSelect: onSelect ?? (_) {},
          onCreate: onCreate ?? () {},
          onRefresh: onRefresh ?? () {},
        ),
      ),
    ),
  );
}

Widget _detailApp(Widget child) {
  return MaterialApp(
    home: Scaffold(
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(24),
        child: SizedBox(width: 420, child: child),
      ),
    ),
  );
}

const _motoristaA = AdminMotoristaRecord(
  id: 1,
  nome: 'Motorista A',
  email: 'a@vanrides.dev',
  telefone: '51999990001',
  dataNascimento: '1992-03-14',
);

const _motoristaB = AdminMotoristaRecord(
  id: 2,
  nome: 'Motorista B',
  email: 'b@vanrides.dev',
  telefone: '51999990002',
);

const _motoristaIncomplete = AdminMotoristaRecord(
  id: 3,
  nome: 'Motorista Incompleto',
  email: '',
  telefone: '',
);

const _motoristas = [_motoristaA, _motoristaB];
