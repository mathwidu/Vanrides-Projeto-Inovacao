import 'admin_motorista_record.dart';
import 'admin_operation_days.dart';

class AdminServicoRecorrenteRecord {
  const AdminServicoRecorrenteRecord({
    required this.id,
    required this.nome,
    required this.tipoOperacao,
    required this.quantidadePassageiros,
    required this.passageiroIds,
    required this.ativo,
    required this.geracaoAutomaticaAtiva,
    required this.politicaPresenca,
    required this.diasOperacao,
    this.rotaBaseLegadaId,
    this.motoristaPadrao,
    this.horarioPartida,
    this.destinoFinalNome,
    this.destinoFinalEndereco,
    this.destinoFinalLatitude,
    this.destinoFinalLongitude,
    this.capacidadePlanejada,
  });

  final int id;
  final int? rotaBaseLegadaId;
  final String nome;
  final String tipoOperacao;
  final AdminMotoristaRecord? motoristaPadrao;
  final int quantidadePassageiros;
  final List<int> passageiroIds;
  final bool ativo;
  final bool geracaoAutomaticaAtiva;
  final String politicaPresenca;
  final List<String> diasOperacao;
  final String? horarioPartida;
  final String? destinoFinalNome;
  final String? destinoFinalEndereco;
  final double? destinoFinalLatitude;
  final double? destinoFinalLongitude;
  final int? capacidadePlanejada;

  bool get hasMotoristaPadrao => motoristaPadrao != null;

  String get tipoOperacaoLabel => switch (tipoOperacao.toUpperCase()) {
    'VOLTA' => 'Volta',
    'CIRCULAR' => 'Circular',
    'FRETADO' => 'Fretado',
    _ => 'Ida',
  };

  factory AdminServicoRecorrenteRecord.fromJson(Map<String, dynamic> json) {
    final motorista = json['motoristaPadrao'];
    return AdminServicoRecorrenteRecord(
      id: (json['id'] as num).toInt(),
      rotaBaseLegadaId: (json['rotaBaseLegadaId'] as num?)?.toInt(),
      nome: json['nome'] as String? ?? '',
      tipoOperacao: json['tipoOperacao'] as String? ?? 'IDA',
      motoristaPadrao:
          motorista is Map<String, dynamic>
              ? AdminMotoristaRecord.fromJson(motorista)
              : null,
      quantidadePassageiros:
          (json['quantidadePassageiros'] as num?)?.toInt() ?? 0,
      passageiroIds: (json['passageiroIds'] as List<dynamic>? ?? const [])
          .map((item) => (item as num).toInt())
          .toList(growable: false),
      ativo: json['ativo'] as bool? ?? false,
      geracaoAutomaticaAtiva: json['geracaoAutomaticaAtiva'] as bool? ?? false,
      politicaPresenca:
          json['politicaPresenca'] as String? ?? 'INCLUIR_POR_PADRAO',
      diasOperacao: decodeAdminOperationDays(
        json['diasOperacao'] as List<dynamic>? ?? const [],
      ),
      horarioPartida: _parseHorario(json['horarioPartida']),
      destinoFinalNome: json['destinoFinalNome'] as String?,
      destinoFinalEndereco: json['destinoFinalEndereco'] as String?,
      destinoFinalLatitude: (json['destinoFinalLatitude'] as num?)?.toDouble(),
      destinoFinalLongitude:
          (json['destinoFinalLongitude'] as num?)?.toDouble(),
      capacidadePlanejada: (json['capacidadePlanejada'] as num?)?.toInt(),
    );
  }
}

String? _parseHorario(dynamic value) {
  final raw = value as String?;
  if (raw == null || raw.isEmpty) return null;
  return raw.length >= 5 ? raw.substring(0, 5) : raw;
}
