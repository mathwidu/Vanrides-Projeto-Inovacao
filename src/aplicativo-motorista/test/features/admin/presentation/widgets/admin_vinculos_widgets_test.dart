import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_passageiro_record.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_responsavel_record.dart';
import 'package:vanrides_frontend_dev/features/admin/presentation/widgets/admin_vinculos_invite_creation_dialog.dart';
import 'package:vanrides_frontend_dev/features/admin/presentation/widgets/admin_vinculos_widgets.dart';

void main() {
  testWidgets('AdminVinculosInlineEmptyState renders title and body', (
    tester,
  ) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: AdminVinculosInlineEmptyState(
            title: 'Nenhum vínculo cadastrado',
            body: 'Convide um responsável ou associe um já cadastrado.',
          ),
        ),
      ),
    );

    expect(find.text('Nenhum vínculo cadastrado'), findsOneWidget);
    expect(
      find.text('Convide um responsável ou associe um já cadastrado.'),
      findsOneWidget,
    );
  });

  testWidgets('AdminVinculosMiniMetricCard renders metric label and value', (
    tester,
  ) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: AdminVinculosMiniMetricCard(label: 'Ativos', value: '3'),
        ),
      ),
    );

    expect(find.text('ATIVOS'), findsOneWidget);
    expect(find.text('3'), findsOneWidget);
  });

  testWidgets('AdminVinculosModeButton highlights active option', (
    tester,
  ) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: AdminVinculosModeButton(label: 'Escolar', active: true),
        ),
      ),
    );

    final text = tester.widget<Text>(find.text('Escolar'));
    expect(text.style?.fontWeight, FontWeight.w700);
  });

  testWidgets('AdminVinculosDashedHintCard renders icon and title', (
    tester,
  ) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: AdminVinculosDashedHintCard(
            icon: Icons.person_search_outlined,
            title: 'Selecione o Responsável',
          ),
        ),
      ),
    );

    expect(find.byIcon(Icons.person_search_outlined), findsOneWidget);
    expect(find.text('Selecione o Responsável'), findsOneWidget);
  });

  testWidgets(
    'AdminVinculosCreationPanel renders totals and dispatches create',
    (tester) async {
      await tester.binding.setSurfaceSize(const Size(900, 900));
      var createTaps = 0;

      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: SingleChildScrollView(
              child: SizedBox(
                width: 420,
                child: AdminVinculosCreationPanel(
                  passageiros: const [_passageiro],
                  responsaveis: const [_responsavel],
                  onCreateLink: () async => createTaps++,
                ),
              ),
            ),
          ),
        ),
      );

      expect(
        find.text('Ajuste técnico responsável-passageiro'),
        findsOneWidget,
      );
      expect(find.text('Revise a ficha do passageiro'), findsOneWidget);
      expect(find.text('Revise a ficha do responsável'), findsOneWidget);
      expect(find.text('Cadastros disponíveis'), findsOneWidget);
      expect(find.text('2'), findsOneWidget);

      await tester.tap(find.text('Registrar ajuste técnico'));
      await tester.pumpAndSettle();

      expect(createTaps, 1);
    },
  );

  testWidgets('AdminVinculosErrorState dispatches retry', (tester) async {
    var retryTaps = 0;

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: AdminVinculosErrorState(
            message: 'Falha ao carregar vínculos.',
            onRetry: () async => retryTaps++,
          ),
        ),
      ),
    );

    expect(find.text('Falha ao carregar vínculos.'), findsOneWidget);

    await tester.tap(find.text('Tentar novamente'));
    await tester.pumpAndSettle();

    expect(retryTaps, 1);
  });

  testWidgets('AdminVinculosCreateInviteCodeDialog returns parsed config', (
    tester,
  ) async {
    AdminVinculosInviteCreationConfig? config;

    await tester.pumpWidget(
      MaterialApp(
        home: Builder(
          builder: (context) {
            return Scaffold(
              body: Center(
                child: ElevatedButton(
                  onPressed: () async {
                    config =
                        await showDialog<AdminVinculosInviteCreationConfig>(
                          context: context,
                          builder:
                              (context) =>
                                  const AdminVinculosCreateInviteCodeDialog(),
                        );
                  },
                  child: const Text('Abrir convite'),
                ),
              ),
            );
          },
        ),
      ),
    );

    await tester.tap(find.text('Abrir convite'));
    await tester.pumpAndSettle();

    await tester.enterText(find.byType(TextFormField).at(0), '5');
    await tester.enterText(find.byType(TextFormField).at(1), '');
    await tester.tap(find.text('Gerar convite'));
    await tester.pumpAndSettle();

    expect(config?.maxUses, 5);
    expect(config?.expiresAt, isNull);
  });
}

const _passageiro = AdminPassageiroRecord(
  id: 21,
  nome: 'Aluno Portal',
  email: 'aluno@vanrides.dev',
  telefone: '51977777777',
  menorDeIdade: true,
);

const _responsavel = AdminResponsavelRecord(
  id: 31,
  nome: 'Responsavel Portal',
  email: 'responsavel@vanrides.dev',
  telefone: '51999999999',
);
