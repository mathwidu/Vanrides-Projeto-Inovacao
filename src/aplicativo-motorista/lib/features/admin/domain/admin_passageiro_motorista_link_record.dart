import 'admin_motorista_record.dart';

class AdminPassageiroMotoristaLinkRecord {
  const AdminPassageiroMotoristaLinkRecord({
    required this.passageiroId,
    required this.passageiroNome,
    required this.menorDeIdade,
    this.motorista,
  });

  final int passageiroId;
  final String passageiroNome;
  final bool menorDeIdade;
  final AdminMotoristaRecord? motorista;

  factory AdminPassageiroMotoristaLinkRecord.fromJson(Map<String, dynamic> json) {
    final motoristaJson = json['motorista'] as Map<String, dynamic>?;
    return AdminPassageiroMotoristaLinkRecord(
      passageiroId: (json['passageiroId'] as num).toInt(),
      passageiroNome: json['passageiroNome'] as String? ?? '',
      menorDeIdade: json['menorDeIdade'] as bool? ?? false,
      motorista: motoristaJson == null ? null : AdminMotoristaRecord.fromJson(motoristaJson),
    );
  }
}
