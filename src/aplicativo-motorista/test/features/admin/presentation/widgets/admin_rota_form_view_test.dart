import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/admin/data/admin_places_gateway.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_motorista_record.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_passageiro_record.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_rota_base_create_input.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_rota_base_record.dart';
import 'package:vanrides_frontend_dev/features/admin/presentation/widgets/admin_rota_form_view.dart';

void main() {
  testWidgets('edicao valida envia input completo preservando destino final', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1440, 1200));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    AdminRotaBaseCreateInput? submittedInput;

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: AdminRotaFormView(
            placesGateway: const _FakeAdminPlacesGateway(),
            motoristas: const [_motorista],
            passageiros: const [_passageiro],
            isSubmitting: false,
            initialRota: _rota,
            onCancel: () {},
            onSubmit: (input) => submittedInput = input,
          ),
        ),
      ),
    );

    await tester.ensureVisible(find.byKey(AdminRotaFormView.submitButtonKey));
    await tester.tap(find.byKey(AdminRotaFormView.submitButtonKey));
    await tester.pump();

    expect(submittedInput, isNotNull);
    expect(submittedInput?.nome, 'Rota Escolar Zona Sul');
    expect(submittedInput?.motoristaId, 7);
    expect(submittedInput?.passageiroIds, [11]);
    expect(submittedInput?.geracaoAutomaticaAtiva, isTrue);
    expect(submittedInput?.diasOperacao, ['SEG', 'TER', 'QUA']);
    expect(submittedInput?.horarioPartida, '07:30');
    expect(submittedInput?.destinoFinalNome, 'Escola Central');
    expect(submittedInput?.destinoFinalEndereco, 'Rua Principal, 100');
    expect(submittedInput?.destinoFinalLatitude, -30.01);
    expect(submittedInput?.destinoFinalLongitude, -51.22);
  });

  testWidgets('bloqueia submissao sem passageiro compativel selecionado', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1440, 1200));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    var submitCount = 0;

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: AdminRotaFormView(
            placesGateway: const _FakeAdminPlacesGateway(),
            motoristas: const [_motorista],
            passageiros: const [_passageiro],
            isSubmitting: false,
            onCancel: () {},
            onSubmit: (_) => submitCount += 1,
          ),
        ),
      ),
    );

    await tester.enterText(
      find.byKey(AdminRotaFormView.nameFieldKey),
      'Nova rota-base',
    );
    await tester.tap(find.byKey(AdminRotaFormView.driverDropdownKey));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Motorista Portal').last);
    await tester.pumpAndSettle();

    await tester.ensureVisible(find.byKey(AdminRotaFormView.submitButtonKey));
    await tester.tap(find.byKey(AdminRotaFormView.submitButtonKey));
    await tester.pump();

    expect(submitCount, 0);
    expect(
      find.text(
        'Selecione ao menos um passageiro vinculado ao motorista desta rota.',
      ),
      findsOneWidget,
    );
  });
}

class _FakeAdminPlacesGateway implements AdminPlacesGateway {
  const _FakeAdminPlacesGateway();

  @override
  Future<List<AdminPlacePrediction>> autocomplete(String input) async {
    return const [];
  }

  @override
  Future<AdminPlaceDetails> fetchDetails(String placeId) {
    throw UnimplementedError();
  }
}

const _motorista = AdminMotoristaRecord(
  id: 7,
  nome: 'Motorista Portal',
  email: 'motorista@vanrides.dev',
  telefone: '51999999999',
);

const _passageiro = AdminPassageiroRecord(
  id: 11,
  nome: 'Aluno Portal',
  email: 'aluno@vanrides.dev',
  telefone: '51999999998',
  menorDeIdade: true,
  motoristaId: 7,
  motoristaNome: 'Motorista Portal',
);

const _rota = AdminRotaBaseRecord(
  id: 21,
  nome: 'Rota Escolar Zona Sul',
  motorista: _motorista,
  quantidadeParadas: 1,
  passageiroIds: [11],
  ativo: true,
  geracaoAutomaticaAtiva: true,
  diasOperacao: ['SEG', 'TER', 'QUA'],
  horarioPartida: '07:30',
  destinoFinalNome: 'Escola Central',
  destinoFinalEndereco: 'Rua Principal, 100',
  destinoFinalLatitude: -30.01,
  destinoFinalLongitude: -51.22,
);
