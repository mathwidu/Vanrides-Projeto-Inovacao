import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:table_calendar/table_calendar.dart';
import 'package:vanrides_frontend_dev/features/service_presence/data/service_presence_service.dart';
import 'package:vanrides_frontend_dev/features/service_presence/domain/passenger_available_service_record.dart';
import 'package:vanrides_frontend_dev/features/service_presence/presentation/confirmar_presenca_screen.dart';

void main() {
  setUpAll(() async {
    await initializeDateFormatting('pt_BR', null);
  });

  testWidgets('renderiza dias da semana e acoes principais', (tester) async {
    final today = DateTime.now();
    final todayIso = today.toIso8601String().split('T')[0];
    final client = MockClient(
      (_) async => http.Response(
        jsonEncode([
          {
            'id': 1,
            'dataPresenca': todayIso,
            'passageiro': {'id': 10},
          },
        ]),
        200,
        headers: {'content-type': 'application/json'},
      ),
    );

    await tester.pumpWidget(
      MaterialApp(
        home: ConfirmarPresencaScreen(
          idPassageiro: 10,
          motoristaId: 1,
          email: 'passageiro@test.dev',
          senha: 'senha123',
          client: client,
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.byKey(ConfirmarPresencaScreen.calendarKey), findsOneWidget);
    expect(
      find.byKey(ConfirmarPresencaScreen.confirmarButtonKey),
      findsOneWidget,
    );
    expect(
      find.byKey(ConfirmarPresencaScreen.removerButtonKey),
      findsOneWidget,
    );
    expect(find.text('${today.day}'), findsWidgets);
  });

  testWidgets('exibe estado de confirmação ao confirmar selecionados', (
    tester,
  ) async {
    final selectedDay = DateTime.now().add(const Duration(days: 1));
    final selectedDayIso = selectedDay.toIso8601String().split('T')[0];
    var requestCount = 0;
    final client = MockClient((request) async {
      requestCount++;
      if (request.method == 'GET') {
        return http.Response(
          '[]',
          200,
          headers: {'content-type': 'application/json'},
        );
      }
      if (request.method == 'POST') {
        expect(request.body, contains(selectedDayIso));
        return http.Response(
          jsonEncode({'ok': true}),
          200,
          headers: {'content-type': 'application/json'},
        );
      }
      return http.Response('unexpected', 500);
    });

    await tester.pumpWidget(
      MaterialApp(
        home: ConfirmarPresencaScreen(
          idPassageiro: 10,
          motoristaId: 1,
          email: 'passageiro@test.dev',
          senha: 'senha123',
          client: client,
        ),
      ),
    );
    await tester.pumpAndSettle();

    final calendar = tester.widget<TableCalendar<dynamic>>(
      find.byKey(ConfirmarPresencaScreen.calendarKey),
    );
    calendar.onDaySelected?.call(selectedDay, selectedDay);
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(ConfirmarPresencaScreen.confirmarButtonKey));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 250));

    expect(requestCount, greaterThanOrEqualTo(2));
    expect(find.textContaining('1 confirmadas'), findsOneWidget);
  });

  testWidgets('exibe erro quando backend falha ao carregar presenças', (
    tester,
  ) async {
    final client = MockClient((_) async => http.Response('error', 500));

    await tester.pumpWidget(
      MaterialApp(
        home: ConfirmarPresencaScreen(
          idPassageiro: 10,
          motoristaId: 1,
          email: 'passageiro@test.dev',
          senha: 'senha123',
          client: client,
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Erro ao carregar presenças.'), findsOneWidget);
  });

  testWidgets('permite decidir presença por serviço disponível', (
    tester,
  ) async {
    final serviceGateway = _FakeServicePresenceGateway();
    var legacyRequests = 0;
    final client = MockClient((_) async {
      legacyRequests++;
      return http.Response('[]', 200);
    });

    await tester.pumpWidget(
      MaterialApp(
        home: ConfirmarPresencaScreen(
          idPassageiro: 10,
          motoristaId: 1,
          email: 'passageiro@test.dev',
          senha: 'senha123',
          client: client,
          servicePresenceGateway: serviceGateway,
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.byKey(ConfirmarPresencaScreen.serviceListKey), findsOneWidget);
    expect(find.text('Linha Manhã'), findsOneWidget);
    expect(legacyRequests, 0);

    await tester.tap(find.byKey(const ValueKey('service-presence.confirm.15')));
    await tester.pumpAndSettle();

    expect(serviceGateway.lastStatus, 'CONFIRMED');
    expect(find.text('Vou'), findsWidgets);
  });
}

class _FakeServicePresenceGateway implements ServicePresenceGateway {
  String? lastStatus;
  bool confirmed = false;

  @override
  Future<void> clearPresence(int presencaId) async {
    confirmed = false;
  }

  @override
  Future<List<PassengerAvailableServiceRecord>> fetchAvailableServices({
    required int passageiroId,
    required DateTime data,
  }) async {
    return [
      PassengerAvailableServiceRecord(
        servicoId: 15,
        nome: 'Linha Manhã',
        tipoOperacao: 'IDA',
        dataOperacao: DateTime(data.year, data.month, data.day),
        horarioPartida: '07:30',
        destinoFinalNome: 'Escola Central',
        politicaPresenca: 'EXIGE_CONFIRMACAO',
        presencaId: confirmed ? 99 : null,
        presencaStatus: confirmed ? 'CONFIRMED' : 'UNKNOWN',
      ),
    ];
  }

  @override
  Future<void> setPresenceStatus({
    required PassengerAvailableServiceRecord service,
    required int passageiroId,
    required String status,
  }) async {
    lastStatus = status;
    confirmed = status == 'CONFIRMED';
  }
}
