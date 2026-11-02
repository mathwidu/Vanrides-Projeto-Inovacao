import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/domain/driver_trip_record.dart';
import 'package:vanrides_frontend_dev/features/navigation/data/driver_navigation_route_plan_contract.dart';
import 'package:vanrides_frontend_dev/features/navigation/domain/driver_navigation_models.dart';
import 'package:vanrides_frontend_dev/features/navigation/presentation/driver_navigation_presenter.dart';
import 'package:vanrides_frontend_dev/features/tracking/domain/trip_tracking_record.dart';

void main() {
  test('parses the Maps Platform route plan response contract', () {
    final response = DriverNavigationRoutePlanResponse.fromJson(
      _routePlanFixture(),
    );

    expect(response.routeId, 'fixture-br-rs-porto-alegre-001');
    expect(response.provider, 'fixture');
    expect(response.region, 'BR-RS');
    expect(response.vehicleType, 'van_school_safe');
    expect(response.coverageStatus, 'fallback');
    expect(response.offlinePackStatus, 'missing');
    expect(response.qualityScore, 0.42);
    expect(response.estimatedDurationSeconds, 780);
    expect(response.estimatedDistanceMeters, 4200);
    expect(response.instructions, hasLength(1));
    expect(response.instructions.single.index, 0);
    expect(response.instructions.single.text, 'Siga ate a proxima parada.');
    expect(
      response.instructions.single.voiceText,
      'Siga ate a proxima parada.',
    );
    expect(response.instructions.single.distanceMeters, 2100);
    expect(response.instructions.single.maneuver, 'depart');
  });

  test('keeps fixture fallback as external fallback for the driver', () {
    final response = DriverNavigationRoutePlanResponse.fromJson(
      _routePlanFixture(),
    );

    final session = response.toNavigationSession(trip: _trip());
    final viewModel = const DriverNavigationPresenter().present(session);

    expect(session.coverageStatus, DriverNavigationCoverageStatus.fallback);
    expect(session.offlineState, DriverNavigationOfflineState.missing);
    expect(
      session.fallbackReason,
      DriverNavigationFallbackReason.routeNotValidated,
    );
    expect(session.currentInstruction, isNull);
    expect(viewModel.mode, DriverNavigationMode.externalFallback);
    expect(viewModel.fallbackIsPrimary, isTrue);
    expect(viewModel.canUseInternalNavigation, isFalse);
    expect(viewModel.voicePrompt, isNull);
    expect(
      viewModel.instructionText,
      'Use o fallback para seguir com seguranca',
    );
    expect(
      viewModel.helperText,
      'Rota ainda em validacao. Abra o mapa externo para seguir.',
    );
    expect(viewModel.helperText, isNot(contains('fixture_route')));
  });

  test('normalizes known technical fallback reasons', () {
    final scenarios = <String, DriverNavigationFallbackReason>{
      'coverage_not_validated_for_navigation':
          DriverNavigationFallbackReason.routeNotValidated,
      'valhalla_unavailable_osrm_fallback':
          DriverNavigationFallbackReason.routeNotValidated,
      'routing_provider_unavailable':
          DriverNavigationFallbackReason.routingProviderUnavailable,
      'future_reason': DriverNavigationFallbackReason.unknown,
    };

    for (final entry in scenarios.entries) {
      final session = DriverNavigationRoutePlanResponse.fromJson(
        _routePlanFixture(fallbackReason: entry.key),
      ).toNavigationSession(trip: _trip());

      expect(session.fallbackReason, entry.value, reason: entry.key);
    }
  });

  test('promotes only validated coverage with a ready offline pack', () {
    final response = DriverNavigationRoutePlanResponse.fromJson(
      _routePlanFixture(
        coverageStatus: 'validated',
        offlinePackStatus: 'ready',
        provider: 'valhalla',
      ),
    );

    final session = response.toNavigationSession(trip: _trip());
    final viewModel = const DriverNavigationPresenter().present(session);

    expect(session.coverageStatus, DriverNavigationCoverageStatus.validated);
    expect(session.offlineState, DriverNavigationOfflineState.ready);
    expect(session.currentInstruction?.id, 'fixture-br-rs-porto-alegre-001:0');
    expect(session.currentInstruction?.text, 'Siga ate a proxima parada.');
    expect(session.currentInstruction?.voiceText, 'Siga ate a proxima parada.');
    expect(session.currentInstruction?.distanceMeters, 2100);
    expect(viewModel.mode, DriverNavigationMode.internal);
    expect(viewModel.fallbackIsPrimary, isFalse);
    expect(viewModel.canUseInternalNavigation, isTrue);
    expect(viewModel.voicePrompt, 'Siga ate a proxima parada.');
  });

  test('normalizes Portuguese coverage statuses from backend contract', () {
    final validated = DriverNavigationRoutePlanResponse.fromJson(
      _routePlanFixture(
        coverageStatus: 'validada',
        offlinePackStatus: 'ready',
        provider: 'valhalla',
      ),
    ).toNavigationSession(trip: _trip());

    expect(validated.coverageStatus, DriverNavigationCoverageStatus.validated);
    expect(validated.offlineState, DriverNavigationOfflineState.ready);
    expect(validated.currentInstruction?.text, 'Siga ate a proxima parada.');

    final observation = DriverNavigationRoutePlanResponse.fromJson(
      _routePlanFixture(
        coverageStatus: 'em_observacao',
        offlinePackStatus: 'ready',
        provider: 'valhalla',
      ),
    ).toNavigationSession(trip: _trip());

    expect(
      observation.coverageStatus,
      DriverNavigationCoverageStatus.observation,
    );
    expect(observation.currentInstruction, isNull);

    final blocked = DriverNavigationRoutePlanResponse.fromJson(
      _routePlanFixture(
        coverageStatus: 'bloqueada',
        offlinePackStatus: 'ready',
        provider: 'valhalla',
      ),
    ).toNavigationSession(trip: _trip());

    expect(blocked.coverageStatus, DriverNavigationCoverageStatus.blocked);
    expect(blocked.currentInstruction, isNull);
  });

  test('normalizes Portuguese offline pack statuses from backend contract', () {
    final ready = DriverNavigationRoutePlanResponse.fromJson(
      _routePlanFixture(
        coverageStatus: 'validada',
        offlinePackStatus: 'pronto',
        provider: 'valhalla',
      ),
    ).toNavigationSession(trip: _trip());
    final readyViewModel = const DriverNavigationPresenter().present(ready);

    expect(ready.coverageStatus, DriverNavigationCoverageStatus.validated);
    expect(ready.offlineState, DriverNavigationOfflineState.ready);
    expect(ready.currentInstruction?.text, 'Siga ate a proxima parada.');
    expect(readyViewModel.mode, DriverNavigationMode.internal);

    final expectedOfflineStates = <String, DriverNavigationOfflineState>{
      'ausente': DriverNavigationOfflineState.missing,
      'desatualizado': DriverNavigationOfflineState.outdated,
      'parcial': DriverNavigationOfflineState.partial,
      'falhou': DriverNavigationOfflineState.failed,
    };

    for (final entry in expectedOfflineStates.entries) {
      final session = DriverNavigationRoutePlanResponse.fromJson(
        _routePlanFixture(
          coverageStatus: 'validada',
          offlinePackStatus: entry.key,
          provider: 'valhalla',
        ),
      ).toNavigationSession(trip: _trip());
      final viewModel = const DriverNavigationPresenter().present(session);

      expect(session.offlineState, entry.value, reason: entry.key);
      expect(session.currentInstruction, isNull, reason: entry.key);
      expect(viewModel.canUseInternalNavigation, isFalse, reason: entry.key);
    }

    final unknown = DriverNavigationRoutePlanResponse.fromJson(
      _routePlanFixture(
        coverageStatus: 'validada',
        offlinePackStatus: 'perfeito',
        provider: 'valhalla',
      ),
    ).toNavigationSession(trip: _trip());

    expect(unknown.offlineState, DriverNavigationOfflineState.missing);
    expect(unknown.currentInstruction, isNull);
  });

  test('unknown status or missing instruction never promotes navigation', () {
    final unknownStatus = DriverNavigationRoutePlanResponse.fromJson(
      _routePlanFixture(coverageStatus: 'perfect', offlinePackStatus: 'ready'),
    ).toNavigationSession(trip: _trip());

    expect(
      unknownStatus.coverageStatus,
      DriverNavigationCoverageStatus.blocked,
    );
    expect(unknownStatus.offlineState, DriverNavigationOfflineState.ready);
    expect(unknownStatus.currentInstruction, isNull);

    final missingInstruction = DriverNavigationRoutePlanResponse.fromJson(
      _routePlanFixture(
        coverageStatus: 'validated',
        offlinePackStatus: 'ready',
        instructions: const [],
      ),
    ).toNavigationSession(trip: _trip());
    final viewModel = const DriverNavigationPresenter().present(
      missingInstruction,
    );

    expect(
      missingInstruction.coverageStatus,
      DriverNavigationCoverageStatus.blocked,
    );
    expect(missingInstruction.offlineState, DriverNavigationOfflineState.ready);
    expect(missingInstruction.currentInstruction, isNull);
    expect(viewModel.mode, DriverNavigationMode.externalFallback);
    expect(viewModel.canUseInternalNavigation, isFalse);
  });
}

Map<String, dynamic> _routePlanFixture({
  String coverageStatus = 'fallback',
  String offlinePackStatus = 'missing',
  String fallbackReason = 'fixture_route_not_validated_for_navigation',
  String provider = 'fixture',
  List<Map<String, dynamic>>? instructions,
}) {
  return {
    'routeId': 'fixture-br-rs-porto-alegre-001',
    'provider': provider,
    'region': 'BR-RS',
    'vehicleType': 'van_school_safe',
    'coverageStatus': coverageStatus,
    'offlinePackStatus': offlinePackStatus,
    'qualityScore': 0.42,
    'fallbackReason': fallbackReason,
    'estimatedDurationSeconds': 780,
    'estimatedDistanceMeters': 4200,
    'polyline': [
      [-30.0346, -51.2177],
    ],
    'legs': [
      {
        'fromStopId': 'origin',
        'toStopId': 'stop-1',
        'distanceMeters': 2100,
        'durationSeconds': 390,
      },
    ],
    'instructions':
        instructions ??
        [
          {
            'index': 0,
            'text': 'Siga ate a proxima parada.',
            'voiceText': 'Siga ate a proxima parada.',
            'distanceMeters': 2100,
            'maneuver': 'depart',
          },
        ],
  };
}

DriverTripRecord _trip() {
  return DriverTripRecord(
    id: 91,
    rotaBaseId: 4,
    rotaNome: 'Rota Centro',
    servicoNome: 'Linha Manha',
    dataOperacao: DateTime(2026, 7, 5),
    status: TripTrackingStatus.inProgress,
    rotaVersao: 3,
    etaMinutosAteProximaParada: 9,
    etaAtualizadoEm: DateTime(2026, 7, 5, 7, 30),
    motorista: const TripTrackingDriverRecord(id: 7, nome: 'Motorista QA'),
    proximaParada: _stop,
    paradas: const [_stop],
    rotaGeografica: null,
  );
}

const _stop = DriverTripStopRecord(
  id: 80,
  ordem: 1,
  status: TripTrackingStopStatus.pending,
  etaMinutos: 9,
  confirmadoPresenca: true,
  proximaParada: true,
  passageiroId: 30,
  passageiroNome: 'Ana Clara',
  menorDeIdade: false,
);
