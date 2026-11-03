import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/service_presence/domain/passenger_available_service_record.dart';
import 'package:vanrides_frontend_dev/features/service_presence/presentation/presence_today_list.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/widgets/vanrides_map_tokens.dart';

void main() {
  testWidgets('renders dark premium presence decisions without agenda copy', (
    tester,
  ) async {
    PassengerAvailableServiceRecord? confirmed;

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: PresenceTodayList(
            services: [
              _service(id: 15, status: 'UNKNOWN'),
              _service(id: 31, status: 'CONFIRMED', presencaId: 91),
            ],
            processing: false,
            onConfirm: (service) => confirmed = service,
            onAbsent: (_) {},
            onClear: (_) {},
          ),
        ),
      ),
    );

    expect(find.text('Presença de hoje'), findsOneWidget);
    expect(find.text('Agenda de hoje'), findsNothing);
    expect(find.text('2 horários'), findsOneWidget);
    expect(find.text('1 confirmado'), findsOneWidget);
    expect(find.text('1 pendente'), findsOneWidget);
    expect(find.text('Pendente'), findsOneWidget);
    expect(find.text('Vou'), findsWidgets);
    expect(find.text('Não vou'), findsWidgets);
    expect(find.text('Sem decisão: escolha uma opção'), findsNothing);
    expect(find.byKey(PresenceTodayList.darkSurfaceKey), findsOneWidget);

    await tester.tap(find.byKey(const ValueKey('service-presence.confirm.15')));
    await tester.pump();

    expect(confirmed?.servicoId, 15);
  });

  testWidgets('supports high text scale without hiding the decision actions', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: MediaQuery(
            data: const MediaQueryData(textScaler: TextScaler.linear(1.35)),
            child: PresenceTodayList(
              services: [_service(id: 15, status: 'UNKNOWN')],
              processing: false,
              onConfirm: (_) {},
              onAbsent: (_) {},
              onClear: (_) {},
            ),
          ),
        ),
      ),
    );

    expect(find.text('Presença de hoje'), findsOneWidget);
    expect(find.text('Vou'), findsOneWidget);
    expect(find.text('Não vou'), findsOneWidget);
  });

  testWidgets(
    'usa contraste claro nos textos principais em tema externo claro',
    (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          theme: ThemeData.light(),
          home: Scaffold(
            body: PresenceTodayList(
              services: [_service(id: 15, status: 'UNKNOWN')],
              processing: false,
              onConfirm: (_) {},
              onAbsent: (_) {},
              onClear: (_) {},
            ),
          ),
        ),
      );

      expect(
        tester.widget<Text>(find.text('Presença de hoje')).style?.color,
        VanRidesMapTokens.textPrimary,
      );
      expect(
        tester.widget<Text>(find.text('07:25 • Ida')).style?.color,
        VanRidesMapTokens.textPrimary,
      );
    },
  );
}

PassengerAvailableServiceRecord _service({
  required int id,
  required String status,
  int? presencaId,
}) {
  return PassengerAvailableServiceRecord(
    servicoId: id,
    nome: 'feevale',
    tipoOperacao: 'IDA',
    dataOperacao: DateTime(2026, 6, 15),
    horarioPartida: id == 15 ? '07:25' : '18:00',
    destinoFinalNome: 'Feevale',
    politicaPresenca: 'EXIGE_CONFIRMACAO',
    presencaStatus: status,
    presencaId: presencaId,
  );
}
