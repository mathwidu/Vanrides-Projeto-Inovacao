import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/domain/driver_trip_record.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/presentation/widgets/driver_home_widgets.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_record.dart';

void main() {
  testWidgets('DriverStopsTab mostra erro e aciona refresh', (tester) async {
    var refreshTaps = 0;

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: DriverStopsTab(
            isLoading: false,
            errorMessage: 'Falha ao carregar fila',
            trip: null,
            onRefresh: () => refreshTaps++,
          ),
        ),
      ),
    );

    expect(find.text('Sem fila para mostrar'), findsOneWidget);
    expect(find.text('Falha ao carregar fila'), findsOneWidget);
    expect(find.text('Atualizar'), findsOneWidget);

    await tester.tap(find.text('Atualizar'));
    await tester.pumpAndSettle();

    expect(refreshTaps, 1);
    expect(tester.takeException(), isNull);
  });

  testWidgets('DriverStopsTab mostra resumo e lista de paradas', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(home: Scaffold(body: DriverStopsTab(trip: _trip))),
    );

    expect(find.text('Fila de paradas'), findsOneWidget);
    expect(find.text('Linha Manha'), findsOneWidget);
    expect(find.text('2'), findsOneWidget);
    expect(find.text('23min'), findsOneWidget);
    expect(find.text('Proxima parada'), findsOneWidget);
    expect(find.text('Pendentes'), findsOneWidget);
    expect(find.text('Aluno A'), findsOneWidget);
    expect(find.text('Aluno B'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('DriverStopList mostra empty state para fila vazia', (
    tester,
  ) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: DriverStopList(stops: [], routeName: 'Linha Manha'),
        ),
      ),
    );

    expect(find.text('Sem paradas operacionais'), findsOneWidget);
    expect(find.text('Fila de paradas'), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('DriverStopList destaca unica proxima parada', (tester) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: DriverStopList(stops: [_stop], routeName: 'Linha Manha'),
        ),
      ),
    );

    expect(find.text('Proxima parada'), findsOneWidget);
    expect(find.text('Aluno A'), findsOneWidget);
    expect(find.text('Pendentes'), findsNothing);
    expect(find.text('Resolvidas'), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('DriverStopList separa oito paradas por prioridade e status', (
    tester,
  ) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: SingleChildScrollView(
            child: DriverStopList(
              stops: [
                _stop,
                _pendingStop,
                _presencePendingStop,
                _completedStop,
                _skippedStop,
                _noShowStop,
                _pendingStopTwo,
                _pendingStopThree,
              ],
              routeName: 'Linha Manha',
            ),
          ),
        ),
      ),
    );

    expect(find.text('Proxima parada'), findsOneWidget);
    expect(find.text('Pendentes'), findsOneWidget);
    expect(find.text('Presenca a confirmar'), findsOneWidget);
    expect(find.text('Resolvidas'), findsOneWidget);
    expect(find.text('Aluno A'), findsOneWidget);
    expect(find.text('Aluno B'), findsOneWidget);
    expect(find.text('Aluno C'), findsOneWidget);
    expect(find.text('Aluno D'), findsOneWidget);
    expect(find.text('Pulada'), findsOneWidget);
    expect(find.textContaining('embarcou'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}

final _trip = DriverTripRecord(
  id: 93,
  rotaBaseId: 4,
  rotaNome: 'Rota Centro',
  servicoNome: 'Linha Manha',
  tipoOperacao: 'IDA',
  dataOperacao: DateTime(2026, 3, 13),
  status: TripTrackingStatus.inProgress,
  rotaVersao: 2,
  etaMinutosAteProximaParada: 9,
  etaAtualizadoEm: null,
  motorista: null,
  proximaParada: _stop,
  paradas: [_stop, _secondStop],
  rotaGeografica: null,
  horarioPartida: '07:30',
);

const _stop = DriverTripStopRecord(
  id: 11,
  ordem: 1,
  status: TripTrackingStopStatus.pending,
  etaMinutos: 9,
  confirmadoPresenca: true,
  proximaParada: true,
  passageiroId: 42,
  passageiroNome: 'Aluno A',
  menorDeIdade: false,
);

const _secondStop = DriverTripStopRecord(
  id: 12,
  ordem: 2,
  status: TripTrackingStopStatus.pending,
  etaMinutos: 14,
  confirmadoPresenca: true,
  proximaParada: false,
  passageiroId: 43,
  passageiroNome: 'Aluno B',
  menorDeIdade: false,
);

const _pendingStop = _secondStop;

const _presencePendingStop = DriverTripStopRecord(
  id: 13,
  ordem: 3,
  status: TripTrackingStopStatus.pending,
  etaMinutos: 18,
  confirmadoPresenca: false,
  proximaParada: false,
  passageiroId: 44,
  passageiroNome: 'Aluno C',
  menorDeIdade: false,
);

const _completedStop = DriverTripStopRecord(
  id: 14,
  ordem: 4,
  status: TripTrackingStopStatus.completed,
  etaMinutos: 0,
  confirmadoPresenca: true,
  proximaParada: false,
  passageiroId: 45,
  passageiroNome: 'Aluno D',
  menorDeIdade: false,
);

const _skippedStop = DriverTripStopRecord(
  id: 15,
  ordem: 5,
  status: TripTrackingStopStatus.skipped,
  etaMinutos: 0,
  confirmadoPresenca: true,
  proximaParada: false,
  passageiroId: 46,
  passageiroNome: 'Aluno E',
  menorDeIdade: false,
);

const _noShowStop = DriverTripStopRecord(
  id: 16,
  ordem: 6,
  status: TripTrackingStopStatus.noShow,
  etaMinutos: 0,
  confirmadoPresenca: true,
  proximaParada: false,
  passageiroId: 47,
  passageiroNome: 'Aluno F',
  menorDeIdade: false,
);

const _pendingStopTwo = DriverTripStopRecord(
  id: 17,
  ordem: 7,
  status: TripTrackingStopStatus.pending,
  etaMinutos: 22,
  confirmadoPresenca: true,
  proximaParada: false,
  passageiroId: 48,
  passageiroNome: 'Aluno G',
  menorDeIdade: true,
);

const _pendingStopThree = DriverTripStopRecord(
  id: 18,
  ordem: 8,
  status: TripTrackingStopStatus.pending,
  etaMinutos: 26,
  confirmadoPresenca: true,
  proximaParada: false,
  passageiroId: 49,
  passageiroNome: 'Aluno H',
  menorDeIdade: false,
);
