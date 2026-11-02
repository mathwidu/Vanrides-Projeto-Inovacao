import 'admin_operation_days.dart';

class AdminRotaBaseCreateInput {
  const AdminRotaBaseCreateInput({
    required this.nome,
    required this.motoristaId,
    required this.passageiroIds,
    required this.geracaoAutomaticaAtiva,
    required this.diasOperacao,
    this.horarioPartida,
    this.destinoFinalNome,
    this.destinoFinalEndereco,
    this.destinoFinalLatitude,
    this.destinoFinalLongitude,
  });

  final String nome;
  final int motoristaId;
  final List<int> passageiroIds;
  final bool geracaoAutomaticaAtiva;
  final List<String> diasOperacao;
  final String? horarioPartida;
  final String? destinoFinalNome;
  final String? destinoFinalEndereco;
  final double? destinoFinalLatitude;
  final double? destinoFinalLongitude;

  Map<String, dynamic> toJson() {
    final destinoNome = _trimToNull(destinoFinalNome);
    final destinoEndereco = _trimToNull(destinoFinalEndereco);
    return {
      'nome': nome.trim(),
      'motoristaId': motoristaId,
      'passageiroIds': passageiroIds,
      'geracaoAutomaticaAtiva': geracaoAutomaticaAtiva,
      'diasOperacao': encodeAdminOperationDays(diasOperacao),
      if (horarioPartida != null) 'horarioPartida': horarioPartida,
      if (destinoNome != null) 'destinoFinalNome': destinoNome,
      if (destinoEndereco != null) 'destinoFinalEndereco': destinoEndereco,
      if (destinoFinalLatitude != null)
        'destinoFinalLatitude': destinoFinalLatitude,
      if (destinoFinalLongitude != null)
        'destinoFinalLongitude': destinoFinalLongitude,
    };
  }
}

String? _trimToNull(String? value) {
  final trimmed = value?.trim();
  if (trimmed == null || trimmed.isEmpty) return null;
  return trimmed;
}
