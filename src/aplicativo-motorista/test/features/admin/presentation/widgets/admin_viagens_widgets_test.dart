import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_motorista_record.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_viagem_record.dart';
import 'package:vanrides_frontend_dev/features/admin/presentation/widgets/admin_viagens_widgets.dart';

void main() {
  testWidgets('AdminViagensDateField renders value and triggers callback', (
    tester,
  ) async {
    var tapped = false;

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: AdminViagensDateField(
            label: 'Data de operacao',
            value: '10 Jun 2026',
            onTap: () => tapped = true,
          ),
        ),
      ),
    );

    expect(find.text('Data de operacao'), findsOneWidget);
    expect(find.text('10 Jun 2026'), findsWidgets);

    await tester.tap(find.byType(InkWell));
    await tester.pump();

    expect(tapped, isTrue);
  });

  testWidgets('AdminViagensDarkMetricRow renders both metric pairs', (
    tester,
  ) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          backgroundColor: Colors.black,
          body: AdminViagensDarkMetricRow(
            leftLabel: 'PARADAS',
            leftValue: '03',
            rightLabel: 'PRESENCAS',
            rightValue: '02',
          ),
        ),
      ),
    );

    expect(find.text('PARADAS'), findsOneWidget);
    expect(find.text('03'), findsOneWidget);
    expect(find.text('PRESENCAS'), findsOneWidget);
    expect(find.text('02'), findsOneWidget);
  });

  testWidgets('AdminViagensTripRow renders trip summary and opens detail', (
    tester,
  ) async {
    var opened = false;
    await tester.binding.setSurfaceSize(const Size(1440, 900));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: SizedBox(
            width: 1440,
            child: AdminViagensTripRow(
              viagem: _sampleViagem,
              onView: () => opened = true,
            ),
          ),
        ),
      ),
    );

    expect(find.text('15 Abr 2026'), findsOneWidget);
    expect(find.text('07:30'), findsOneWidget);
    expect(find.text('Linha Centro'), findsOneWidget);
    expect(find.text('Motorista Principal'), findsOneWidget);
    expect(find.text('03 paradas'), findsOneWidget);
    expect(find.text('EM TRÂNSITO'), findsOneWidget);

    await tester.tap(find.byIcon(Icons.visibility_outlined));
    await tester.pump();

    expect(opened, isTrue);
  });

  testWidgets('AdminViagensDateRangeChip renders placeholder and callback', (
    tester,
  ) async {
    var tapped = false;

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: AdminViagensDateRangeChip(
            date: null,
            onPressed: () => tapped = true,
          ),
        ),
      ),
    );

    expect(find.text('Filtrar por data'), findsOneWidget);

    await tester.tap(find.text('Filtrar por data'));
    await tester.pump();

    expect(tapped, isTrue);
  });
}

const _sampleMotorista = AdminMotoristaRecord(
  id: 7,
  nome: 'Motorista Principal',
  email: 'motorista@vanrides.dev',
  telefone: '51999999999',
);

final _sampleViagem = AdminViagemRecord(
  id: 31,
  rotaNome: 'Rota Centro',
  servicoNome: 'Linha Centro',
  dataOperacao: DateTime(2026, 4, 15),
  status: 'IN_PROGRESS',
  motorista: _sampleMotorista,
  quantidadeParadas: 3,
  horarioPartida: '07:30',
);
