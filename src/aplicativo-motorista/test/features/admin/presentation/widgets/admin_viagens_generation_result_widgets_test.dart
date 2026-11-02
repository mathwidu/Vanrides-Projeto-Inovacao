import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_viagem_bulk_create_result.dart';
import 'package:vanrides_frontend_dev/features/admin/presentation/widgets/admin_viagens_generation_result_widgets.dart';

void main() {
  testWidgets('renderiza sucesso quando nao ha servicos ignorados', (
    tester,
  ) async {
    await tester.pumpWidget(
      _wrap(
        AdminViagensGenerationResultPanel(
          result: AdminViagemBulkCreateResult(
            dataOperacao: _operationDate,
            criadas: 2,
            existentes: 1,
            atualizadas: 1,
            ignoradas: 0,
            viagens: [],
          ),
        ),
      ),
    );

    expect(find.text('Dia operacional montado'), findsOneWidget);
    expect(
      find.text(
        'Resultado: 2 criadas, 1 atualizada, 1 já existiam, 0 bloqueadas, 0 ignoradas.',
      ),
      findsOneWidget,
    );
    expect(find.text('Serviços que precisam de revisão'), findsNothing);
  });

  testWidgets('renderiza revisoes quando servicos sao ignorados', (
    tester,
  ) async {
    await tester.pumpWidget(
      _wrap(
        AdminViagensGenerationResultPanel(
          result: AdminViagemBulkCreateResult(
            dataOperacao: _operationDate,
            criadas: 0,
            existentes: 0,
            bloqueadas: 1,
            ignoradas: 1,
            viagens: [],
            servicosBloqueados: [
              const AdminViagemIgnoradaRecord(
                servicoId: 72,
                servicoNome: 'Linha Rodando',
                motivo: 'VIAGEM_EM_ANDAMENTO',
                mensagem: 'A viagem já começou; revise alterações manualmente.',
              ),
            ],
            servicosIgnorados: [
              const AdminViagemIgnoradaRecord(
                servicoId: 71,
                servicoNome: 'Linha Lotada',
                motivo: 'CAPACIDADE_EXCEDIDA',
                mensagem:
                    'Manifesto operacional acima da capacidade planejada.',
              ),
            ],
          ),
        ),
      ),
    );

    expect(find.text('Dia montado com revisão'), findsOneWidget);
    expect(
      find.text(
        'Resultado: 0 criadas, 0 atualizadas, 0 já existiam, 1 bloqueada, 1 ignorada.',
      ),
      findsOneWidget,
    );
    expect(find.text('Bloqueadas para proteger a operação'), findsOneWidget);
    expect(find.text('Linha Rodando'), findsOneWidget);
    expect(find.text('viagem em andamento'), findsOneWidget);
    expect(
      find.text('A viagem já começou; revise alterações manualmente.'),
      findsOneWidget,
    );
    expect(find.text('Serviços que precisam de revisão'), findsOneWidget);
    expect(find.text('Linha Lotada'), findsOneWidget);
    expect(find.text('capacidade excedida'), findsOneWidget);
    expect(
      find.text('Lista da viagem acima da capacidade planejada.'),
      findsOneWidget,
    );
  });
}

Widget _wrap(Widget child) {
  return MaterialApp(
    home: Scaffold(
      body: SingleChildScrollView(child: SizedBox(width: 900, child: child)),
    ),
  );
}

final _operationDate = DateTime(2026, 6, 12);
