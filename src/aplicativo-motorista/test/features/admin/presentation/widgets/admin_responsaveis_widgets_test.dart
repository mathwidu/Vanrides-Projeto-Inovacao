import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_passageiro_linked_record.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_responsavel_record.dart';
import 'package:vanrides_frontend_dev/features/admin/presentation/widgets/admin_portal_layout.dart';
import 'package:vanrides_frontend_dev/features/admin/presentation/widgets/admin_responsaveis_widgets.dart';

void main() {
  testWidgets('lista renderiza responsavel selecionado e aciona callbacks', (
    tester,
  ) async {
    AdminResponsavelRecord? selected;
    var editCalls = 0;
    var deleteCalls = 0;
    var refreshCalls = 0;

    await tester.pumpWidget(
      _widgetApp(
        AdminResponsaveisListPanel(
          responsaveis: const [_responsavel, _responsavelAlternativo],
          selectedResponsavel: _responsavel,
          onRefresh: () async => refreshCalls++,
          onCreate: () async {},
          onEdit: () async => editCalls++,
          onDelete: () async => deleteCalls++,
          onSelect: (responsavel) => selected = responsavel,
        ),
      ),
    );

    expect(find.text('Responsavel Portal'), findsOneWidget);
    expect(find.text('Responsavel Alternativo'), findsOneWidget);
    expect(find.text('Mostrando 2 responsáveis'), findsOneWidget);
    expect(find.text('Cadastro direto ativo'), findsOneWidget);
    expect(find.textContaining('backend'), findsNothing);
    expect(find.textContaining('senha provisória'), findsNothing);
    expect(find.text('Ativos'), findsNothing);
    expect(find.text('Inativos'), findsNothing);
    expect(find.text('Filtrar'), findsNothing);
    expect(find.text('2'), findsNothing);

    await tester.tap(find.text('Responsavel Alternativo'));
    expect(selected, _responsavelAlternativo);

    await tester.tap(find.byIcon(Icons.edit_outlined).first);
    await tester.tap(find.byIcon(Icons.delete_outline).first);
    await tester.tap(find.byTooltip('Atualizar responsáveis'));

    expect(editCalls, 1);
    expect(deleteCalls, 1);
    expect(refreshCalls, 1);
  });

  testWidgets('detalhe renderiza vinculos e acoes do responsavel', (
    tester,
  ) async {
    var manageCalls = 0;
    var editCalls = 0;
    var deleteCalls = 0;

    await tester.pumpWidget(
      _widgetApp(
        AdminResponsavelDetailPanel(
          isLoading: false,
          responsavel: _responsavel,
          referenceDate: DateTime(2025, 9, 2),
          onManagePassageiros: () async => manageCalls++,
          onEdit: () async => editCalls++,
          onDelete: () async => deleteCalls++,
        ),
      ),
    );

    expect(find.text('Responsavel Portal'), findsOneWidget);
    expect(find.text('ACESSO ATIVO'), findsOneWidget);
    expect(find.text('PREMIUM'), findsNothing);
    expect(find.text('Acesso ao app configurado'), findsOneWidget);
    expect(find.text('Próxima ação'), findsOneWidget);
    expect(find.text('Revisar passageiros vinculados'), findsOneWidget);
    expect(find.text('Vínculos atuais'), findsOneWidget);
    expect(find.text('Aluno Portal'), findsOneWidget);
    expect(find.text('Filho • acompanha serviços liberados'), findsOneWidget);
    expect(find.text('Gerenciar passageiros'), findsOneWidget);
    expect(find.text('Notas Operacionais'), findsNothing);
    expect(find.text('Dados administrativos'), findsOneWidget);
    expect(find.text('responsavel@vanrides.dev'), findsNothing);
    expect(find.text('02/09/1980, 45 anos'), findsNothing);
    expect(find.text('1 passageiro vinculado'), findsOneWidget);

    final heroSurface = tester.widget<AdminPortalSurface>(
      find.byType(AdminPortalSurface).first,
    );
    expect(heroSurface.color, isNot(AdminPortalColors.blue));

    await tester.ensureVisible(find.text('Gerenciar passageiros'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Gerenciar passageiros'));
    await tester.pumpAndSettle();

    await tester.ensureVisible(find.text('Dados administrativos'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Dados administrativos'));
    await tester.pumpAndSettle();

    expect(find.text('responsavel@vanrides.dev'), findsOneWidget);
    expect(find.text('02/09/1980, 45 anos'), findsOneWidget);
    expect(find.text('Configurado'), findsOneWidget);

    await tester.ensureVisible(find.text('Editar responsável'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Editar responsável'));

    await tester.ensureVisible(find.text('Remover responsável'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Remover responsável'));

    expect(manageCalls, 1);
    expect(editCalls, 1);
    expect(deleteCalls, 1);
  });

  for (final scenario in [
    (DateTime(2025, 9, 1), '02/09/1980, 44 anos'),
    (DateTime(2025, 9, 2), '02/09/1980, 45 anos'),
    (DateTime(2026, 10, 30), '02/09/1980, 46 anos'),
  ]) {
    testWidgets('detalhe calcula idade na referencia ${scenario.$1}', (
      tester,
    ) async {
      await tester.pumpWidget(
        _widgetApp(
          AdminResponsavelDetailPanel(
            isLoading: false,
            responsavel: _responsavel,
            referenceDate: scenario.$1,
            onManagePassageiros: () async {},
            onEdit: () async {},
            onDelete: () async {},
          ),
        ),
      );

      expect(find.text(scenario.$2), findsNothing);
      await tester.ensureVisible(find.text('Dados administrativos'));
      await tester.tap(find.text('Dados administrativos'));
      await tester.pumpAndSettle();

      expect(find.text(scenario.$2), findsOneWidget);
    });
  }

  testWidgets('detalhe guia cadastro incompleto do responsavel', (
    tester,
  ) async {
    await tester.pumpWidget(
      _widgetApp(
        AdminResponsavelDetailPanel(
          isLoading: false,
          responsavel: _responsavelAlternativo,
          onManagePassageiros: () async {},
          onEdit: () async {},
          onDelete: () async {},
        ),
      ),
    );

    expect(find.text('Perfil operacional incompleto'), findsOneWidget);
    expect(find.text('Próxima ação'), findsOneWidget);
    expect(find.text('Completar cadastro'), findsOneWidget);
    expect(find.text('Informar data de nascimento'), findsOneWidget);
    expect(find.text('Vincular passageiro acompanhado'), findsOneWidget);
    expect(find.text('Configurar acesso de acompanhamento'), findsOneWidget);
  });

  testWidgets('detalhe vazio orienta selecao sem quebrar layout', (
    tester,
  ) async {
    await tester.pumpWidget(
      _widgetApp(
        AdminResponsavelDetailPanel(
          isLoading: false,
          responsavel: null,
          onManagePassageiros: () async {},
          onEdit: () async {},
          onDelete: () async {},
        ),
      ),
    );

    expect(
      find.text('Selecione um responsável para ver o detalhe administrativo.'),
      findsOneWidget,
    );
  });
}

Widget _widgetApp(Widget child) {
  return MaterialApp(
    home: Scaffold(
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(24),
        child: child,
      ),
    ),
  );
}

const _responsavel = AdminResponsavelRecord(
  id: 7,
  nome: 'Responsavel Portal',
  email: 'responsavel@vanrides.dev',
  telefone: '51999999997',
  dataNascimento: '1980-09-02',
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

const _responsavelAlternativo = AdminResponsavelRecord(
  id: 8,
  nome: 'Responsavel Alternativo',
  email: 'responsavel2@vanrides.dev',
  telefone: '51999999998',
  passageiroCount: 0,
  acessoConfigurado: false,
);
