import 'trip_tracking_state_record.dart';

class TripNavigationRecord {
  const TripNavigationRecord({
    required this.provider,
    required this.url,
    required this.origem,
    required this.destino,
    required this.quantidadeWaypoints,
  });

  final String provider;
  final String url;
  final String origem;
  final String destino;
  final int quantidadeWaypoints;

  bool get hasUrl => url.trim().isNotEmpty;

  factory TripNavigationRecord.fromJson(Map<String, dynamic> json) {
    return TripNavigationRecord(
      provider: (json['provider'] as String? ?? '').trim(),
      url: (json['url'] as String? ?? '').trim(),
      origem: (json['origem'] as String? ?? '').trim(),
      destino: (json['destino'] as String? ?? '').trim(),
      quantidadeWaypoints: (json['quantidadeWaypoints'] as num?)?.toInt() ?? 0,
    );
  }
}

class TripRouteWaypointRecord {
  const TripRouteWaypointRecord({
    required this.tipo,
    required this.paradaId,
    required this.passageiroId,
    required this.nome,
    required this.latitude,
    required this.longitude,
    required this.ordem,
    required this.proximaParada,
    required this.paradaDoPassageiro,
  });

  final String tipo;
  final int? paradaId;
  final int? passageiroId;
  final String nome;
  final double? latitude;
  final double? longitude;
  final int? ordem;
  final bool proximaParada;
  final bool paradaDoPassageiro;

  bool get hasCoordinates => latitude != null && longitude != null;

  factory TripRouteWaypointRecord.fromJson(Map<String, dynamic> json) {
    return TripRouteWaypointRecord(
      tipo: (json['tipo'] as String? ?? '').trim(),
      paradaId: (json['paradaId'] as num?)?.toInt(),
      passageiroId: (json['passageiroId'] as num?)?.toInt(),
      nome: (json['nome'] as String? ?? '').trim(),
      latitude: (json['latitude'] as num?)?.toDouble(),
      longitude: (json['longitude'] as num?)?.toDouble(),
      ordem: (json['ordem'] as num?)?.toInt(),
      proximaParada: json['proximaParada'] as bool? ?? false,
      paradaDoPassageiro: json['paradaDoPassageiro'] as bool? ?? false,
    );
  }
}

class TripRouteLegRecord {
  const TripRouteLegRecord({
    required this.indice,
    required this.origemLabel,
    required this.destinoLabel,
    required this.distanciaMetros,
    required this.duracaoSegundos,
  });

  final int indice;
  final String origemLabel;
  final String destinoLabel;
  final int? distanciaMetros;
  final int? duracaoSegundos;

  factory TripRouteLegRecord.fromJson(Map<String, dynamic> json) {
    return TripRouteLegRecord(
      indice: (json['indice'] as num?)?.toInt() ?? 0,
      origemLabel: (json['origemLabel'] as String? ?? '').trim(),
      destinoLabel: (json['destinoLabel'] as String? ?? '').trim(),
      distanciaMetros: (json['distanciaMetros'] as num?)?.toInt(),
      duracaoSegundos: (json['duracaoSegundos'] as num?)?.toInt(),
    );
  }
}

class TripRouteGeometryRecord {
  const TripRouteGeometryRecord({
    required this.provider,
    required this.polyline,
    required this.navegacao,
    this.navegacaoRestante,
    required this.waypoints,
    required this.legs,
    required this.filaTotalParadas,
    required this.posicaoPassageiroNaFila,
    required this.paradasAntesDoPassageiro,
    this.calculadaEm,
    this.snapshotOperacional = false,
    this.snapshotKey,
    this.routeInputHash,
    this.routeSnapshotStatus = TripRouteSnapshotStatus.unavailable,
    this.routeRecomputeReason = TripRouteRecomputeReason.none,
    this.routeRecomputeAllowedAfter,
  });

  final String provider;
  final String polyline;
  final TripNavigationRecord? navegacao;
  final TripNavigationRecord? navegacaoRestante;
  final List<TripRouteWaypointRecord> waypoints;
  final List<TripRouteLegRecord> legs;
  final int? filaTotalParadas;
  final int? posicaoPassageiroNaFila;
  final int? paradasAntesDoPassageiro;
  final DateTime? calculadaEm;
  final bool snapshotOperacional;
  final String? snapshotKey;
  final String? routeInputHash;
  final TripRouteSnapshotStatus routeSnapshotStatus;
  final TripRouteRecomputeReason routeRecomputeReason;
  final DateTime? routeRecomputeAllowedAfter;

  bool get hasPolyline => polyline.trim().isNotEmpty;
  bool get hasWaypoints => waypoints.any((item) => item.hasCoordinates);
  bool get isDrawable => hasPolyline || hasWaypoints;
  TripNavigationRecord? get navegacaoPreferencial =>
      navegacaoRestante?.hasUrl == true ? navegacaoRestante : navegacao;

  List<TripRouteWaypointRecord> get orderedStops {
    final stops = waypoints
        .where((item) => item.ordem != null)
        .toList(growable: true);
    stops.sort((a, b) => (a.ordem ?? 0).compareTo(b.ordem ?? 0));
    return List<TripRouteWaypointRecord>.unmodifiable(stops);
  }

  TripRouteWaypointRecord? get passengerStop {
    for (final waypoint in waypoints) {
      if (waypoint.paradaDoPassageiro) {
        return waypoint;
      }
    }
    return null;
  }

  TripRouteWaypointRecord? get nextStop {
    for (final waypoint in waypoints) {
      if (waypoint.proximaParada) {
        return waypoint;
      }
    }
    return null;
  }

  factory TripRouteGeometryRecord.fromJson(Map<String, dynamic> json) {
    final waypointsJson = json['waypoints'] as List<dynamic>? ?? const [];
    final legsJson = json['legs'] as List<dynamic>? ?? const [];

    return TripRouteGeometryRecord(
      provider: (json['provider'] as String? ?? '').trim(),
      polyline: (json['polyline'] as String? ?? '').trim(),
      navegacao:
          json['navegacao'] is Map<String, dynamic>
              ? TripNavigationRecord.fromJson(
                json['navegacao'] as Map<String, dynamic>,
              )
              : null,
      navegacaoRestante:
          json['navegacaoRestante'] is Map<String, dynamic>
              ? TripNavigationRecord.fromJson(
                json['navegacaoRestante'] as Map<String, dynamic>,
              )
              : null,
      waypoints: waypointsJson
          .whereType<Map<String, dynamic>>()
          .map(TripRouteWaypointRecord.fromJson)
          .toList(growable: false),
      legs: legsJson
          .whereType<Map<String, dynamic>>()
          .map(TripRouteLegRecord.fromJson)
          .toList(growable: false),
      filaTotalParadas: (json['filaTotalParadas'] as num?)?.toInt(),
      posicaoPassageiroNaFila:
          (json['posicaoPassageiroNaFila'] as num?)?.toInt(),
      paradasAntesDoPassageiro:
          (json['paradasAntesDoPassageiro'] as num?)?.toInt(),
      calculadaEm: _parseOptionalDateTime(json['calculadaEm']),
      snapshotOperacional: json['snapshotOperacional'] as bool? ?? false,
      snapshotKey: (json['snapshotKey'] as String?)?.trim(),
      routeInputHash: (json['routeInputHash'] as String?)?.trim(),
      routeSnapshotStatus: parseTripRouteSnapshotStatus(
        json['routeSnapshotStatus'] as String?,
      ),
      routeRecomputeReason: parseTripRouteRecomputeReason(
        json['routeRecomputeReason'] as String?,
      ),
      routeRecomputeAllowedAfter: _parseOptionalDateTime(
        json['routeRecomputeAllowedAfter'],
      ),
    );
  }
}

DateTime? _parseOptionalDateTime(Object? rawValue) {
  if (rawValue is! String || rawValue.trim().isEmpty) {
    return null;
  }
  return DateTime.tryParse(rawValue.trim());
}
