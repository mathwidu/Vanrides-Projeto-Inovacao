import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:vanrides_frontend_dev/features/service_presence/presentation/presencas_hoje_screen.dart';

void main() {
  testWidgets('exibe lista operacional por viagem do dia', (tester) async {
    final client = MockClient((request) async {
      expect(request.url.path, '/viagens/motorista/1/dia');
      expect(request.url.queryParameters['data'], '2026-05-25');
      return http.Response(
        jsonEncode([
          {
            'id': 11,
            'rotaBaseId': 4,
            'rotaNome': 'Rota Centro',
            'servicoId': 15,
            'servicoNome': 'Linha Manhã',
            'tipoOperacao': 'IDA',
            'dataOperacao': '2026-05-25',
            'status': 'PLANNED',
            'rotaVersao': 1,
            'horarioPartida': '07:00:00',
            'paradas': [
              {
                'id': 80,
                'ordem': 1,
                'status': 'PENDING',
                'etaMinutos': 6,
                'confirmadoPresenca': true,
                'proximaParada': true,
                'passageiro': {
                  'id': 30,
                  'nome': 'Ana Silva',
                  'menorDeIdade': true,
                },
              },
              {
                'id': 90,
                'ordem': 2,
                'status': 'PENDING',
                'etaMinutos': 12,
                'confirmadoPresenca': false,
                'proximaParada': false,
                'passageiro': {
                  'id': 40,
                  'nome': 'Carlos Dias',
                  'menorDeIdade': false,
                },
              },
            ],
          },
        ]),
        200,
        headers: {'content-type': 'application/json'},
      );
    });

    await tester.pumpWidget(
      MaterialApp(
        home: PresencasHojeScreen(
          motoristaId: 1,
          email: 'motorista@test.dev',
          senha: 'senha123',
          client: client,
          now: () => DateTime(2026, 5, 25),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Lista de hoje'), findsOneWidget);
    expect(find.text('07:00 • Ida • Linha Manhã'), findsOneWidget);
    expect(find.text('2 paradas • 1 confirmadas • 1 pendentes'), findsWidgets);
    expect(find.text('Ana Silva'), findsOneWidget);
    expect(find.text('Carlos Dias'), findsOneWidget);
    expect(find.text('Presenca confirmada'), findsOneWidget);
    expect(find.text('Presenca pendente'), findsOneWidget);
  });

  testWidgets('exibe empty state quando nao ha viagens com paradas', (
    tester,
  ) async {
    final client = MockClient(
      (_) async => http.Response(
        '[]',
        200,
        headers: {'content-type': 'application/json'},
      ),
    );

    await tester.pumpWidget(
      MaterialApp(
        home: PresencasHojeScreen(
          motoristaId: 1,
          email: 'motorista@test.dev',
          senha: 'senha123',
          client: client,
          now: () => DateTime(2026, 5, 25),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.byKey(PresencasHojeScreen.emptyStateTextKey), findsOneWidget);
  });

  testWidgets('exibe snackbar quando servico retorna erro', (tester) async {
    final client = MockClient((_) async => http.Response('error', 500));

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: PresencasHojeScreen(
            motoristaId: 1,
            email: 'motorista@test.dev',
            senha: 'senha123',
            client: client,
            now: () => DateTime(2026, 5, 25),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.textContaining('Erro ao carregar presenças'), findsOneWidget);
  });
}
