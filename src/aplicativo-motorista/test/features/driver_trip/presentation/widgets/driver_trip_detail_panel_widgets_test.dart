import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/domain/driver_trip_record.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/presentation/widgets/driver_home_widgets.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_record.dart';

const _mapsKey = Key('driver.detail.maps');
const _presenceKey = Key('driver.detail.presence');
const _editRouteKey = Key('driver.detail.editRoute');
const _skipStopKey = Key('driver.detail.skipStop');
const _noShowStopKey = Key('driver.detail.noShowStop');
const _endTripKey = Key('driver.detail.endTrip');

void main() {
  testWidgets('filtra fila por paradas pendentes no painel de detalhes', (
    tester,
  ) async {
    var selectedFilter = DriverStopFilter.all;

    await tester.pumpWidget(
      _panelApp(
        trip: _trip(status: TripTrackingStatus.planned),
        filteredStops: [_pendingStop],
        stopFilter: DriverStopFilter.pending,
        onStopFilterSelected: (value) => selectedFilter = value,
      ),
    );

    expect(find.text('Ana Pendente'), findsOneWidget);
    expect(find.text('Bruno Confirmado'), findsNothing);

    await tester.tap(find.text('Confirmados'));
    await tester.pumpAndSettle();

    expect(selectedFilter, DriverStopFilter.confirmed);
  });

  testWidgets('modo planejamento mostra mapas, presencas e edicao de rota', (
    tester,
  ) async {
    var editRouteTapped = false;

    await tester.pumpWidget(
      _panelApp(
        trip: _trip(status: TripTrackingStatus.planned),
        filteredStops: _stops,
        initialTabIndex: 1,
        onEditRoute: () => editRouteTapped = true,
      ),
    );

    await _scrollOverviewActionsIntoView(tester);

    expect(find.byKey(_mapsKey), findsOneWidget);
    expect(find.byKey(_presenceKey), findsOneWidget);
    expect(find.byKey(_editRouteKey), findsOneWidget);
    expect(find.byKey(_skipStopKey), findsNothing);
    expect(find.byKey(_noShowStopKey), findsNothing);
    expect(find.byKey(_endTripKey), findsNothing);

    await tester.tap(find.byKey(_editRouteKey));
    expect(editRouteTapped, isTrue);
  });

  testWidgets('modo em andamento mostra acoes operacionais da proxima parada', (
    tester,
  ) async {
    var noShowTapped = false;

    await tester.pumpWidget(
      _panelApp(
        trip: _trip(status: TripTrackingStatus.inProgress),
        filteredStops: _stops,
        initialTabIndex: 1,
        onNoShowStop: () => noShowTapped = true,
      ),
    );

    await _scrollOverviewActionsIntoView(tester);

    expect(find.byKey(_mapsKey), findsOneWidget);
    expect(find.byKey(_presenceKey), findsNothing);
    expect(find.byKey(_editRouteKey), findsNothing);
    expect(find.byKey(_skipStopKey), findsOneWidget);
    expect(find.byKey(_noShowStopKey), findsOneWidget);
    expect(find.byKey(_endTripKey), findsOneWidget);

    await tester.tap(find.byKey(_noShowStopKey));
    expect(noShowTapped, isTrue);
  });
}

Future<void> _scrollOverviewActionsIntoView(WidgetTester tester) async {
  await tester.drag(find.byType(ListView), const Offset(0, -500));
  await tester.pumpAndSettle();
}

Widget _panelApp({
  required DriverTripRecord trip,
  required List<DriverTripStopRecord> filteredStops,
  DriverStopFilter stopFilter = DriverStopFilter.all,
  ValueChanged<DriverStopFilter>? onStopFilterSelected,
  VoidCallback? onOpenMaps,
  VoidCallback? onOpenPresence,
  VoidCallback? onEditRoute,
  VoidCallback? onSkipStop,
  VoidCallback? onNoShowStop,
  VoidCallback? onEndTrip,
  int initialTabIndex = 0,
}) {
  return MaterialApp(
    home: Scaffold(
      body: DriverTripDetailPanel(
        trip: trip,
        editingRoute: false,
        editableStops: const [],
        filteredStops: filteredStops,
        stopFilter: stopFilter,
        onStopFilterSelected: onStopFilterSelected ?? (_) {},
        actionKeys: const DriverTripDetailActionKeys(
          maps: _mapsKey,
          presence: _presenceKey,
          editRoute: _editRouteKey,
          skipStop: _skipStopKey,
          noShowStop: _noShowStopKey,
          endTrip: _endTripKey,
        ),
        openingNavigation: false,
        runningLifecycleAction: false,
        onOpenMaps: onOpenMaps ?? () {},
        onOpenPresence: onOpenPresence ?? () {},
        onEditRoute: onEditRoute,
        onSkipStop: onSkipStop,
        onNoShowStop: onNoShowStop,
        onEndTrip: onEndTrip,
        initialTabIndex: initialTabIndex,
      ),
    ),
  );
}

DriverTripRecord _trip({required TripTrackingStatus status}) {
  return DriverTripRecord(
    id: 11,
    rotaBaseId: 4,
    rotaNome: 'Rota Centro',
    servicoNome: 'Linha 07:00',
    dataOperacao: DateTime(2026, 3, 13),
    status: status,
    rotaVersao: 2,
    etaMinutosAteProximaParada: 9,
    etaAtualizadoEm: DateTime(2026, 3, 18, 23, 38),
    motorista: const TripTrackingDriverRecord(id: 7, nome: 'Motorista QA'),
    proximaParada: _pendingStop,
    paradas: _stops,
    rotaGeografica: null,
  );
}

const _pendingStop = DriverTripStopRecord(
  id: 80,
  ordem: 1,
  status: TripTrackingStopStatus.pending,
  etaMinutos: 9,
  confirmadoPresenca: false,
  proximaParada: true,
  passageiroId: 30,
  passageiroNome: 'Ana Pendente',
  menorDeIdade: false,
);

const _confirmedStop = DriverTripStopRecord(
  id: 90,
  ordem: 2,
  status: TripTrackingStopStatus.pending,
  etaMinutos: 15,
  confirmadoPresenca: true,
  proximaParada: false,
  passageiroId: 40,
  passageiroNome: 'Bruno Confirmado',
  menorDeIdade: false,
);

const _stops = [_pendingStop, _confirmedStop];
