import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/admin/data/admin_places_gateway.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_motorista_record.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_passageiro_record.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_servico_recorrente_input.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_servico_recorrente_record.dart';
import 'package:vanrides_frontend_dev/features/admin/presentation/widgets/admin_servico_recorrente_form.dart';

void main() {
  testWidgets('edicao valida envia input completo preservando destino final', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1440, 1400));
    AdminServicoRecorrenteInput? submitted;

    await _pumpForm(
      tester,
      initial: _servico,
      onSubmit: (input) => submitted = input,
    );

    await tester.tap(find.byKey(AdminServicoRecorrenteForm.submitButtonKey));
    await tester.pumpAndSettle();

    expect(submitted?.nome, 'Linha Manhã');
    expect(submitted?.motoristaPadraoId, 7);
    expect(submitted?.tipoOperacao, 'IDA');
    expect(submitted?.politicaPresenca, 'INCLUIR_POR_PADRAO');
    expect(submitted?.geracaoAutomaticaAtiva, true);
    expect(submitted?.diasOperacao, ['SEG', 'TER', 'QUA']);
    expect(submitted?.horarioPartida, '07:30');
    expect(submitted?.destinoFinalNome, 'Escola Central');
    expect(submitted?.destinoFinalEndereco, 'Rua Principal, 100');
    expect(submitted?.destinoFinalLatitude, -30.01);
    expect(submitted?.destinoFinalLongitude, -51.22);
    expect(submitted?.capacidadePlanejada, 12);
    expect(submitted?.passageiroIds, [11]);
  });

  testWidgets('busca e seleciona passageiros visiveis antes de salvar', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1440, 1400));
    AdminServicoRecorrenteInput? submitted;

    await _pumpForm(tester, onSubmit: (input) => submitted = input);

    await tester.enterText(
      find.byKey(AdminServicoRecorrenteForm.nameFieldKey),
      'Linha Tarde',
    );
    await tester.enterText(
      find.byKey(AdminServicoRecorrenteForm.searchPassengerFieldKey),
      'Bia',
    );
    await tester.pumpAndSettle();

    expect(find.text('Bia Portal'), findsOneWidget);
    expect(find.text('Aluno Portal'), findsNothing);

    await tester.tap(find.text('Selecionar visíveis'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(AdminServicoRecorrenteForm.submitButtonKey));
    await tester.pumpAndSettle();

    expect(submitted?.nome, 'Linha Tarde');
    expect(submitted?.passageiroIds, [12]);
  });

  testWidgets('horario invalido bloqueia submissao', (tester) async {
    await tester.binding.setSurfaceSize(const Size(1440, 1400));
    var submitCount = 0;

    await _pumpForm(tester, onSubmit: (_) => submitCount++);

    await tester.enterText(
      find.byKey(AdminServicoRecorrenteForm.nameFieldKey),
      'Linha Tarde',
    );
    await tester.enterText(
      find.byKey(AdminServicoRecorrenteForm.departureTimeFieldKey),
      '25:99',
    );
    await tester.tap(find.byKey(AdminServicoRecorrenteForm.submitButtonKey));
    await tester.pumpAndSettle();

    expect(submitCount, 0);
    expect(find.text('Informe um horário válido.'), findsOneWidget);
  });

  testWidgets('formulario explica impacto da alteracao no dia operacional', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1440, 1400));

    await _pumpForm(tester, initial: _servico, onSubmit: (_) {});

    expect(
      find.text(
        'Alterações salvas aqui atualizam a base recorrente e valem para as próximas montagens. Se a viagem de hoje já existe, revise em Viagens do dia.',
      ),
      findsOneWidget,
    );
  });
}

Future<void> _pumpForm(
  WidgetTester tester, {
  AdminServicoRecorrenteRecord? initial,
  required ValueChanged<AdminServicoRecorrenteInput> onSubmit,
}) {
  return tester.pumpWidget(
    MaterialApp(
      home: Scaffold(
        body: AdminServicoRecorrenteForm(
          placesGateway: _FakePlacesGateway(),
          initial: initial,
          motoristas: const [_motorista],
          passageiros: const [_passageiro, _passageiroBia],
          isSubmitting: false,
          onCancel: () {},
          onSubmit: onSubmit,
        ),
      ),
    ),
  );
}

class _FakePlacesGateway implements AdminPlacesGateway {
  @override
  Future<List<AdminPlacePrediction>> autocomplete(String input) async =>
      const [];

  @override
  Future<AdminPlaceDetails> fetchDetails(String placeId) async =>
      const AdminPlaceDetails(latitude: -30.01, longitude: -51.22);
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
);

const _passageiroBia = AdminPassageiroRecord(
  id: 12,
  nome: 'Bia Portal',
  email: 'bia@vanrides.dev',
  telefone: '51999999997',
  menorDeIdade: false,
);

const _servico = AdminServicoRecorrenteRecord(
  id: 15,
  nome: 'Linha Manhã',
  tipoOperacao: 'IDA',
  motoristaPadrao: _motorista,
  quantidadePassageiros: 1,
  passageiroIds: [11],
  ativo: true,
  geracaoAutomaticaAtiva: true,
  politicaPresenca: 'INCLUIR_POR_PADRAO',
  diasOperacao: ['SEG', 'TER', 'QUA'],
  horarioPartida: '07:30',
  destinoFinalNome: 'Escola Central',
  destinoFinalEndereco: 'Rua Principal, 100',
  destinoFinalLatitude: -30.01,
  destinoFinalLongitude: -51.22,
  capacidadePlanejada: 12,
);
