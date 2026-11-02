import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_motorista_record.dart';
import 'package:vanrides_frontend_dev/features/admin/presentation/widgets/admin_servicos_recorrentes_form_widgets.dart';

void main() {
  testWidgets(
    'AdminServicoFormOperationalSummary renderiza labels do formulario',
    (tester) async {
      await tester.pumpWidget(
        const MaterialApp(
          home: Scaffold(
            body: AdminServicoFormOperationalSummary(
              tipoOperacao: 'VOLTA',
              horarioPartida: '17:40',
              politicaPresenca: 'EXIGE_CONFIRMACAO',
              geracaoAutomaticaAtiva: false,
              motoristaPadraoValue: 7,
              motoristas: [_motorista],
              selectedPassengers: 2,
              totalPassengers: 5,
            ),
          ),
        ),
      );

      expect(find.text('17:40 Volta'), findsOneWidget);
      expect(find.text('2 de 5 elegíveis'), findsOneWidget);
      expect(find.text('Exige confirmação'), findsOneWidget);
      expect(find.text('Montagem manual'), findsOneWidget);
      expect(find.text('Motorista Portal'), findsOneWidget);
    },
  );

  test(
    'validadores preservam campos opcionais e rejeitam valores invalidos',
    () {
      expect(validateAdminServicoOptionalTime(null), isNull);
      expect(validateAdminServicoOptionalTime(''), isNull);
      expect(validateAdminServicoOptionalTime('07:30'), isNull);
      expect(validateAdminServicoOptionalTime('7:30'), 'Use o formato HH:mm.');
      expect(
        validateAdminServicoOptionalTime('24:00'),
        'Informe um horário válido.',
      );
      expect(
        validateAdminServicoOptionalTime('23:60'),
        'Informe um horário válido.',
      );

      expect(validateAdminServicoOptionalPositiveInt(null), isNull);
      expect(validateAdminServicoOptionalPositiveInt(''), isNull);
      expect(validateAdminServicoOptionalPositiveInt('12'), isNull);
      expect(
        validateAdminServicoOptionalPositiveInt('0'),
        'Informe um número maior que zero.',
      );
      expect(
        validateAdminServicoOptionalPositiveInt('abc'),
        'Informe um número maior que zero.',
      );
    },
  );

  test('helpers formatam operacao motorista e dias em ordem operacional', () {
    expect(adminServicoFormOperationLabel('IDA', ''), 'Ida');
    expect(adminServicoFormOperationLabel('FRETADO', '08:10'), '08:10 Fretado');
    expect(
      adminServicoFormDriverLabel(0, const [_motorista]),
      'Escala por viagem',
    );
    expect(
      adminServicoFormDriverLabel(99, const [_motorista]),
      'Motorista padrão',
    );
    expect(
      adminServicoFormDriverLabel(7, const [_motorista]),
      'Motorista Portal',
    );
    expect(orderedAdminServicoDays({'SEX', 'SEG', 'QUA'}), [
      'SEG',
      'QUA',
      'SEX',
    ]);
  });
}

const _motorista = AdminMotoristaRecord(
  id: 7,
  nome: 'Motorista Portal',
  email: 'motorista@vanrides.dev',
  telefone: '51999999999',
);
