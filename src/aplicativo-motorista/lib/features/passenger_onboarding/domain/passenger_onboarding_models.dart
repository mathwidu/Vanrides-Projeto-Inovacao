import '../../../app/session/app_company_link_status.dart';

class PassengerOnboardingException implements Exception {
  const PassengerOnboardingException(this.message);

  final String message;

  @override
  String toString() => message;
}

class PassengerOnboardingCompanyRecord {
  const PassengerOnboardingCompanyRecord({
    required this.id,
    required this.nome,
    required this.slug,
  });

  final int id;
  final String nome;
  final String slug;

  factory PassengerOnboardingCompanyRecord.fromJson(Map<String, dynamic> json) {
    return PassengerOnboardingCompanyRecord(
      id: (json['id'] as num).toInt(),
      nome: (json['nome'] as String?)?.trim() ?? '',
      slug: (json['slug'] as String?)?.trim() ?? '',
    );
  }
}

class PendingCompanyLinkRequestRecord {
  const PendingCompanyLinkRequestRecord({
    required this.id,
    required this.companyId,
    required this.companyName,
    required this.status,
    this.requestedAt,
  });

  final int id;
  final int companyId;
  final String companyName;
  final String status;
  final DateTime? requestedAt;

  factory PendingCompanyLinkRequestRecord.fromJson(Map<String, dynamic> json) {
    return PendingCompanyLinkRequestRecord(
      id: (json['id'] as num).toInt(),
      companyId: (json['companyId'] as num).toInt(),
      companyName: (json['companyName'] as String?)?.trim() ?? '',
      status: (json['status'] as String?)?.trim() ?? 'PENDING',
      requestedAt: DateTime.tryParse((json['requestedAt'] as String?) ?? ''),
    );
  }
}

class PassengerOnboardingStatusRecord {
  const PassengerOnboardingStatusRecord({
    required this.companyLinkStatus,
    this.companyId,
    this.companyName,
    this.motoristaId,
    this.pendingRequest,
  });

  final AppCompanyLinkStatus companyLinkStatus;
  final int? companyId;
  final String? companyName;
  final int? motoristaId;
  final PendingCompanyLinkRequestRecord? pendingRequest;

  factory PassengerOnboardingStatusRecord.fromJson(Map<String, dynamic> json) {
    return PassengerOnboardingStatusRecord(
      companyLinkStatus: AppCompanyLinkStatus.fromBackendValue(
        json['companyLinkStatus'] as String?,
      ),
      companyId: (json['companyId'] as num?)?.toInt(),
      companyName: (json['companyName'] as String?)?.trim(),
      motoristaId: (json['motoristaId'] as num?)?.toInt(),
      pendingRequest:
          json['pendingRequest'] is Map<String, dynamic>
              ? PendingCompanyLinkRequestRecord.fromJson(
                json['pendingRequest'] as Map<String, dynamic>,
              )
              : null,
    );
  }
}
