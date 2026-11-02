import 'package:vanrides_frontend_dev/features/driver_trip/domain/driver_trip_record.dart';
import 'package:vanrides_frontend_dev/features/navigation/domain/driver_navigation_action_queue.dart';
import 'package:vanrides_frontend_dev/features/navigation/domain/driver_navigation_models.dart';

class DriverNavigationRoutePlanResponse {
  const DriverNavigationRoutePlanResponse({
    required this.routeId,
    required this.provider,
    required this.region,
    required this.vehicleType,
    required this.coverageStatus,
    required this.offlinePackStatus,
    required this.qualityScore,
    required this.fallbackReason,
    required this.estimatedDurationSeconds,
    required this.estimatedDistanceMeters,
    required this.polyline,
    required this.legs,
    required this.instructions,
  });

  final String routeId;
  final String provider;
  final String region;
  final String vehicleType;
  final String coverageStatus;
  final String offlinePackStatus;
  final double? qualityScore;
  final String fallbackReason;
  final int? estimatedDurationSeconds;
  final int? estimatedDistanceMeters;
  final List<DriverNavigationRoutePlanCoordinate> polyline;
  final List<DriverNavigationRoutePlanLeg> legs;
  final List<DriverNavigationRoutePlanInstruction> instructions;

  factory DriverNavigationRoutePlanResponse.fromJson(
    Map<String, dynamic> json,
  ) {
    final polylineJson = json['polyline'] as List<dynamic>? ?? const [];
    final legsJson = json['legs'] as List<dynamic>? ?? const [];
    final instructionsJson = json['instructions'] as List<dynamic>? ?? const [];

    return DriverNavigationRoutePlanResponse(
      routeId: _readString(json['routeId']),
      provider: _readString(json['provider']),
      region: _readString(json['region']),
      vehicleType: _readString(json['vehicleType']),
      coverageStatus: _readString(json['coverageStatus']),
      offlinePackStatus: _readString(json['offlinePackStatus']),
      qualityScore: _readDouble(json['qualityScore']),
      fallbackReason: _readString(json['fallbackReason']),
      estimatedDurationSeconds: _readInt(json['estimatedDurationSeconds']),
      estimatedDistanceMeters: _readInt(json['estimatedDistanceMeters']),
      polyline: polylineJson
          .map(DriverNavigationRoutePlanCoordinate.fromJson)
          .whereType<DriverNavigationRoutePlanCoordinate>()
          .toList(growable: false),
      legs: legsJson
          .whereType<Map<String, dynamic>>()
          .map(DriverNavigationRoutePlanLeg.fromJson)
          .toList(growable: false),
      instructions: instructionsJson
          .whereType<Map<String, dynamic>>()
          .map(DriverNavigationRoutePlanInstruction.fromJson)
          .toList(growable: false),
    );
  }

  DriverNavigationSession toNavigationSession({
    required DriverTripRecord trip,
    DriverNavigationActionQueueSummary? actionQueueSummary,
    bool offRoute = false,
  }) {
    final mappedCoverage = _coverageStatusFromContract(coverageStatus);
    final mappedOfflineState = _offlineStateFromContract(offlinePackStatus);
    final instruction = _firstUsableInstruction();
    final canPromote =
        mappedCoverage == DriverNavigationCoverageStatus.validated &&
        mappedOfflineState == DriverNavigationOfflineState.ready &&
        instruction != null;

    return DriverNavigationSession(
      trip: trip,
      currentInstruction:
          canPromote ? instruction.toNavigationInstruction(routeId) : null,
      actionQueueSummary: actionQueueSummary,
      fallbackReason:
          canPromote
              ? null
              : _fallbackReasonFromContract(
                fallbackReason,
                coverageStatus: mappedCoverage,
                offlineState: mappedOfflineState,
                hasInstruction: instruction != null,
              ),
      offlineState: mappedOfflineState,
      coverageStatus:
          canPromote
              ? mappedCoverage
              : _nonPromotableCoverage(mappedCoverage, mappedOfflineState),
      offRoute: offRoute,
    );
  }

  DriverNavigationRoutePlanInstruction? _firstUsableInstruction() {
    for (final instruction in instructions) {
      if (instruction.hasUsableCopy) {
        return instruction;
      }
    }
    return null;
  }
}

class DriverNavigationRoutePlanCoordinate {
  const DriverNavigationRoutePlanCoordinate({
    required this.latitude,
    required this.longitude,
  });

  final double latitude;
  final double longitude;

  static DriverNavigationRoutePlanCoordinate? fromJson(Object? rawValue) {
    if (rawValue is! List || rawValue.length < 2) {
      return null;
    }
    final latitude = _readDouble(rawValue[0]);
    final longitude = _readDouble(rawValue[1]);
    if (latitude == null || longitude == null) {
      return null;
    }
    return DriverNavigationRoutePlanCoordinate(
      latitude: latitude,
      longitude: longitude,
    );
  }
}

class DriverNavigationRoutePlanLeg {
  const DriverNavigationRoutePlanLeg({
    required this.fromStopId,
    required this.toStopId,
    required this.distanceMeters,
    required this.durationSeconds,
  });

  final String fromStopId;
  final String toStopId;
  final int? distanceMeters;
  final int? durationSeconds;

  factory DriverNavigationRoutePlanLeg.fromJson(Map<String, dynamic> json) {
    return DriverNavigationRoutePlanLeg(
      fromStopId: _readString(json['fromStopId']),
      toStopId: _readString(json['toStopId']),
      distanceMeters: _readInt(json['distanceMeters']),
      durationSeconds: _readInt(json['durationSeconds']),
    );
  }
}

class DriverNavigationRoutePlanInstruction {
  const DriverNavigationRoutePlanInstruction({
    required this.index,
    required this.text,
    required this.voiceText,
    required this.distanceMeters,
    required this.maneuver,
  });

  final int index;
  final String text;
  final String voiceText;
  final int? distanceMeters;
  final String maneuver;

  bool get hasUsableCopy =>
      text.trim().isNotEmpty || voiceText.trim().isNotEmpty;

  factory DriverNavigationRoutePlanInstruction.fromJson(
    Map<String, dynamic> json,
  ) {
    return DriverNavigationRoutePlanInstruction(
      index: _readInt(json['index']) ?? 0,
      text: _readString(json['text']),
      voiceText: _readString(json['voiceText']),
      distanceMeters: _readInt(json['distanceMeters']),
      maneuver: _readString(json['maneuver']),
    );
  }

  DriverNavigationInstruction toNavigationInstruction(String routeId) {
    final safeRouteId =
        routeId.trim().isEmpty ? 'maps-route-plan' : routeId.trim();
    final safeText = text.trim().isNotEmpty ? text.trim() : voiceText.trim();
    final normalizedText =
        safeText.isEmpty ? 'Siga para a proxima parada' : safeText;
    final normalizedVoiceText =
        voiceText.trim().isNotEmpty ? voiceText.trim() : normalizedText;

    return DriverNavigationInstruction(
      id: '$safeRouteId:$index',
      text: normalizedText,
      voiceText: normalizedVoiceText,
      distanceMeters: distanceMeters,
    );
  }
}

DriverNavigationCoverageStatus _coverageStatusFromContract(String value) {
  switch (value.trim().toLowerCase()) {
    case 'validated':
    case 'validada':
      return DriverNavigationCoverageStatus.validated;
    case 'observation':
    case 'observacao':
    case 'em_observacao':
    case 'em-observacao':
    case 'em observacao':
      return DriverNavigationCoverageStatus.observation;
    case 'fallback':
      return DriverNavigationCoverageStatus.fallback;
    case 'blocked':
    case 'bloqueada':
    case 'bloqueado':
      return DriverNavigationCoverageStatus.blocked;
    default:
      return DriverNavigationCoverageStatus.blocked;
  }
}

DriverNavigationOfflineState _offlineStateFromContract(String value) {
  switch (value.trim().toLowerCase()) {
    case 'ready':
    case 'pronto':
      return DriverNavigationOfflineState.ready;
    case 'outdated':
    case 'desatualizado':
    case 'desatualizada':
      return DriverNavigationOfflineState.outdated;
    case 'partial':
    case 'parcial':
      return DriverNavigationOfflineState.partial;
    case 'failed':
    case 'falhou':
    case 'falha':
      return DriverNavigationOfflineState.failed;
    case 'missing':
    case 'ausente':
      return DriverNavigationOfflineState.missing;
    default:
      return DriverNavigationOfflineState.missing;
  }
}

DriverNavigationFallbackReason? _fallbackReasonFromContract(
  String value, {
  required DriverNavigationCoverageStatus coverageStatus,
  required DriverNavigationOfflineState offlineState,
  required bool hasInstruction,
}) {
  switch (value.trim().toLowerCase()) {
    case 'fixture_route_not_validated_for_navigation':
    case 'coverage_not_validated_for_navigation':
    case 'valhalla_unavailable_osrm_fallback':
      return DriverNavigationFallbackReason.routeNotValidated;
    case 'routing_provider_unavailable':
      return DriverNavigationFallbackReason.routingProviderUnavailable;
    case 'offline_pack_missing':
      return DriverNavigationFallbackReason.offlinePackMissing;
    case 'offline_pack_outdated':
      return DriverNavigationFallbackReason.offlinePackOutdated;
    case 'offline_pack_partial':
      return DriverNavigationFallbackReason.offlinePackPartial;
    case 'offline_pack_failed':
      return DriverNavigationFallbackReason.offlinePackFailed;
    case '':
      return _fallbackReasonFromState(
        coverageStatus: coverageStatus,
        offlineState: offlineState,
        hasInstruction: hasInstruction,
      );
    default:
      return DriverNavigationFallbackReason.unknown;
  }
}

DriverNavigationFallbackReason? _fallbackReasonFromState({
  required DriverNavigationCoverageStatus coverageStatus,
  required DriverNavigationOfflineState offlineState,
  required bool hasInstruction,
}) {
  switch (offlineState) {
    case DriverNavigationOfflineState.missing:
      return DriverNavigationFallbackReason.offlinePackMissing;
    case DriverNavigationOfflineState.outdated:
      return DriverNavigationFallbackReason.offlinePackOutdated;
    case DriverNavigationOfflineState.partial:
      return DriverNavigationFallbackReason.offlinePackPartial;
    case DriverNavigationOfflineState.failed:
      return DriverNavigationFallbackReason.offlinePackFailed;
    case DriverNavigationOfflineState.ready:
      break;
  }
  if (coverageStatus != DriverNavigationCoverageStatus.validated) {
    return DriverNavigationFallbackReason.routeNotValidated;
  }
  if (!hasInstruction) {
    return DriverNavigationFallbackReason.instructionMissing;
  }
  return null;
}

DriverNavigationCoverageStatus _nonPromotableCoverage(
  DriverNavigationCoverageStatus coverageStatus,
  DriverNavigationOfflineState offlineState,
) {
  if (coverageStatus == DriverNavigationCoverageStatus.validated &&
      offlineState == DriverNavigationOfflineState.ready) {
    return DriverNavigationCoverageStatus.blocked;
  }
  return coverageStatus;
}

String _readString(Object? value) {
  if (value == null) {
    return '';
  }
  return value.toString().trim();
}

int? _readInt(Object? value) {
  if (value is num) {
    return value.toInt();
  }
  if (value is String) {
    return int.tryParse(value.trim());
  }
  return null;
}

double? _readDouble(Object? value) {
  if (value is num) {
    return value.toDouble();
  }
  if (value is String) {
    return double.tryParse(value.trim());
  }
  return null;
}
