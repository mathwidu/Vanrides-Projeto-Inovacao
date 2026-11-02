import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_company_link_flow_models.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_motorista_record.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_servico_recorrente_record.dart';
import 'package:vanrides_frontend_dev/features/admin/presentation/widgets/admin_vinculos_access_request_dialog.dart';

void main() {
  testWidgets('aprovacao retorna motorista inicial e servicos liberados', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(900, 900));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    AdminAccessRequestDecision? decision;

    await tester.pumpWidget(
      _wrapDialogLauncher(
        onPressed: (context) async {
          decision = await showDialog<AdminAccessRequestDecision>(
            context: context,
            builder:
                (context) => const AdminAccessRequestDecisionDialog(
                  detail: _detail,
                  motoristas: [_motorista],
                  servicos: [_servicoIda, _servicoVolta],
                ),
          );
        },
      ),
    );

    await tester.tap(find.text('Abrir solicitação'));
    await tester.pumpAndSettle();

    await tester.tap(find.text('Definir pela viagem do dia'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Motorista Portal').last);
    await tester.pumpAndSettle();
    await tester.tap(find.text('Ida manhã'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Aprovar vínculo'));
    await tester.pumpAndSettle();

    expect(decision?.type, AdminAccessRequestDecisionType.approve);
    expect(decision?.motoristaId, 5);
    expect(decision?.servicoIds, [10]);
  });

  testWidgets('rejeicao retorna motivo informado', (tester) async {
    await tester.binding.setSurfaceSize(const Size(900, 900));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    AdminAccessRequestDecision? decision;

    await tester.pumpWidget(
      _wrapDialogLauncher(
        onPressed: (context) async {
          decision = await showDialog<AdminAccessRequestDecision>(
            context: context,
            builder:
                (context) => const AdminAccessRequestDecisionDialog(
                  detail: _detail,
                  motoristas: [_motorista],
                  servicos: [_servicoIda],
                ),
          );
        },
      ),
    );

    await tester.tap(find.text('Abrir solicitação'));
    await tester.pumpAndSettle();

    await tester.enterText(
      find.byType(TextField),
      'Responsável não reconhecido pela escola',
    );
    await tester.tap(find.text('Rejeitar'));
    await tester.pumpAndSettle();

    expect(decision?.type, AdminAccessRequestDecisionType.reject);
    expect(decision?.reason, 'Responsável não reconhecido pela escola');
  });
}

Widget _wrapDialogLauncher({
  required Future<void> Function(BuildContext context) onPressed,
}) {
  return MaterialApp(
    home: Builder(
      builder: (context) {
        return Scaffold(
          body: Center(
            child: ElevatedButton(
              onPressed: () => onPressed(context),
              child: const Text('Abrir solicitação'),
            ),
          ),
        );
      },
    ),
  );
}

const _detail = AdminAccessRequestDetailRecord(
  id: 1,
  passageiroId: 11,
  passageiroNome: 'Aluno Portal',
  passageiroEmail: 'aluno@vanrides.dev',
  passageiroTelefone: '51999999999',
  companyName: 'Portal Empresa',
  source: 'REQUEST',
  status: 'PENDING',
  emailVerified: true,
);

const _motorista = AdminMotoristaRecord(
  id: 5,
  nome: 'Motorista Portal',
  email: 'motorista@vanrides.dev',
  telefone: '51999999998',
);

const _servicoIda = AdminServicoRecorrenteRecord(
  id: 10,
  nome: 'Ida manhã',
  tipoOperacao: 'IDA',
  quantidadePassageiros: 1,
  passageiroIds: [],
  ativo: true,
  geracaoAutomaticaAtiva: true,
  politicaPresenca: 'INCLUIR_POR_PADRAO',
  diasOperacao: ['MONDAY'],
  horarioPartida: '07:30',
);

const _servicoVolta = AdminServicoRecorrenteRecord(
  id: 11,
  nome: 'Volta tarde',
  tipoOperacao: 'VOLTA',
  quantidadePassageiros: 1,
  passageiroIds: [],
  ativo: true,
  geracaoAutomaticaAtiva: true,
  politicaPresenca: 'INCLUIR_POR_PADRAO',
  diasOperacao: ['MONDAY'],
  horarioPartida: '17:40',
);
