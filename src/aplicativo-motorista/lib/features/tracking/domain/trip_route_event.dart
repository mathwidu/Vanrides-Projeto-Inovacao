import 'trip_tracking_record.dart';
import 'trip_tracking_state_record.dart';

class TripRouteEvent {
  const TripRouteEvent({
    required this.tipo,
    required this.viagemId,
    required this.motoristaId,
    required this.dataOperacao,
    required this.status,
    required this.rotaVersao,
    required this.totalParadas,
    required this.paradasPendentes,
    required this.paradasConcluidas,
    required this.proximaParadaId,
    required this.proximaParadaOrdem,
    required this.proximoPassageiroId,
    required this.proximoPassageiroNome,
    required this.proximaParadaStatus,
    required this.etaMinutosAteProximaParada,
    required this.etaAtualizadoEm,
    required this.latitudeMotorista,
    required this.longitudeMotorista,
    required this.localizacaoAtualizadaEm,
    required this.atualizadoEm,
    this.avisosOperacionais = const [],
    this.trackingState,
  });

  final String tipo;
  final int viagemId;
  final int motoristaId;
  final DateTime dataOperacao;
  final TripTrackingStatus status;
  final int rotaVersao;
  final int? totalParadas;
  final int? paradasPendentes;
  final int? paradasConcluidas;
  final int? proximaParadaId;
  final int? proximaParadaOrdem;
  final int? proximoPassageiroId;
  final String? proximoPassageiroNome;
  final TripTrackingStopStatus proximaParadaStatus;
  final int? etaMinutosAteProximaParada;
  final DateTime? etaAtualizadoEm;
  final double? latitudeMotorista;
  final double? longitudeMotorista;
  final DateTime? localizacaoAtualizadaEm;
  final DateTime? atualizadoEm;
  final List<TripOperationalAlertRecord> avisosOperacionais;
  final TripTrackingStateRecord? trackingState;

  bool get isRouteUpdated => tipo.trim().toUpperCase() == 'ROTA_ATUALIZADA';
  bool get hasLiveLocation =>
      latitudeMotorista != null && longitudeMotorista != null;

  factory TripRouteEvent.fromJson(Map<String, dynamic> json) {
    return TripRouteEvent(
      tipo: (json['tipo'] as String? ?? '').trim(),
      viagemId: (json['viagemId'] as num?)?.toInt() ?? 0,
      motoristaId: (json['motoristaId'] as num?)?.toInt() ?? 0,
      dataOperacao:
          DateTime.tryParse(json['dataOperacao'] as String? ?? '') ??
          DateTime.now(),
      status: _statusFromBackend(json['status'] as String?),
      rotaVersao: (json['rotaVersao'] as num?)?.toInt() ?? 0,
      totalParadas: (json['totalParadas'] as num?)?.toInt(),
      paradasPendentes: (json['paradasPendentes'] as num?)?.toInt(),
      paradasConcluidas: (json['paradasConcluidas'] as num?)?.toInt(),
      proximaParadaId: (json['proximaParadaId'] as num?)?.toInt(),
      proximaParadaOrdem: (json['proximaParadaOrdem'] as num?)?.toInt(),
      proximoPassageiroId: (json['proximoPassageiroId'] as num?)?.toInt(),
      proximoPassageiroNome: (json['proximoPassageiroNome'] as String?)?.trim(),
      proximaParadaStatus: _stopStatusFromBackend(
        json['proximaParadaStatus'] as String?,
      ),
      etaMinutosAteProximaParada:
          (json['etaMinutosAteProximaParada'] as num?)?.toInt(),
      etaAtualizadoEm: DateTime.tryParse(
        json['etaAtualizadoEm'] as String? ?? '',
      ),
      latitudeMotorista: (json['latitudeMotorista'] as num?)?.toDouble(),
      longitudeMotorista: (json['longitudeMotorista'] as num?)?.toDouble(),
      localizacaoAtualizadaEm: DateTime.tryParse(
        json['localizacaoAtualizadaEm'] as String? ?? '',
      ),
      atualizadoEm: DateTime.tryParse(json['atualizadoEm'] as String? ?? ''),
      avisosOperacionais: parseTripOperationalAlerts(
        json['avisosOperacionais'] ?? json['operationalAlerts'],
      ),
      trackingState: parseOptionalTripTrackingState(json['trackingState']),
    );
  }
}

enum TripOperationalAlertType {
  nextPassenger,
  driverApproaching,
  stopCompleted,
  unknown,
}

enum TripOperationalAlertSeverity { info, success, warning, urgent, unknown }

class TripOperationalAlertRecord {
  const TripOperationalAlertRecord({
    required this.tipo,
    required this.severidade,
    required this.titulo,
    required this.mensagem,
    this.passageiroId,
    this.paradaId,
    this.paradaOrdem,
  });

  final TripOperationalAlertType tipo;
  final TripOperationalAlertSeverity severidade;
  final String titulo;
  final String mensagem;
  final int? passageiroId;
  final int? paradaId;
  final int? paradaOrdem;

  factory TripOperationalAlertRecord.fromJson(Map<String, dynamic> json) {
    return TripOperationalAlertRecord(
      tipo: _alertTypeFromBackend(json['tipo'] as String?),
      severidade: _alertSeverityFromBackend(json['severidade'] as String?),
      titulo: (json['titulo'] as String? ?? '').trim(),
      mensagem: (json['mensagem'] as String? ?? '').trim(),
      passageiroId: (json['passageiroId'] as num?)?.toInt(),
      paradaId: (json['paradaId'] as num?)?.toInt(),
      paradaOrdem: (json['paradaOrdem'] as num?)?.toInt(),
    );
  }

  bool isTargetedToPassenger(int passengerId) {
    return passageiroId == passengerId;
  }
}

List<TripOperationalAlertRecord> parseTripOperationalAlerts(Object? rawValue) {
  if (rawValue is! List) {
    return const [];
  }
  return rawValue
      .whereType<Map>()
      .map(
        (raw) => TripOperationalAlertRecord.fromJson(
          raw.map((key, value) => MapEntry('$key', value)),
        ),
      )
      .where((alert) => alert.titulo.isNotEmpty || alert.mensagem.isNotEmpty)
      .toList(growable: false);
}

TripOperationalAlertRecord? selectPassengerOperationalAlert(
  TripRouteEvent event,
  int passengerId,
) {
  for (final type in const [
    TripOperationalAlertType.driverApproaching,
    TripOperationalAlertType.nextPassenger,
  ]) {
    for (final alert in event.avisosOperacionais) {
      if (alert.tipo == type && alert.isTargetedToPassenger(passengerId)) {
        return alert;
      }
    }
  }

  for (final alert in event.avisosOperacionais) {
    if (alert.tipo == TripOperationalAlertType.stopCompleted) {
      return alert;
    }
  }

  if (event.proximoPassageiroId == passengerId) {
    return const TripOperationalAlertRecord(
      tipo: TripOperationalAlertType.nextPassenger,
      severidade: TripOperationalAlertSeverity.info,
      titulo: 'Você é o próximo',
      mensagem: 'A van está seguindo para a sua parada.',
    );
  }

  return null;
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

TripOperationalAlertType _alertTypeFromBackend(String? value) {
  switch ((value ?? '').trim().toUpperCase()) {
    case 'NEXT_PASSENGER':
      return TripOperationalAlertType.nextPassenger;
    case 'DRIVER_APPROACHING':
      return TripOperationalAlertType.driverApproaching;
    case 'STOP_COMPLETED':
      return TripOperationalAlertType.stopCompleted;
    default:
      return TripOperationalAlertType.unknown;
  }
}

TripOperationalAlertSeverity _alertSeverityFromBackend(String? value) {
  switch ((value ?? '').trim().toUpperCase()) {
    case 'INFO':
      return TripOperationalAlertSeverity.info;
    case 'SUCCESS':
      return TripOperationalAlertSeverity.success;
    case 'WARNING':
      return TripOperationalAlertSeverity.warning;
    case 'URGENT':
      return TripOperationalAlertSeverity.urgent;
    default:
      return TripOperationalAlertSeverity.unknown;
  }
}
