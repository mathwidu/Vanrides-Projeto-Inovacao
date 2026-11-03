import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/widgets/compact_trip_switcher.dart';

void main() {
  testWidgets('hides itself when there is only one trip', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: CompactTripSwitcher(
            dayTrips: [_trip(id: 1, horario: '07:25')],
            selectedTripId: 1,
            onTripSelected: (_) {},
          ),
        ),
      ),
    );

    expect(find.byType(CompactTripSwitcher), findsOneWidget);
    expect(find.text('07:25 • Ida • feevale'), findsNothing);
    expect(find.byType(SingleChildScrollView), findsNothing);
  });

  testWidgets('renders two trips as a segmented row', (tester) async {
    TripTrackingRecord? selected;

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: CompactTripSwitcher(
            dayTrips: [
              _trip(id: 1, horario: '07:25'),
              _trip(id: 2, horario: '18:00', tipo: 'VOLTA'),
            ],
            selectedTripId: 1,
            tripKeyBuilder: (id) => Key('trip-$id'),
            onTripSelected: (trip) => selected = trip,
          ),
        ),
      ),
    );

    expect(find.text('07:25 • Ida'), findsOneWidget);
    expect(find.text('18:00 • Volta'), findsOneWidget);
    expect(find.text('07:25 • Ida • feevale'), findsNothing);
    expect(find.text('18:00 • Volta • feevale'), findsNothing);
    expect(find.byType(SingleChildScrollView), findsNothing);

    await tester.tap(find.byKey(const Key('trip-2')));
    await tester.pump();

    expect(selected?.id, 2);
  });

  testWidgets('renders three or more trips as a visible vertical list', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: CompactTripSwitcher(
            dayTrips: [
              _trip(id: 1, horario: '07:25'),
              _trip(id: 2, horario: '12:10', tipo: 'CIRCULAR'),
              _trip(id: 3, horario: '18:00', tipo: 'VOLTA'),
            ],
            selectedTripId: 1,
            onTripSelected: (_) {},
          ),
        ),
      ),
    );

    expect(find.text('07:25 • Ida • feevale'), findsOneWidget);
    expect(find.text('12:10 • Circular • feevale'), findsOneWidget);
    expect(find.text('18:00 • Volta • feevale'), findsOneWidget);
    expect(find.byType(SingleChildScrollView), findsNothing);
    expect(find.byKey(CompactTripSwitcher.verticalListKey), findsOneWidget);
  });
}

TripTrackingRecord _trip({
  required int id,
  required String horario,
  String tipo = 'IDA',
}) {
  return TripTrackingRecord(
    id: id,
    rotaNome: 'Rota Escolar',
    servicoId: id + 100,
    servicoNome: 'feevale',
    tipoOperacao: tipo,
    dataOperacao: DateTime(2026, 6, 15),
    status: TripTrackingStatus.planned,
    rotaVersao: 1,
    etaMinutosAtePassageiro: 12,
    etaAtualizadoEm: DateTime(2026, 6, 15, 7, 10),
    motorista: const TripTrackingDriverRecord(id: 8, nome: 'Matheus do teste'),
    rotaGeografica: null,
    paradaDoPassageiro: null,
    horarioPartida: horario,
  );
}
