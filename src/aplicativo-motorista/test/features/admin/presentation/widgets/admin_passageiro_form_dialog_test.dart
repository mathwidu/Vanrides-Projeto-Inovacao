import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_motorista_record.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_passageiro_create_input.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_passageiro_record.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_servico_recorrente_record.dart';
import 'package:vanrides_frontend_dev/features/admin/presentation/widgets/admin_passageiro_form_dialog.dart';

void main() {
  testWidgets(
    'AdminPassageiroFormDialog envia input normalizado e servicos selecionados',
    (tester) async {
      AdminPassageiroCreateInput? submittedInput;
      List<int>? submittedServicoIds;
      AdminPassageiroRecord? dialogResult;

      await _pumpDialog(
        tester,
        onResult: (result) => dialogResult = result,
        onSubmit: (input, servicoIds) async {
          submittedInput = input;
          submittedServicoIds = servicoIds;
          return _createdPassageiro;
        },
      );

      await tester.tap(find.text('Abrir cadastro'));
      await tester.pumpAndSettle();

      expect(find.text('Novo passageiro'), findsOneWidget);
      expect(find.text('Serviços liberados no cadastro'), findsOneWidget);

      await tester.tap(find.text('07:30 Ida • Ida manhã'));
      await tester.tap(find.text('17:40 Volta • Volta tarde'));
      await tester.pump();

      await tester.enterText(
        find.widgetWithText(TextFormField, 'Nome completo'),
        ' Aluno Novo ',
      );
      await tester.enterText(
        find.widgetWithText(TextFormField, 'E-mail'),
        'ALUNO.NOVO@VANRIDES.DEV',
      );
      await tester.enterText(
        find.widgetWithText(TextFormField, 'Telefone'),
        '51999999991',
      );
      await tester.enterText(
        find.widgetWithText(TextFormField, 'Data de nascimento'),
        '20/05/2010',
      );
      await tester.pump();

      expect(find.text('Menor de idade'), findsOneWidget);

      await tester.enterText(
        find.widgetWithText(TextFormField, 'Endereço'),
        'Rua Nova',
      );
      await tester.enterText(
        find.widgetWithText(TextFormField, 'Bairro'),
        'Centro',
      );
      await tester.enterText(
        find.widgetWithText(TextFormField, 'Cidade'),
        'Porto Alegre',
      );
      await tester.enterText(
        find.widgetWithText(TextFormField, 'Estado'),
        'RS',
      );
      await tester.enterText(
        find.widgetWithText(TextFormField, 'CEP'),
        '90000-002',
      );
      await tester.enterText(
        find.widgetWithText(TextFormField, 'Senha'),
        'Portal!123',
      );
      await tester.enterText(
        find.widgetWithText(TextFormField, 'Confirmar senha'),
        'Portal!123',
      );

      await tester.ensureVisible(find.text('Salvar passageiro'));
      await tester.tap(find.text('Salvar passageiro'));
      await tester.pumpAndSettle();

      expect(submittedInput?.nome, 'Aluno Novo');
      expect(submittedInput?.email, 'aluno.novo@vanrides.dev');
      expect(submittedInput?.dataNascimento, '2010-05-20');
      expect(submittedInput?.menorDeIdade, isTrue);
      expect(submittedInput?.motoristaId, isNull);
      expect(submittedInput?.servicoIds, [10, 11]);
      expect(submittedServicoIds, [10, 11]);
      expect(dialogResult, _createdPassageiro);
    },
  );

  testWidgets('AdminPassageiroFormDialog bloqueia senha fraca', (tester) async {
    var submitCount = 0;

    await _pumpDialog(
      tester,
      onSubmit: (input, servicoIds) async {
        submitCount++;
        return _createdPassageiro;
      },
    );

    await tester.tap(find.text('Abrir cadastro'));
    await tester.pumpAndSettle();

    await tester.enterText(
      find.widgetWithText(TextFormField, 'Nome completo'),
      'Aluno Novo',
    );
    await tester.enterText(
      find.widgetWithText(TextFormField, 'E-mail'),
      'aluno.novo@vanrides.dev',
    );
    await tester.enterText(
      find.widgetWithText(TextFormField, 'Telefone'),
      '51999999991',
    );
    await tester.enterText(
      find.widgetWithText(TextFormField, 'Data de nascimento'),
      '20/05/2010',
    );
    await tester.enterText(
      find.widgetWithText(TextFormField, 'Endereço'),
      'Rua Nova',
    );
    await tester.enterText(find.widgetWithText(TextFormField, 'Bairro'), 'Sul');
    await tester.enterText(
      find.widgetWithText(TextFormField, 'Cidade'),
      'Porto Alegre',
    );
    await tester.enterText(find.widgetWithText(TextFormField, 'Estado'), 'RS');
    await tester.enterText(
      find.widgetWithText(TextFormField, 'CEP'),
      '90000-002',
    );
    await tester.enterText(
      find.widgetWithText(TextFormField, 'Senha'),
      'fraca',
    );
    await tester.enterText(
      find.widgetWithText(TextFormField, 'Confirmar senha'),
      'fraca',
    );

    await tester.ensureVisible(find.text('Salvar passageiro'));
    await tester.tap(find.text('Salvar passageiro'));
    await tester.pumpAndSettle();

    expect(
      find.text('A senha precisa seguir o padrão forte do projeto.'),
      findsOneWidget,
    );
    expect(submitCount, 0);
  });
}

Future<void> _pumpDialog(
  WidgetTester tester, {
  required Future<AdminPassageiroRecord> Function(
    AdminPassageiroCreateInput input,
    List<int> servicoIds,
  )
  onSubmit,
  ValueChanged<AdminPassageiroRecord?>? onResult,
}) async {
  await tester.binding.setSurfaceSize(const Size(900, 1200));
  addTearDown(() => tester.binding.setSurfaceSize(null));

  await tester.pumpWidget(
    MaterialApp(
      home: Builder(
        builder:
            (context) => Scaffold(
              body: Center(
                child: FilledButton(
                  onPressed: () async {
                    final result = await showDialog<AdminPassageiroRecord>(
                      context: context,
                      builder:
                          (context) => AdminPassageiroFormDialog(
                            title: 'Novo passageiro',
                            subtitle: 'Cadastre a pessoa no portal.',
                            motoristas: _motoristas,
                            servicos: _servicos,
                            onSubmit: onSubmit,
                          ),
                    );
                    onResult?.call(result);
                  },
                  child: const Text('Abrir cadastro'),
                ),
              ),
            ),
      ),
    ),
  );
}

const _motoristas = [
  AdminMotoristaRecord(
    id: 7,
    nome: 'Motorista Portal',
    email: 'portal@vanrides.dev',
    telefone: '51999999999',
  ),
];

const _servicos = [
  AdminServicoRecorrenteRecord(
    id: 10,
    nome: 'Ida manhã',
    tipoOperacao: 'IDA',
    quantidadePassageiros: 4,
    passageiroIds: [21, 22, 23, 24],
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
    quantidadePassageiros: 3,
    passageiroIds: [21, 22, 23],
    ativo: true,
    geracaoAutomaticaAtiva: true,
    politicaPresenca: 'EXIGE_CONFIRMACAO',
    diasOperacao: ['SEG', 'TER', 'QUA'],
    horarioPartida: '17:40',
  ),
];

const _createdPassageiro = AdminPassageiroRecord(
  id: 42,
  nome: 'Aluno Novo',
  email: 'aluno.novo@vanrides.dev',
  telefone: '51999999991',
  endereco: 'Rua Nova',
  bairro: 'Centro',
  cidade: 'Porto Alegre',
  estado: 'RS',
  cep: '90000-002',
  menorDeIdade: false,
  motoristaId: null,
  motoristaNome: null,
  responsavelCount: 0,
);
