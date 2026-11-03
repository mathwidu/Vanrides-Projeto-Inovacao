import '../../tracking/domain/trip_tracking_record.dart';
import '../../tracking/domain/trip_route_geometry_record.dart';
import '../../tracking/domain/trip_route_event.dart';
import '../../tracking/domain/trip_tracking_state_record.dart';
import '../../navigation/data/driver_navigation_route_plan_contract.dart';

class DriverTripStopRecord {
  const DriverTripStopRecord({
    required this.id,
    required this.ordem,
    required this.status,
    required this.etaMinutos,
    required this.confirmadoPresenca,
    required this.proximaParada,
    required this.passageiroId,
    required this.passageiroNome,
    required this.menorDeIdade,
  });

  final int id;
  final int ordem;
  final TripTrackingStopStatus status;
  final int? etaMinutos;
  final bool confirmadoPresenca;
  final bool proximaParada;
  final int passageiroId;
  final String passageiroNome;
  final bool menorDeIdade;

  factory DriverTripStopRecord.fromJson(Map<String, dynamic> json) {
    final passageiro = json['passageiro'] as Map<String, dynamic>? ?? const {};
    return DriverTripStopRecord(
      id: (json['id'] as num?)?.toInt() ?? 0,
      ordem: (json['ordem'] as num?)?.toInt() ?? 0,
      status: _stopStatusFromBackend(json['status'] as String?),
      etaMinutos: (json['etaMinutos'] as num?)?.toInt(),
      confirmadoPresenca: json['confirmadoPresenca'] as bool? ?? false,
      proximaParada: json['proximaParada'] as bool? ?? false,
      passageiroId: (passageiro['id'] as num?)?.toInt() ?? 0,
      passageiroNome: (passageiro['nome'] as String? ?? '').trim(),
      menorDeIdade: passageiro['menorDeIdade'] as bool? ?? false,
    );
  }
}

class DriverTripRecord {
  const DriverTripRecord({
    required this.id,
    required this.rotaBaseId,
    required this.rotaNome,
    required this.dataOperacao,
    required this.status,
    required this.rotaVersao,
    required this.etaMinutosAteProximaParada,
    required this.etaAtualizadoEm,
    required this.motorista,
    required this.proximaParada,
    required this.paradas,
    required this.rotaGeografica,
    this.navigationRoutePlan,
    this.trackingState = TripTrackingStateRecord.unavailable,
    this.horarioPartida,
    this.servicoId,
    this.servicoNome,
    this.tipoOperacao,
  });

  final int id;
  final int rotaBaseId;
  final String rotaNome;
  final int? servicoId;
  final String? servicoNome;
  final String? tipoOperacao;
  final DateTime dataOperacao;
  final TripTrackingStatus status;
  final int rotaVersao;
  final int? etaMinutosAteProximaParada;
  final DateTime? etaAtualizadoEm;
  final TripTrackingDriverRecord? motorista;
  final DriverTripStopRecord? proximaParada;
  final List<DriverTripStopRecord> paradas;
  final TripRouteGeometryRecord? rotaGeografica;
  final DriverNavigationRoutePlanResponse? navigationRoutePlan;
  final TripTrackingStateRecord trackingState;
  final String? horarioPartida;

  int get totalParadas => paradas.length;
  int get confirmadosCount =>
      paradas.where((item) => item.confirmadoPresenca).length;
  int get pendentesCount =>
      paradas.where((item) => !item.confirmadoPresenca).length;
  String get nomeOperacional {
    final serviceName = servicoNome;
    if (serviceName != null && serviceName.trim().isNotEmpty) {
      return serviceName;
    }
    return rotaNome;
  }

  String get tipoOperacaoLabel => switch ((tipoOperacao ?? '').toUpperCase()) {
    'VOLTA' => 'Volta',
    'CIRCULAR' => 'Circular',
    'FRETADO' => 'Fretado',
    'IDA' => 'Ida',
    _ => '',
  };

  factory DriverTripRecord.fromJson(Map<String, dynamic> json) {
    final paradasJson = json['paradas'] as List<dynamic>? ?? const [];
    return DriverTripRecord(
      id: (json['id'] as num?)?.toInt() ?? 0,
      rotaBaseId: (json['rotaBaseId'] as num?)?.toInt() ?? 0,
      rotaNome: (json['rotaNome'] as String? ?? '').trim(),
      servicoId: (json['servicoId'] as num?)?.toInt(),
      servicoNome: (json['servicoNome'] as String?)?.trim(),
      tipoOperacao: (json['tipoOperacao'] as String?)?.trim(),
      dataOperacao:
          DateTime.tryParse(json['dataOperacao'] as String? ?? '') ??
          DateTime.now(),
      status: _statusFromBackend(json['status'] as String?),
      rotaVersao: (json['rotaVersao'] as num?)?.toInt() ?? 1,
      etaMinutosAteProximaParada:
          (json['etaMinutosAteProximaParada'] as num?)?.toInt(),
      etaAtualizadoEm: DateTime.tryParse(
        json['etaAtualizadoEm'] as String? ?? '',
      ),
      motorista:
          json['motorista'] is Map<String, dynamic>
              ? TripTrackingDriverRecord.fromJson(
                json['motorista'] as Map<String, dynamic>,
              )
              : null,
      proximaParada:
          json['proximaParada'] is Map<String, dynamic>
              ? DriverTripStopRecord.fromJson(
                json['proximaParada'] as Map<String, dynamic>,
              )
              : null,
      paradas: paradasJson
          .whereType<Map<String, dynamic>>()
          .map(DriverTripStopRecord.fromJson)
          .toList(growable: false),
      rotaGeografica:
          json['rotaGeografica'] is Map<String, dynamic>
              ? TripRouteGeometryRecord.fromJson(
                json['rotaGeografica'] as Map<String, dynamic>,
              )
              : null,
      navigationRoutePlan:
          json['navigationRoutePlan'] is Map<String, dynamic>
              ? DriverNavigationRoutePlanResponse.fromJson(
                json['navigationRoutePlan'] as Map<String, dynamic>,
              )
              : null,
      trackingState: parseTripTrackingState(json['trackingState']),
      horarioPartida: _parseHorario(json['horarioPartida']),
    );
  }

  DriverTripRecord copyWithRealtime(TripRouteEvent event) {
    return DriverTripRecord(
      id: id,
      rotaBaseId: rotaBaseId,
      rotaNome: rotaNome,
      servicoId: servicoId,
      servicoNome: servicoNome,
      tipoOperacao: tipoOperacao,
      dataOperacao: dataOperacao,
      status:
          event.status == TripTrackingStatus.unknown ? status : event.status,
      rotaVersao: event.rotaVersao > 0 ? event.rotaVersao : rotaVersao,
      etaMinutosAteProximaParada:
          event.etaMinutosAteProximaParada ?? etaMinutosAteProximaParada,
      etaAtualizadoEm: event.etaAtualizadoEm ?? etaAtualizadoEm,
      motorista: motorista,
      proximaParada: proximaParada,
      paradas: paradas,
      rotaGeografica: rotaGeografica,
      navigationRoutePlan: navigationRoutePlan,
      trackingState: event.trackingState ?? trackingState,
      horarioPartida: horarioPartida,
    );
  }

  TripTrackingRecord toTrackingRecord() {
    final highlightedStop =
        proximaParada ?? (paradas.isNotEmpty ? paradas.first : null);
    return TripTrackingRecord(
      id: id,
      rotaNome: rotaNome,
      servicoId: servicoId,
      servicoNome: servicoNome,
      tipoOperacao: tipoOperacao,
      dataOperacao: dataOperacao,
      status: status,
      rotaVersao: rotaVersao,
      etaMinutosAtePassageiro:
          highlightedStop?.etaMinutos ?? etaMinutosAteProximaParada,
      etaAtualizadoEm: etaAtualizadoEm,
      motorista: motorista,
      paradaDoPassageiro:
          highlightedStop == null
              ? null
              : TripTrackingPassengerStopRecord(
                id: highlightedStop.id,
                ordem: highlightedStop.ordem,
                status: highlightedStop.status,
                etaMinutos: highlightedStop.etaMinutos,
                confirmadoPresenca: highlightedStop.confirmadoPresenca,
                proximaParada: highlightedStop.proximaParada,
                passageiroId: highlightedStop.passageiroId,
                passageiroNome: highlightedStop.passageiroNome,
                menorDeIdade: highlightedStop.menorDeIdade,
              ),
      rotaGeografica: rotaGeografica,
      trackingState: trackingState,
      horarioPartida: horarioPartida,
    );
  }
}

TripTrackingStatus _statusFromBackend(String? value) {
  switch ((value ?? '').trim().toUpperCase()) {
    case 'PLANNED':
      return TripTrackingStatus.planned;
    case 'IN_PROGRESS':
      return TripTrackingStatus.inProgress;
    case 'COMPLETED':
      return TripTrackingStatus.completed;
    case 'CANCELED':
      return TripTrackingStatus.canceled;
    default:
      return TripTrackingStatus.unknown;
  }
}

TripTrackingStopStatus _stopStatusFromBackend(String? value) {
  switch ((value ?? '').trim().toUpperCase()) {
    case 'PENDING':
      return TripTrackingStopStatus.pending;
    case 'COMPLETED':
      return TripTrackingStopStatus.completed;
    case 'SKIPPED':
      return TripTrackingStopStatus.skipped;
    case 'NO_SHOW':
      return TripTrackingStopStatus.noShow;
    default:
      return TripTrackingStopStatus.unknown;
  }
}

String? _parseHorario(dynamic value) {
  final raw = value as String?;
  if (raw == null || raw.isEmpty) return null;
  return raw.length >= 5 ? raw.substring(0, 5) : raw;
}
