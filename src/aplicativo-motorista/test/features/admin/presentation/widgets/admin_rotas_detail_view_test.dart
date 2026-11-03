import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_motorista_record.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_rota_base_record.dart';
import 'package:vanrides_frontend_dev/features/admin/presentation/widgets/admin_rotas_detail_view.dart';

void main() {
  testWidgets('detalhe renderiza destino final persistido e metricas', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1440, 1200));

    await tester.pumpWidget(
      _widgetApp(
        AdminRotaDetailView(
          rota: _rotaComDestino,
          isSubmitting: false,
          onBack: () {},
          onEdit: () {},
          onDelete: () {},
        ),
      ),
    );

    expect(find.text('Rota Escolar Zona Sul'), findsOneWidget);
    expect(find.text('Informações da rota-base'), findsOneWidget);
    expect(find.text('Motorista responsável'), findsOneWidget);
    expect(find.text('Motorista Portal'), findsOneWidget);
    expect(find.text('Horário de Partida'), findsOneWidget);
    expect(find.text('07:30'), findsOneWidget);
    expect(find.text('Escola Central'), findsOneWidget);
    expect(find.text('Rua Principal, 100'), findsOneWidget);
    expect(find.textContaining('destino final já está salvo'), findsOneWidget);
    expect(find.text('ATIVA'), findsOneWidget);
    expect(find.text('ATIVO'), findsOneWidget);
  });

  testWidgets('detalhe sem destino mostra fallback operacional', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1440, 1200));

    await tester.pumpWidget(
      _widgetApp(
        AdminRotaDetailView(
          rota: _rotaSemDestino,
          isSubmitting: false,
          onBack: () {},
          onEdit: () {},
          onDelete: () {},
        ),
      ),
    );

    expect(find.text('Sem destino final configurado'), findsOneWidget);
    expect(
      find.textContaining('usa a última parada da fila como destino'),
      findsOneWidget,
    );
    expect(
      find.textContaining('a roteirização usa a última parada como fallback'),
      findsOneWidget,
    );
    expect(find.text('INATIVA'), findsOneWidget);
    expect(find.text('OFF'), findsOneWidget);
  });

  testWidgets('acoes de voltar editar e excluir disparam callbacks', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1440, 1200));
    var backCalls = 0;
    var editCalls = 0;
    var deleteCalls = 0;

    await tester.pumpWidget(
      _widgetApp(
        AdminRotaDetailView(
          rota: _rotaComDestino,
          isSubmitting: false,
          onBack: () => backCalls++,
          onEdit: () => editCalls++,
          onDelete: () => deleteCalls++,
        ),
      ),
    );

    await tester.tap(find.text('Voltar para lista'));
    await tester.tap(find.text('Editar'));
    await tester.tap(find.text('Excluir'));
    await tester.pumpAndSettle();

    expect(backCalls, 1);
    expect(editCalls, 1);
    expect(deleteCalls, 1);
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

const _motorista = AdminMotoristaRecord(
  id: 7,
  nome: 'Motorista Portal',
  email: 'motorista@vanrides.dev',
  telefone: '51999999999',
);

const _rotaComDestino = AdminRotaBaseRecord(
  id: 21,
  nome: 'Rota Escolar Zona Sul',
  motorista: _motorista,
  quantidadeParadas: 1,
  passageiroIds: [11],
  ativo: true,
  geracaoAutomaticaAtiva: true,
  diasOperacao: ['SEG', 'TER', 'QUA', 'QUI', 'SEX'],
  horarioPartida: '07:30',
  destinoFinalNome: 'Escola Central',
  destinoFinalEndereco: 'Rua Principal, 100',
  destinoFinalLatitude: -30.01,
  destinoFinalLongitude: -51.22,
);

const _rotaSemDestino = AdminRotaBaseRecord(
  id: 22,
  nome: 'Rota Sem Destino',
  motorista: _motorista,
  quantidadeParadas: 0,
  passageiroIds: [],
  ativo: false,
  geracaoAutomaticaAtiva: false,
  diasOperacao: [],
);
