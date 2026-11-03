import 'trip_route_geometry_record.dart';
import 'trip_tracking_state_record.dart';

enum TripTrackingStatus { planned, inProgress, completed, canceled, unknown }

enum TripTrackingStopStatus { pending, completed, skipped, noShow, unknown }

class TripTrackingDriverRecord {
  const TripTrackingDriverRecord({
    required this.id,
    required this.nome,
    this.email,
    this.telefone,
  });

  final int id;
  final String nome;
  final String? email;
  final String? telefone;

  factory TripTrackingDriverRecord.fromJson(Map<String, dynamic> json) {
    return TripTrackingDriverRecord(
      id: (json['id'] as num).toInt(),
      nome: (json['nome'] as String? ?? '').trim(),
      email: (json['email'] as String?)?.trim(),
      telefone: (json['telefone'] as String?)?.trim(),
    );
  }
}

class TripTrackingPassengerStopRecord {
  const TripTrackingPassengerStopRecord({
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

  factory TripTrackingPassengerStopRecord.fromJson(Map<String, dynamic> json) {
    final passageiro = json['passageiro'] as Map<String, dynamic>? ?? const {};
    return TripTrackingPassengerStopRecord(
      id: (json['id'] as num).toInt(),
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

class TripTrackingRecord {
  const TripTrackingRecord({
    required this.id,
    required this.rotaNome,
    required this.dataOperacao,
    required this.status,
    required this.rotaVersao,
    required this.etaMinutosAtePassageiro,
    required this.etaAtualizadoEm,
    required this.motorista,
    required this.paradaDoPassageiro,
    required this.rotaGeografica,
    this.trackingState = TripTrackingStateRecord.unavailable,
    this.horarioPartida,
    this.servicoId,
    this.servicoNome,
    this.tipoOperacao,
  });

  final int id;
  final String rotaNome;
  final int? servicoId;
  final String? servicoNome;
  final String? tipoOperacao;
  final DateTime dataOperacao;
  final TripTrackingStatus status;
  final int rotaVersao;
  final int? etaMinutosAtePassageiro;
  final DateTime? etaAtualizadoEm;
  final TripTrackingDriverRecord? motorista;
  final TripTrackingPassengerStopRecord? paradaDoPassageiro;
  final TripRouteGeometryRecord? rotaGeografica;
  final TripTrackingStateRecord trackingState;
  final String? horarioPartida;

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

  factory TripTrackingRecord.fromJson(Map<String, dynamic> json) {
    return TripTrackingRecord(
      id: (json['id'] as num).toInt(),
      rotaNome: (json['rotaNome'] as String? ?? '').trim(),
      servicoId: (json['servicoId'] as num?)?.toInt(),
      servicoNome: (json['servicoNome'] as String?)?.trim(),
      tipoOperacao: (json['tipoOperacao'] as String?)?.trim(),
      dataOperacao:
          DateTime.tryParse(json['dataOperacao'] as String? ?? '') ??
          DateTime.now(),
      status: _statusFromBackend(json['status'] as String?),
      rotaVersao: (json['rotaVersao'] as num?)?.toInt() ?? 1,
      etaMinutosAtePassageiro:
          (json['etaMinutosAtePassageiro'] as num?)?.toInt(),
      etaAtualizadoEm: DateTime.tryParse(
        json['etaAtualizadoEm'] as String? ?? '',
      ),
      motorista:
          json['motorista'] is Map<String, dynamic>
              ? TripTrackingDriverRecord.fromJson(
                json['motorista'] as Map<String, dynamic>,
              )
              : null,
      paradaDoPassageiro:
          json['paradaDoPassageiro'] is Map<String, dynamic>
              ? TripTrackingPassengerStopRecord.fromJson(
                json['paradaDoPassageiro'] as Map<String, dynamic>,
              )
              : null,
      rotaGeografica:
          json['rotaGeografica'] is Map<String, dynamic>
              ? TripRouteGeometryRecord.fromJson(
                json['rotaGeografica'] as Map<String, dynamic>,
              )
              : null,
      trackingState: parseTripTrackingState(json['trackingState']),
      horarioPartida: _parseHorario(json['horarioPartida']),
    );
  }
}

String? _parseHorario(dynamic value) {
  final raw = value as String?;
  if (raw == null || raw.isEmpty) return null;
  return raw.length >= 5 ? raw.substring(0, 5) : raw;
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
