import 'admin_motorista_record.dart';
import '../../tracking/domain/trip_route_geometry_record.dart';

class AdminViagemDetailRecord {
  const AdminViagemDetailRecord({
    required this.id,
    required this.rotaNome,
    required this.dataOperacao,
    required this.status,
    required this.motorista,
    required this.quantidadeParadas,
    required this.rotaVersao,
    required this.etaMinutosAteProximaParada,
    required this.paradas,
    required this.fontePlanejamento,
    required this.snapshotOperacional,
    this.rotaBaseId,
    this.servicoId,
    this.servicoNome,
    this.tipoOperacao,
    this.horarioPartida,
    this.etaAtualizadoEm,
    this.destinoFinal,
    this.proximaParada,
    this.rotaGeografica,
  });

  final int id;
  final int? rotaBaseId;
  final int? servicoId;
  final String rotaNome;
  final String? servicoNome;
  final String? tipoOperacao;
  final DateTime dataOperacao;
  final String status;
  final AdminMotoristaRecord motorista;
  final int quantidadeParadas;
  final String? horarioPartida;
  final int rotaVersao;
  final int etaMinutosAteProximaParada;
  final DateTime? etaAtualizadoEm;
  final AdminViagemDestinoFinalRecord? destinoFinal;
  final AdminViagemParadaRecord? proximaParada;
  final List<AdminViagemParadaRecord> paradas;
  final String fontePlanejamento;
  final bool snapshotOperacional;
  final TripRouteGeometryRecord? rotaGeografica;

  String get nomeOperacional {
    final serviceName = servicoNome;
    if (serviceName != null && serviceName.trim().isNotEmpty) {
      return serviceName;
    }
    return rotaNome;
  }

  bool get canChangeMotorista {
    final normalized = status.toUpperCase();
    return normalized.contains('PLANNED') || normalized.contains('PLANEJ');
  }

  bool get canCancel {
    final normalized = status.toUpperCase();
    return normalized.contains('PLANNED') || normalized.contains('PLANEJ');
  }

  bool get canSyncManifest {
    return servicoId != null && canCancel;
  }

  factory AdminViagemDetailRecord.fromJson(Map<String, dynamic> json) {
    final destino = json['destinoFinal'];
    final proxima = json['proximaParada'];
    final paradasJson = json['paradas'];
    final rotaGeograficaJson = json['rotaGeografica'];
    return AdminViagemDetailRecord(
      id: (json['id'] as num).toInt(),
      rotaBaseId: (json['rotaBaseId'] as num?)?.toInt(),
      servicoId: (json['servicoId'] as num?)?.toInt(),
      rotaNome: json['rotaNome'] as String? ?? '',
      servicoNome: json['servicoNome'] as String?,
      tipoOperacao: json['tipoOperacao'] as String?,
      dataOperacao:
          DateTime.tryParse(json['dataOperacao'] as String? ?? '') ??
          DateTime.now(),
      status: json['status'] as String? ?? 'PLANNED',
      motorista: AdminMotoristaRecord.fromJson(
        json['motorista'] as Map<String, dynamic>? ?? const {},
      ),
      quantidadeParadas: (json['quantidadeParadas'] as num?)?.toInt() ?? 0,
      horarioPartida: _parseHorario(json['horarioPartida']),
      rotaVersao: (json['rotaVersao'] as num?)?.toInt() ?? 1,
      etaMinutosAteProximaParada:
          (json['etaMinutosAteProximaParada'] as num?)?.toInt() ?? 0,
      etaAtualizadoEm: DateTime.tryParse(
        json['etaAtualizadoEm'] as String? ?? '',
      ),
      destinoFinal:
          destino is Map<String, dynamic>
              ? AdminViagemDestinoFinalRecord.fromJson(destino)
              : null,
      proximaParada:
          proxima is Map<String, dynamic>
              ? AdminViagemParadaRecord.fromJson(proxima)
              : null,
      paradas:
          paradasJson is List
              ? paradasJson
                  .map(
                    (item) => AdminViagemParadaRecord.fromJson(
                      item as Map<String, dynamic>,
                    ),
                  )
                  .toList(growable: false)
              : const [],
      fontePlanejamento: json['fontePlanejamento'] as String? ?? 'ROTA_BASE',
      snapshotOperacional: json['snapshotOperacional'] as bool? ?? false,
      rotaGeografica:
          rotaGeograficaJson is Map<String, dynamic>
              ? TripRouteGeometryRecord.fromJson(rotaGeograficaJson)
              : null,
    );
  }
}

class AdminViagemDestinoFinalRecord {
  const AdminViagemDestinoFinalRecord({
    this.nome,
    this.endereco,
    this.latitude,
    this.longitude,
  });

  final String? nome;
  final String? endereco;
  final double? latitude;
  final double? longitude;

  bool get hasContent {
    return (nome != null && nome!.trim().isNotEmpty) ||
        (endereco != null && endereco!.trim().isNotEmpty) ||
        (latitude != null && longitude != null);
  }

  String get label {
    if (nome != null && nome!.trim().isNotEmpty) return nome!;
    if (endereco != null && endereco!.trim().isNotEmpty) return endereco!;
    return 'Destino final não publicado';
  }

  factory AdminViagemDestinoFinalRecord.fromJson(Map<String, dynamic> json) {
    return AdminViagemDestinoFinalRecord(
      nome: json['nome'] as String?,
      endereco: json['endereco'] as String?,
      latitude: (json['latitude'] as num?)?.toDouble(),
      longitude: (json['longitude'] as num?)?.toDouble(),
    );
  }
}

class AdminViagemParadaRecord {
  const AdminViagemParadaRecord({
    required this.id,
    required this.ordem,
    required this.status,
    required this.confirmadoPresenca,
    required this.presencaId,
    required this.presencaStatus,
    required this.proximaParada,
    required this.passageiro,
    this.etaMinutos,
  });

  final int id;
  final int ordem;
  final String status;
  final int? etaMinutos;
  final bool confirmadoPresenca;
  final int? presencaId;
  final String presencaStatus;
  final bool proximaParada;
  final AdminViagemPassageiroRecord passageiro;

  bool get isAusente {
    final normalized = presencaStatus.toUpperCase();
    return normalized.contains('ABSENT') || normalized.contains('CANCELED');
  }

  factory AdminViagemParadaRecord.fromJson(Map<String, dynamic> json) {
    return AdminViagemParadaRecord(
      id: (json['id'] as num).toInt(),
      ordem: (json['ordem'] as num?)?.toInt() ?? 0,
      status: json['status'] as String? ?? 'PENDING',
      etaMinutos: (json['etaMinutos'] as num?)?.toInt(),
      confirmadoPresenca: json['confirmadoPresenca'] as bool? ?? false,
      presencaId: (json['presencaId'] as num?)?.toInt(),
      presencaStatus: json['presencaStatus'] as String? ?? 'UNKNOWN',
      proximaParada: json['proximaParada'] as bool? ?? false,
      passageiro: AdminViagemPassageiroRecord.fromJson(
        json['passageiro'] as Map<String, dynamic>? ?? const {},
      ),
    );
  }
}

class AdminViagemPassageiroRecord {
  const AdminViagemPassageiroRecord({
    required this.id,
    required this.nome,
    required this.menorDeIdade,
  });

  final int id;
  final String nome;
  final bool menorDeIdade;

  factory AdminViagemPassageiroRecord.fromJson(Map<String, dynamic> json) {
    return AdminViagemPassageiroRecord(
      id: (json['id'] as num?)?.toInt() ?? 0,
      nome: json['nome'] as String? ?? '',
      menorDeIdade: json['menorDeIdade'] as bool? ?? false,
    );
  }
}

String? _parseHorario(dynamic value) {
  final raw = value as String?;
  if (raw == null || raw.isEmpty) return null;
  return raw.length >= 5 ? raw.substring(0, 5) : raw;
}
