import 'admin_operation_days.dart';

class AdminServicoRecorrenteInput {
  const AdminServicoRecorrenteInput({
    required this.nome,
    required this.tipoOperacao,
    required this.politicaPresenca,
    required this.geracaoAutomaticaAtiva,
    required this.diasOperacao,
    required this.passageiroIds,
    this.motoristaPadraoId,
    this.horarioPartida,
    this.destinoFinalNome,
    this.destinoFinalEndereco,
    this.destinoFinalLatitude,
    this.destinoFinalLongitude,
    this.capacidadePlanejada,
  });

  final String nome;
  final int? motoristaPadraoId;
  final String tipoOperacao;
  final String politicaPresenca;
  final bool geracaoAutomaticaAtiva;
  final List<String> diasOperacao;
  final String? horarioPartida;
  final String? destinoFinalNome;
  final String? destinoFinalEndereco;
  final double? destinoFinalLatitude;
  final double? destinoFinalLongitude;
  final int? capacidadePlanejada;
  final List<int> passageiroIds;

  Map<String, dynamic> toJson() {
    return {
      'nome': nome.trim(),
      if (motoristaPadraoId != null) 'motoristaPadraoId': motoristaPadraoId,
      'tipoOperacao': tipoOperacao,
      'politicaPresenca': politicaPresenca,
      'geracaoAutomaticaAtiva': geracaoAutomaticaAtiva,
      'diasOperacao': encodeAdminOperationDays(diasOperacao),
      if (horarioPartida != null && horarioPartida!.trim().isNotEmpty)
        'horarioPartida': horarioPartida!.trim(),
      if (destinoFinalNome != null && destinoFinalNome!.trim().isNotEmpty)
        'destinoFinalNome': destinoFinalNome!.trim(),
      if (destinoFinalEndereco != null &&
          destinoFinalEndereco!.trim().isNotEmpty)
        'destinoFinalEndereco': destinoFinalEndereco!.trim(),
      if (destinoFinalLatitude != null)
        'destinoFinalLatitude': destinoFinalLatitude,
      if (destinoFinalLongitude != null)
        'destinoFinalLongitude': destinoFinalLongitude,
      if (capacidadePlanejada != null)
        'capacidadePlanejada': capacidadePlanejada,
      'passageiroIds': passageiroIds,
    };
  }
}
