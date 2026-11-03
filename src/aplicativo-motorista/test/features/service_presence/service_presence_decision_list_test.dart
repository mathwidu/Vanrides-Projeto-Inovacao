import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/service_presence/domain/passenger_available_service_record.dart';
import 'package:vanrides_frontend_dev/features/service_presence/presentation/service_presence_decision_list.dart';

void main() {
  testWidgets('renderiza vazio util quando nao ha horarios para decidir', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: ServicePresenceDecisionList(
            services: const [],
            processing: false,
            onConfirm: (_) {},
            onAbsent: (_) {},
            onClear: (_) {},
          ),
        ),
      ),
    );

    expect(find.text('Presença de hoje'), findsOneWidget);
    expect(find.text('Nenhum horário para decidir agora'), findsOneWidget);
    expect(
      find.text(
        'Quando houver serviço liberado para hoje, a decisão aparece aqui.',
      ),
      findsOneWidget,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('um unico horario mostra decisao primaria sem poluir a tela', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: ServicePresenceDecisionList(
            services: [
              _service(
                id: 15,
                nome: 'Linha Manhã',
                tipo: 'IDA',
                horario: '07:30',
                status: 'UNKNOWN',
              ),
            ],
            processing: false,
            onConfirm: (_) {},
            onAbsent: (_) {},
            onClear: (_) {},
          ),
        ),
      ),
    );

    expect(find.text('1 horário'), findsOneWidget);
    expect(find.text('1 pendente'), findsOneWidget);
    expect(find.text('Vou'), findsOneWidget);
    expect(find.text('Não vou'), findsOneWidget);
    expect(find.text('Alterar'), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'dois horarios decididos continuam editaveis sem agenda duplicada',
    (tester) async {
      PassengerAvailableServiceRecord? cleared;

      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: ServicePresenceDecisionList(
              services: [
                _service(
                  id: 15,
                  nome: 'Linha Manhã',
                  tipo: 'IDA',
                  horario: '07:30',
                  status: 'CONFIRMED',
                  presencaId: 91,
                ),
                _service(
                  id: 31,
                  nome: 'Linha Tarde',
                  tipo: 'VOLTA',
                  horario: '17:40',
                  status: 'ABSENT',
                  presencaId: 92,
                ),
              ],
              processing: false,
              onConfirm: (_) {},
              onAbsent: (_) {},
              onClear: (service) => cleared = service,
            ),
          ),
        ),
      );

      expect(find.text('2 horários'), findsOneWidget);
      expect(find.text('Tudo decidido para hoje.'), findsOneWidget);
      expect(find.text('Agenda de hoje'), findsNothing);
      expect(find.text('Alterar'), findsNWidgets(2));
      expect(find.text('Vou'), findsOneWidget);
      expect(find.text('Não vou'), findsOneWidget);

      await tester.tap(find.byKey(const ValueKey('service-presence.clear.31')));
      await tester.pump();

      expect(cleared?.servicoId, 31);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'cinco horarios ficam em lista vertical compacta em tela pequena',
    (tester) async {
      await tester.binding.setSurfaceSize(const Size(375, 812));
      addTearDown(() => tester.binding.setSurfaceSize(null));

      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: ListView(
              children: [
                ServicePresenceDecisionList(
                  services: [
                    _service(
                      id: 11,
                      nome: 'Linha 1',
                      tipo: 'IDA',
                      horario: '06:50',
                      status: 'UNKNOWN',
                    ),
                    _service(
                      id: 12,
                      nome: 'Linha 2',
                      tipo: 'IDA',
                      horario: '07:25',
                      status: 'CONFIRMED',
                      presencaId: 82,
                    ),
                    _service(
                      id: 13,
                      nome: 'Linha 3',
                      tipo: 'VOLTA',
                      horario: '12:10',
                      status: 'UNKNOWN',
                    ),
                    _service(
                      id: 14,
                      nome: 'Linha 4',
                      tipo: 'VOLTA',
                      horario: '18:00',
                      status: 'ABSENT',
                      presencaId: 84,
                    ),
                    _service(
                      id: 15,
                      nome: 'Linha 5',
                      tipo: 'CIRCULAR',
                      horario: '22:15',
                      status: 'UNKNOWN',
                    ),
                  ],
                  processing: false,
                  onConfirm: (_) {},
                  onAbsent: (_) {},
                  onClear: (_) {},
                ),
              ],
            ),
          ),
        ),
      );

      expect(find.text('5 horários'), findsOneWidget);
      expect(find.text('3 pendentes'), findsOneWidget);
      expect(find.text('06:50 • Ida'), findsOneWidget);
      expect(find.text('22:15 • Circular'), findsOneWidget);
      expect(find.byType(SingleChildScrollView), findsNothing);
      expect(find.text('Alterar'), findsNWidgets(2));
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('resume multiplos servicos e decide cada horario isoladamente', (
    tester,
  ) async {
    final semantics = tester.ensureSemantics();

    PassengerAvailableServiceRecord? confirmedService;
    PassengerAvailableServiceRecord? absentService;

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: ListView(
            children: [
              ServicePresenceDecisionList(
                services: [
                  _service(
                    id: 15,
                    nome: 'Linha Manhã',
                    tipo: 'IDA',
                    horario: '07:30',
                    status: 'UNKNOWN',
                  ),
                  _service(
                    id: 31,
                    nome: 'Linha Tarde',
                    tipo: 'VOLTA',
                    horario: '17:40',
                    status: 'CONFIRMED',
                    presencaId: 91,
                  ),
                  _service(
                    id: 44,
                    nome: 'Linha Noite',
                    tipo: 'CIRCULAR',
                    horario: '22:10',
                    status: 'ABSENT',
                    presencaId: 92,
                  ),
                ],
                processing: false,
                onConfirm: (service) => confirmedService = service,
                onAbsent: (service) => absentService = service,
                onClear: (_) {},
              ),
            ],
          ),
        ),
      ),
    );

    expect(find.text('Presença de hoje'), findsOneWidget);
    expect(
      find.text('Toque em Vou ou Não vou em cada horário.'),
      findsOneWidget,
    );
    expect(find.text('3 horários'), findsOneWidget);
    expect(find.text('1 confirmado'), findsOneWidget);
    expect(find.text('1 ausência'), findsOneWidget);
    expect(find.text('1 pendente'), findsOneWidget);
    expect(find.text('Pendente'), findsOneWidget);
    expect(find.text('Sem decisão: escolha uma opção'), findsNothing);
    expect(find.text('Você vai'), findsNothing);
    expect(find.text('Você não vai'), findsNothing);
    expect(find.text('07:30 • Ida'), findsOneWidget);
    expect(find.text('17:40 • Volta'), findsOneWidget);
    expect(find.text('22:10 • Circular'), findsOneWidget);
    expect(find.textContaining('A definir'), findsWidgets);
    expect(
      find.bySemanticsLabel('07:30 Ida, Linha Manhã, Pendente, Escola Central'),
      findsOneWidget,
    );
    semantics.dispose();
    expect(find.text('Vou neste horário'), findsNothing);
    expect(find.text('Não vou neste horário'), findsNothing);
    expect(find.text('Alterar'), findsNWidgets(2));

    await tester.tap(find.byKey(const ValueKey('service-presence.confirm.15')));
    await tester.pump();
    await tester.tap(find.byKey(const ValueKey('service-presence.absent.15')));
    await tester.pump();

    expect(confirmedService?.servicoId, 15);
    expect(absentService?.servicoId, 15);
  });

  testWidgets('preserva hierarquia do card em largura compacta', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: Center(
            child: SizedBox(
              width: 320,
              child: ServicePresenceDecisionList(
                services: [
                  _service(
                    id: 15,
                    nome: 'Linha Manhã',
                    tipo: 'IDA',
                    horario: '07:30',
                    status: 'UNKNOWN',
                  ),
                ],
                processing: false,
                onConfirm: (_) {},
                onAbsent: (_) {},
                onClear: (_) {},
              ),
            ),
          ),
        ),
      ),
    );

    expect(tester.takeException(), isNull);
    expect(find.text('07:30 • Ida'), findsOneWidget);
    expect(find.text('Pendente'), findsOneWidget);
    expect(tester.getRect(find.text('07:30 • Ida')).width, greaterThan(88));
  });
}

PassengerAvailableServiceRecord _service({
  required int id,
  required String nome,
  required String tipo,
  required String horario,
  required String status,
  int? presencaId,
}) {
  return PassengerAvailableServiceRecord(
    servicoId: id,
    nome: nome,
    tipoOperacao: tipo,
    dataOperacao: DateTime(2026, 5, 25),
    horarioPartida: horario,
    destinoFinalNome: 'Escola Central',
    politicaPresenca: 'EXIGE_CONFIRMACAO',
    presencaStatus: status,
    presencaId: presencaId,
  );
}
