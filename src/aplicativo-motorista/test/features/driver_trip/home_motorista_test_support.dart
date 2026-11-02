import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/account/data/account_profile_service.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/data/driver_trip_service.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/domain/driver_trip_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/data/trip_live_location_service.dart';
import 'package:vanrides_frontend_dev/features/tracking/data/trip_navigation_launcher.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_live_location_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_route_geometry_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_record.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/trip_live_map_panel.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/presentation/home_motorista_screen.dart';
import 'package:vanrides_frontend_dev/features/navigation/data/driver_navigation_action_queue_store.dart';
import 'package:vanrides_frontend_dev/features/navigation/data/driver_navigation_route_plan_contract.dart';
import 'package:vanrides_frontend_dev/features/navigation/data/driver_navigation_route_plan_source.dart';
import 'package:vanrides_frontend_dev/features/navigation/domain/driver_navigation_action_queue.dart';
import 'package:vanrides_frontend_dev/services/location_gateway.dart';
import 'package:vanrides_frontend_dev/services/websocket_service.dart';

import '../../support/fakes/fake_websocket_service.dart';

class FakeDriverTripGateway implements DriverTripGateway {
  FakeDriverTripGateway(
    this.record, {
    this.dayTrips,
    this.reorderedRecord,
    this.startedRecord,
    this.completedStopRecord,
    this.skippedStopRecord,
    this.noShowStopRecord,
    this.endedRecord,
    this.completeStopError,
    this.skipStopError,
    this.noShowStopError,
  });

  final DriverTripRecord? record;
  final List<DriverTripRecord>? dayTrips;
  final DriverTripRecord? reorderedRecord;
  final DriverTripRecord? startedRecord;
  final DriverTripRecord? completedStopRecord;
  final DriverTripRecord? skippedStopRecord;
  final DriverTripRecord? noShowStopRecord;
  final DriverTripRecord? endedRecord;
  Object? completeStopError;
  final Object? skipStopError;
  final Object? noShowStopError;
  List<int>? lastReorderStopIds;
  int? lastTripId;
  int? lastLifecycleTripId;
  int? lastLifecycleStopId;

  @override
  Future<DriverTripRecord?> fetchTodayTrip(int motoristaId) async {
    await Future<void>.delayed(Duration.zero);
    return record;
  }

  @override
  Future<List<DriverTripRecord>> fetchDayTrips(int motoristaId) async {
    await Future<void>.delayed(Duration.zero);
    final trips = dayTrips;
    if (trips != null) {
      return trips;
    }
    return record == null ? const [] : [record!];
  }

  @override
  Future<DriverTripRecord> reorderTrip(int tripId, List<int> stopIds) async {
    await Future<void>.delayed(Duration.zero);
    lastTripId = tripId;
    lastReorderStopIds = stopIds;
    return reorderedRecord ?? record!;
  }

  @override
  Future<DriverTripRecord> startTrip(int tripId) async {
    await Future<void>.delayed(Duration.zero);
    lastLifecycleTripId = tripId;
    return startedRecord ?? record!;
  }

  @override
  Future<DriverTripRecord> completeStop(int tripId, int stopId) async {
    await Future<void>.delayed(Duration.zero);
    lastLifecycleTripId = tripId;
    lastLifecycleStopId = stopId;
    final error = completeStopError;
    if (error != null) {
      completeStopError = null;
      throw error;
    }
    return completedStopRecord ?? record!;
  }

  @override
  Future<DriverTripRecord> skipStop(int tripId, int stopId) async {
    await Future<void>.delayed(Duration.zero);
    lastLifecycleTripId = tripId;
    lastLifecycleStopId = stopId;
    final error = skipStopError;
    if (error != null) {
      throw error;
    }
    return skippedStopRecord ?? record!;
  }

  @override
  Future<DriverTripRecord> noShowStop(int tripId, int stopId) async {
    await Future<void>.delayed(Duration.zero);
    lastLifecycleTripId = tripId;
    lastLifecycleStopId = stopId;
    final error = noShowStopError;
    if (error != null) {
      throw error;
    }
    return noShowStopRecord ?? record!;
  }

  @override
  Future<DriverTripRecord> endTrip(int tripId) async {
    await Future<void>.delayed(Duration.zero);
    lastLifecycleTripId = tripId;
    return endedRecord ?? record!;
  }
}

class FakeNavigationActionQueueStore
    implements DriverNavigationActionQueueStore {
  FakeNavigationActionQueueStore([
    this.queue = const DriverNavigationActionQueue.empty(),
  ]);

  DriverNavigationActionQueue queue;
  final writes = <DriverNavigationActionQueue>[];

  @override
  Future<DriverNavigationActionQueue> read() async {
    return queue;
  }

  @override
  Future<void> write(DriverNavigationActionQueue nextQueue) async {
    queue = nextQueue;
    writes.add(nextQueue);
  }

  @override
  Future<DriverNavigationActionQueue> enqueue({
    required int tripId,
    required int stopId,
    required String stopName,
    required DriverNavigationQueuedActionType type,
    required DateTime createdAt,
  }) async {
    final nextQueue = queue.enqueue(
      tripId: tripId,
      stopId: stopId,
      stopName: stopName,
      type: type,
      createdAt: createdAt,
    );
    await write(nextQueue);
    return nextQueue;
  }
}

class FakeTripLiveLocationGateway implements TripLiveLocationGateway {
  const FakeTripLiveLocationGateway(this.record);

  final TripLiveLocationRecord? record;

  @override
  Future<TripLiveLocationRecord?> fetchLatestLocation(int motoristaId) async {
    await Future<void>.delayed(Duration.zero);
    return record;
  }
}

class RecordingNavigationLauncher implements TripNavigationLauncher {
  final openedUrls = <String>[];

  @override
  Future<bool> open(String url) async {
    openedUrls.add(url);
    return true;
  }
}

class FakeNavigationRoutePlanSource
    implements DriverNavigationRoutePlanSnapshotSource {
  FakeNavigationRoutePlanSource(this.response);

  final DriverNavigationRoutePlanResponse? response;
  final requestedTripIds = <int>[];

  @override
  DriverNavigationRoutePlanResponse? snapshotForTrip(DriverTripRecord trip) {
    requestedTripIds.add(trip.id);
    return response;
  }
}

TripRouteGeometryRecord sampleDriverGeometry({
  String navigationUrl = 'https://maps.google.com/?q=feevale',
  String? remainingNavigationUrl,
}) {
  return TripRouteGeometryRecord(
    provider: 'GOOGLE_DIRECTIONS_V1',
    polyline: '',
    navegacao: TripNavigationRecord(
      provider: 'GOOGLE_MAPS_HANDOFF',
      url: navigationUrl,
      origem: 'Origem',
      destino: 'Destino',
      quantidadeWaypoints: 2,
    ),
    navegacaoRestante:
        remainingNavigationUrl == null
            ? null
            : TripNavigationRecord(
              provider: 'GOOGLE_MAPS_HANDOFF',
              url: remainingNavigationUrl,
              origem: 'Motorista',
              destino: 'Destino',
              quantidadeWaypoints: 1,
            ),
    waypoints: const [
      TripRouteWaypointRecord(
        tipo: 'STOP',
        paradaId: 80,
        passageiroId: 30,
        nome: 'Ana',
        latitude: -29.68,
        longitude: -51.13,
        ordem: 1,
        proximaParada: false,
        paradaDoPassageiro: false,
      ),
      TripRouteWaypointRecord(
        tipo: 'STOP',
        paradaId: 90,
        passageiroId: 40,
        nome: 'Joao',
        latitude: -29.69,
        longitude: -51.14,
        ordem: 2,
        proximaParada: true,
        paradaDoPassageiro: false,
      ),
    ],
    legs: const [],
    filaTotalParadas: 2,
    posicaoPassageiroNaFila: null,
    paradasAntesDoPassageiro: null,
  );
}

DriverTripRecord sampleDriverTripRecord({
  required int id,
  required String passageiroNome,
  required String horarioPartida,
  TripTrackingStatus status = TripTrackingStatus.planned,
  String tipoOperacao = 'IDA',
  TripRouteGeometryRecord? rotaGeografica,
  DriverNavigationRoutePlanResponse? navigationRoutePlan,
}) {
  final stop = DriverTripStopRecord(
    id: id + 100,
    ordem: 1,
    status: TripTrackingStopStatus.pending,
    etaMinutos: 9,
    confirmadoPresenca: true,
    proximaParada: true,
    passageiroId: id + 200,
    passageiroNome: passageiroNome,
    menorDeIdade: false,
  );
  return DriverTripRecord(
    id: id,
    rotaBaseId: 4,
    rotaNome: 'Rota Centro',
    dataOperacao: DateTime(2026, 3, 13),
    status: status,
    rotaVersao: 2,
    etaMinutosAteProximaParada: 9,
    etaAtualizadoEm: DateTime(2026, 3, 18, 23, 38),
    motorista: const TripTrackingDriverRecord(id: 7, nome: 'Motorista QA'),
    proximaParada: stop,
    paradas: [stop],
    rotaGeografica: rotaGeografica ?? sampleDriverGeometry(),
    navigationRoutePlan: navigationRoutePlan,
    horarioPartida: horarioPartida,
    servicoNome: 'Linha $horarioPartida',
    tipoOperacao: tipoOperacao,
  );
}

DriverNavigationRoutePlanResponse sampleRoutePlanResponse({
  required String coverageStatus,
  required String offlinePackStatus,
}) {
  return DriverNavigationRoutePlanResponse.fromJson({
    'routeId': 'home-route-plan-test-001',
    'provider': 'fixture',
    'region': 'BR-RS',
    'vehicleType': 'van_school_safe',
    'coverageStatus': coverageStatus,
    'offlinePackStatus': offlinePackStatus,
    'qualityScore': 0.84,
    'estimatedDurationSeconds': 480,
    'estimatedDistanceMeters': 1900,
    'polyline': const [
      [-30.0346, -51.2177],
    ],
    'legs': const [
      {
        'fromStopId': 'origin',
        'toStopId': 'stop-1',
        'distanceMeters': 1900,
        'durationSeconds': 480,
      },
    ],
    'instructions': const [
      {
        'index': 0,
        'text': 'Siga pela Avenida Ipiranga.',
        'voiceText': 'Em 200 metros, siga pela Avenida Ipiranga.',
        'distanceMeters': 200,
        'maneuver': 'continue',
      },
    ],
  });
}

Future<void> pumpHomeMotorista(
  WidgetTester tester, {
  required DriverTripGateway tripGateway,
  int motoristaId = 7,
  String? accessToken,
  int? initialTripId,
  TripLiveLocationGateway liveLocationGateway =
      const FakeTripLiveLocationGateway(null),
  WebSocketServiceBase? liveLocationWebSocketService,
  TripNavigationLauncher? navigationLauncher,
  LocationGateway? locationGateway,
  WebSocketServiceBase? locationSharingWebSocketService,
  AccountProfileGateway? accountProfileGateway,
  DriverNavigationActionQueueStore? navigationActionQueueStore,
  DriverNavigationRoutePlanSnapshotSource? navigationRoutePlanSource,
  TripLiveMapRenderMode mapRenderMode = TripLiveMapRenderMode.placeholder,
}) async {
  await tester.pumpWidget(
    MaterialApp(
      home: HomeMotoristaScreen(
        nome: 'Motorista QA',
        motoristaId: motoristaId,
        email: 'motorista@vanrides.dev',
        senha: 'Secret123',
        accessToken: accessToken ?? jwtWithCompanyId(1),
        initialTripId: initialTripId,
        tripGateway: tripGateway,
        liveLocationGateway: liveLocationGateway,
        liveLocationWebSocketService:
            liveLocationWebSocketService ?? FakeWebSocketService(),
        navigationLauncher: navigationLauncher,
        locationGateway: locationGateway,
        locationSharingWebSocketService: locationSharingWebSocketService,
        accountProfileGateway: accountProfileGateway,
        navigationActionQueueStore: navigationActionQueueStore,
        navigationRoutePlanSource: navigationRoutePlanSource,
        mapRenderMode: mapRenderMode,
      ),
    ),
  );
}

Future<void> scrollToAndEnsureVisible(
  WidgetTester tester,
  Finder target,
) async {
  await tester.ensureVisible(target);
  await tester.pumpAndSettle();
}

Future<void> setPhoneViewport(WidgetTester tester) async {
  tester.view.physicalSize = const Size(1179, 2556);
  tester.view.devicePixelRatio = 3;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);
}

Future<void> invokeButton(WidgetTester tester, Finder finder) async {
  final dynamic button = tester.widget(finder);
  final VoidCallback? onPressed = button.onPressed as VoidCallback?;
  onPressed?.call();
  await tester.pumpAndSettle();
}

String jwtWithCompanyId(int companyId) {
  return 'eyJhbGciOiJIUzI1NiJ9.${base64Url.encode(utf8.encode('{"companyId":$companyId}'))}.signature';
}
