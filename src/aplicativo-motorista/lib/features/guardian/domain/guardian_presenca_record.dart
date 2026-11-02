class GuardianPresencaRecord {
  const GuardianPresencaRecord({
    required this.id,
    required this.passageiroId,
    required this.dataPresenca,
    required this.confirmado,
  });

  final int id;
  final int passageiroId;
  final DateTime dataPresenca;
  final bool confirmado;

  factory GuardianPresencaRecord.fromJson(Map<String, dynamic> json) {
    final passageiro = json['passageiro'];
    final data = (json['dataPresenca'] as String?)?.trim() ?? '';
    final partes = data.split('-');
    final parsedDate =
        partes.length == 3
            ? DateTime(
              int.parse(partes[0]),
              int.parse(partes[1]),
              int.parse(partes[2]),
            )
            : DateTime.now();

    return GuardianPresencaRecord(
      id: (json['id'] as num).toInt(),
      passageiroId:
          passageiro is Map<String, dynamic>
              ? (passageiro['id'] as num?)?.toInt() ?? 0
              : 0,
      dataPresenca: parsedDate,
      confirmado: (json['confirmado'] as bool?) ?? false,
    );
  }
}
