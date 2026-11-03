import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_motorista_record.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_rota_base_record.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_servico_recorrente_record.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_viagem_create_input.dart';
import 'package:vanrides_frontend_dev/features/admin/presentation/widgets/admin_viagens_create_widgets.dart';

void main() {
  testWidgets('seleciona servico e motorista e emite input de criacao', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1440, 1400));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    AdminViagemCreateInput? submitted;

    await tester.pumpWidget(
      _wrap(
        AdminViagemCreateView(
          rotas: const [_sampleRota],
          servicos: const [_sampleServicoSemMotorista],
          motoristas: const [_sampleMotorista],
          isSubmitting: false,
          onCancel: () {},
          onSubmit: (input) => submitted = input,
        ),
      ),
    );

    await tester.tap(find.byType(DropdownButtonFormField<int>).first);
    await tester.pumpAndSettle();
    await tester.tap(find.text('Linha Escola • Ida').last);
    await tester.pumpAndSettle();

    await tester.tap(find.byType(DropdownButtonFormField<int>).last);
    await tester.pumpAndSettle();
    await tester.tap(find.text('Motorista Portal').last);
    await tester.pumpAndSettle();

    expect(find.text('Exceção do dia'), findsWidgets);
    expect(
      find.textContaining('Se o serviço já tem viagem planejada'),
      findsOneWidget,
    );

    await tester.tap(find.text('Criar exceção').last);
    await tester.pump();

    expect(submitted?.servicoId, 41);
    expect(submitted?.motoristaId, 7);
    expect(submitted?.rotaBaseId, isNull);
    expect(submitted?.dataOperacao, isNotNull);
  });

  testWidgets('usa fallback legado por rota-base quando nao ha servicos', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1440, 1400));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    AdminViagemCreateInput? submitted;

    await tester.pumpWidget(
      _wrap(
        AdminViagemCreateView(
          rotas: const [_sampleRota],
          servicos: const [],
          motoristas: const [_sampleMotorista],
          isSubmitting: false,
          onCancel: () {},
          onSubmit: (input) => submitted = input,
        ),
      ),
    );

    expect(find.text('Fallback legado por rota-base'), findsOneWidget);

    await tester.tap(find.byType(DropdownButtonFormField<int>).at(1));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Rota Escolar Zona Sul').last);
    await tester.pumpAndSettle();

    await tester.tap(find.text('Criar exceção').last);
    await tester.pump();

    expect(submitted?.servicoId, isNull);
    expect(submitted?.motoristaId, isNull);
    expect(submitted?.rotaBaseId, 21);
    expect(submitted?.dataOperacao, isNotNull);
  });

  testWidgets('usa data inicial recebida no formulario de excecao', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1440, 1400));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    AdminViagemCreateInput? submitted;
    final selectedDate = DateTime(2026, 7, 6);

    await tester.pumpWidget(
      _wrap(
        AdminViagemCreateView(
          rotas: const [_sampleRota],
          servicos: const [_sampleServicoSemMotorista],
          motoristas: const [_sampleMotorista],
          isSubmitting: false,
          initialDate: selectedDate,
          onCancel: () {},
          onSubmit: (input) => submitted = input,
        ),
      ),
    );

    expect(find.text('06/07/2026'), findsWidgets);

    await tester.tap(find.byType(DropdownButtonFormField<int>).first);
    await tester.pumpAndSettle();
    await tester.tap(find.text('Linha Escola • Ida').last);
    await tester.pumpAndSettle();

    await tester.tap(find.byType(DropdownButtonFormField<int>).last);
    await tester.pumpAndSettle();
    await tester.tap(find.text('Motorista Portal').last);
    await tester.pumpAndSettle();

    await tester.tap(find.text('Criar exceção').last);
    await tester.pump();

    expect(submitted?.dataOperacao, selectedDate);
  });

  testWidgets(
    'nao renderiza botao morto quando servico ja tem motorista padrao',
    (tester) async {
      await tester.binding.setSurfaceSize(const Size(1440, 1400));
      addTearDown(() => tester.binding.setSurfaceSize(null));

      await tester.pumpWidget(
        _wrap(
          AdminViagemCreateView(
            rotas: const [_sampleRota],
            servicos: const [_sampleServicoComMotorista],
            motoristas: const [_sampleMotorista],
            isSubmitting: false,
            onCancel: () {},
            onSubmit: (_) {},
          ),
        ),
      );

      await tester.tap(find.byType(DropdownButtonFormField<int>).first);
      await tester.pumpAndSettle();
      await tester.tap(find.text('Linha Escola • Ida').last);
      await tester.pumpAndSettle();

      expect(find.text('Motorista Portal'), findsWidgets);
      expect(find.text('Troca via campo'), findsNothing);
    },
  );
}

Widget _wrap(Widget child) {
  return MaterialApp(home: Scaffold(body: child));
}

const _sampleMotorista = AdminMotoristaRecord(
  id: 7,
  nome: 'Motorista Portal',
  email: 'motorista@vanrides.dev',
  telefone: '51999999999',
);

const _sampleServicoSemMotorista = AdminServicoRecorrenteRecord(
  id: 41,
  nome: 'Linha Escola',
  tipoOperacao: 'IDA',
  quantidadePassageiros: 3,
  passageiroIds: [91, 92, 93],
  ativo: true,
  geracaoAutomaticaAtiva: true,
  politicaPresenca: 'INCLUIR_POR_PADRAO',
  diasOperacao: ['MONDAY'],
  horarioPartida: '07:30',
  destinoFinalNome: 'Escola Central',
  motoristaPadrao: null,
);

const _sampleServicoComMotorista = AdminServicoRecorrenteRecord(
  id: 42,
  nome: 'Linha Escola',
  tipoOperacao: 'IDA',
  quantidadePassageiros: 3,
  passageiroIds: [91, 92, 93],
  ativo: true,
  geracaoAutomaticaAtiva: true,
  politicaPresenca: 'INCLUIR_POR_PADRAO',
  diasOperacao: ['MONDAY'],
  horarioPartida: '07:30',
  destinoFinalNome: 'Escola Central',
  motoristaPadrao: _sampleMotorista,
);

const _sampleRota = AdminRotaBaseRecord(
  id: 21,
  nome: 'Rota Escolar Zona Sul',
  motorista: _sampleMotorista,
  quantidadeParadas: 4,
  passageiroIds: [91, 92, 93, 94],
  ativo: true,
  geracaoAutomaticaAtiva: false,
  diasOperacao: ['MONDAY'],
  horarioPartida: '07:20',
  destinoFinalNome: 'Escola Central',
);
