import 'package:flutter/material.dart';
import 'package:google_maps_flutter/google_maps_flutter.dart';

import '../../../l10n/app_strings.dart';
import '../domain/trip_live_location_record.dart';
import '../domain/trip_route_geometry_record.dart';

enum TripLiveMapRenderMode { interactive, placeholder }

enum TripLiveMapFocusMode {
  fullJourney,
  nextStopOnly,
  passengerJourney,
  vehicleOnly,
  passengerOnly,
}

class TripLiveMapPlaceholderContent {
  const TripLiveMapPlaceholderContent({
    required this.loading,
    required this.title,
    required this.description,
    required this.routeReady,
    required this.waitingForSignal,
  });

  factory TripLiveMapPlaceholderContent.from({
    required bool loading,
    required TripLiveLocationRecord? location,
    required TripRouteGeometryRecord? routeGeometry,
    required String? errorMessage,
  }) {
    final hasDrawableRoute = routeGeometry?.isDrawable ?? false;
    final routeReadyWithoutSignal = hasDrawableRoute && location == null;
    final routeReadyWithLiveSignal = hasDrawableRoute && location != null;
    final waitingForSignal = location == null && errorMessage == null;
    final usesRealRoadRouting = tripRouteUsesRealRoadRouting(routeGeometry);
    final snapshotTimeLabel = _routeSnapshotTimeLabel(routeGeometry);

    final title =
        loading
            ? AppStrings.passageiro.carregandoMapaDaViagem
            : routeReadyWithoutSignal
            ? usesRealRoadRouting
                ? AppStrings.passageiro.rotaProntaAguardandoSinal
                : AppStrings.passageiro.trajetoProntoAguardandoSinal
            : routeReadyWithLiveSignal
            ? usesRealRoadRouting
                ? AppStrings.passageiro.rotaComMotoristaAoVivo
                : AppStrings.passageiro.trajetoComMotoristaAoVivo
            : waitingForSignal
            ? AppStrings.passageiro.aguardandoLocalizacaoAoVivo
            : AppStrings.passageiro.mapaIndisponivelNoMomento;

    final description =
        loading
            ? AppStrings.passageiro.buscandoDadosOperacionais
            : routeReadyWithoutSignal
            ? snapshotTimeLabel != null
                ? AppStrings.passageiro
                    .rotaOperacionalSnapshotAguardandoPrimeiraPosicao(
                      snapshotTimeLabel,
                    )
                : usesRealRoadRouting
                ? AppStrings.passageiro.rotaDeRuasAguardandoPrimeiraPosicao
                : AppStrings
                    .passageiro
                    .trajetoAproximadoAguardandoPrimeiraPosicao
            : routeReadyWithLiveSignal
            ? snapshotTimeLabel != null
                ? AppStrings.passageiro
                    .rotaOperacionalSnapshotComMotoristaDescricao(
                      snapshotTimeLabel,
                    )
                : usesRealRoadRouting
                ? AppStrings.passageiro.rotaDeRuasComMotoristaDescricao
                : AppStrings.passageiro.trajetoAproximadoComMotoristaDescricao
            : waitingForSignal
            ? AppStrings.passageiro.semCoordenadasPublicadas
            : (errorMessage ?? AppStrings.passageiro.mapaIndisponivelNoMomento);

    return TripLiveMapPlaceholderContent(
      loading: loading,
      title: title,
      description: description,
      routeReady: routeReadyWithoutSignal || routeReadyWithLiveSignal,
      waitingForSignal: waitingForSignal,
    );
  }

  final bool loading;
  final String title;
  final String description;
  final bool routeReady;
  final bool waitingForSignal;

  IconData get icon {
    if (routeReady) {
      return Icons.alt_route_rounded;
    }
    if (waitingForSignal) {
      return Icons.wifi_tethering_off_rounded;
    }
    return Icons.warning_amber_outlined;
  }

  Color get iconColor {
    return routeReady || waitingForSignal
        ? const Color(0xFF161B36)
        : const Color(0xFFB3261E);
  }
}

bool tripRouteUsesRealRoadRouting(TripRouteGeometryRecord? routeGeometry) {
  final provider = routeGeometry?.provider.trim().toUpperCase() ?? '';
  return provider.startsWith('GOOGLE_DIRECTIONS');
}

List<TripRouteWaypointRecord> visibleTripRouteWaypoints(
  TripRouteGeometryRecord routeGeometry,
  TripLiveMapFocusMode focusMode,
) {
  final orderedStops = routeGeometry.orderedStops;
  if (focusMode == TripLiveMapFocusMode.vehicleOnly) {
    return const [];
  }
  if (focusMode == TripLiveMapFocusMode.passengerOnly) {
    for (final stop in orderedStops) {
      if (stop.paradaDoPassageiro) {
        return [stop];
      }
    }
    return const [];
  }
  if (focusMode == TripLiveMapFocusMode.nextStopOnly) {
    final nextStop = routeGeometry.nextStop;
    if (nextStop == null) {
      return orderedStops;
    }
    return [nextStop];
  }
  if (focusMode == TripLiveMapFocusMode.passengerJourney) {
    final focusedStops = <TripRouteWaypointRecord>[];
    final nextStop = routeGeometry.nextStop;
    if (nextStop != null) {
      focusedStops.add(nextStop);
    }
    for (final stop in orderedStops) {
      if (stop.paradaDoPassageiro) {
        focusedStops.add(stop);
        break;
      }
    }
    if (focusedStops.isEmpty) {
      return orderedStops;
    }
    final uniqueStops = <String, TripRouteWaypointRecord>{};
    for (final stop in focusedStops) {
      final key =
          '${stop.paradaId ?? stop.passageiroId ?? stop.nome}:${stop.ordem}';
      uniqueStops[key] = stop;
    }
    return uniqueStops.values.toList(growable: false);
  }
  return orderedStops;
}

List<LatLng> collectTripLiveMapPoints({
  required TripLiveLocationRecord? location,
  required TripRouteGeometryRecord? routeGeometry,
  required TripLiveMapFocusMode focusMode,
}) {
  final points = <LatLng>[];
  if (location != null && focusMode != TripLiveMapFocusMode.passengerOnly) {
    points.add(LatLng(location.latitude, location.longitude));
  }

  if (focusMode == TripLiveMapFocusMode.vehicleOnly) {
    return points;
  }

  if (routeGeometry != null) {
    if (routeGeometry.hasPolyline &&
        focusMode == TripLiveMapFocusMode.fullJourney) {
      points.addAll(decodeTripPolyline(routeGeometry.polyline));
    }
    for (final waypoint in visibleTripRouteWaypoints(
      routeGeometry,
      focusMode,
    )) {
      if (waypoint.hasCoordinates) {
        points.add(LatLng(waypoint.latitude!, waypoint.longitude!));
      }
    }
  }

  final unique = <String, LatLng>{};
  for (final point in points) {
    unique['${point.latitude}:${point.longitude}'] = point;
  }
  return unique.values.toList(growable: false);
}

List<LatLng> decodeTripPolyline(String encoded) {
  final result = <LatLng>[];
  if (encoded.trim().isEmpty) {
    return result;
  }

  int index = 0;
  int latitude = 0;
  int longitude = 0;

  while (index < encoded.length) {
    int shift = 0;
    int value = 0;

    while (true) {
      final byte = encoded.codeUnitAt(index++) - 63;
      value |= (byte & 0x1f) << shift;
      shift += 5;
      if (byte < 0x20) {
        break;
      }
    }

    final deltaLatitude = (value & 1) != 0 ? ~(value >> 1) : (value >> 1);
    latitude += deltaLatitude;

    shift = 0;
    value = 0;
    while (true) {
      final byte = encoded.codeUnitAt(index++) - 63;
      value |= (byte & 0x1f) << shift;
      shift += 5;
      if (byte < 0x20) {
        break;
      }
    }

    final deltaLongitude = (value & 1) != 0 ? ~(value >> 1) : (value >> 1);
    longitude += deltaLongitude;

    result.add(LatLng(latitude / 1e5, longitude / 1e5));
  }

  return result;
}

String? _routeSnapshotTimeLabel(TripRouteGeometryRecord? routeGeometry) {
  final calculadaEm = routeGeometry?.calculadaEm;
  if (routeGeometry?.snapshotOperacional != true || calculadaEm == null) {
    return null;
  }
  return _formatClockTime(calculadaEm);
}

String _formatClockTime(DateTime value) {
  final hour = value.hour.toString().padLeft(2, '0');
  final minute = value.minute.toString().padLeft(2, '0');
  return '$hour:$minute';
}
