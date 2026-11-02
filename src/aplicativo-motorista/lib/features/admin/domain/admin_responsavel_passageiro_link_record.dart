import 'admin_passageiro_linked_record.dart';
import 'admin_responsavel_record.dart';

class AdminResponsavelPassageiroLinkRecord {
  const AdminResponsavelPassageiroLinkRecord({
    required this.responsavel,
    required this.passageiro,
  });

  final AdminResponsavelRecord responsavel;
  final AdminPassageiroLinkedRecord passageiro;

  factory AdminResponsavelPassageiroLinkRecord.fromJson(Map<String, dynamic> json) {
    return AdminResponsavelPassageiroLinkRecord(
      responsavel: AdminResponsavelRecord.fromJson(json['responsavel'] as Map<String, dynamic>),
      passageiro: AdminPassageiroLinkedRecord.fromJson(json['passageiro'] as Map<String, dynamic>),
    );
  }
}
