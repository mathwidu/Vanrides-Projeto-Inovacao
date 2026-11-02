import '../../../app/session/app_company_link_status.dart';

class DriverOnboardingException implements Exception {
  const DriverOnboardingException(this.message);

  final String message;

  @override
  String toString() => message;
}

class DriverInvitePreviewRecord {
  const DriverInvitePreviewRecord({
    required this.companyId,
    required this.companyName,
    required this.role,
    required this.status,
    this.expiresAt,
  });

  final int companyId;
  final String companyName;
  final String role;
  final String status;
  final DateTime? expiresAt;

  factory DriverInvitePreviewRecord.fromJson(Map<String, dynamic> json) {
    return DriverInvitePreviewRecord(
      companyId: (json['companyId'] as num).toInt(),
      companyName: (json['companyName'] as String?)?.trim() ?? '',
      role: (json['role'] as String?)?.trim() ?? 'DRIVER',
      status: (json['status'] as String?)?.trim() ?? 'ACTIVE',
      expiresAt: DateTime.tryParse((json['expiresAt'] as String?) ?? ''),
    );
  }
}

class DriverOnboardingCompanyRecord {
  const DriverOnboardingCompanyRecord({
    required this.id,
    required this.nome,
    required this.slug,
  });

  final int id;
  final String nome;
  final String slug;

  factory DriverOnboardingCompanyRecord.fromJson(Map<String, dynamic> json) {
    return DriverOnboardingCompanyRecord(
      id: (json['id'] as num).toInt(),
      nome: (json['nome'] as String?)?.trim() ?? '',
      slug: (json['slug'] as String?)?.trim() ?? '',
    );
  }
}

class PendingDriverCompanyLinkRequestRecord {
  const PendingDriverCompanyLinkRequestRecord({
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

  factory PendingDriverCompanyLinkRequestRecord.fromJson(
    Map<String, dynamic> json,
  ) {
    return PendingDriverCompanyLinkRequestRecord(
      id: (json['id'] as num).toInt(),
      companyId: (json['companyId'] as num).toInt(),
      companyName: (json['companyName'] as String?)?.trim() ?? '',
      status: (json['status'] as String?)?.trim() ?? 'PENDING_APPROVAL',
      requestedAt: DateTime.tryParse((json['requestedAt'] as String?) ?? ''),
    );
  }
}

class DriverOnboardingStatusRecord {
  const DriverOnboardingStatusRecord({
    required this.companyLinkStatus,
    this.companyId,
    this.companyName,
    this.activeMembershipId,
    this.pendingRequest,
  });

  final AppCompanyLinkStatus companyLinkStatus;
  final int? companyId;
  final String? companyName;
  final int? activeMembershipId;
  final PendingDriverCompanyLinkRequestRecord? pendingRequest;

  factory DriverOnboardingStatusRecord.fromJson(Map<String, dynamic> json) {
    return DriverOnboardingStatusRecord(
      companyLinkStatus: AppCompanyLinkStatus.fromBackendValue(
        json['companyLinkStatus'] as String?,
      ),
      companyId: (json['companyId'] as num?)?.toInt(),
      companyName: (json['companyName'] as String?)?.trim(),
      activeMembershipId: (json['activeMembershipId'] as num?)?.toInt(),
      pendingRequest:
          json['pendingRequest'] is Map<String, dynamic>
              ? PendingDriverCompanyLinkRequestRecord.fromJson(
                json['pendingRequest'] as Map<String, dynamic>,
              )
              : null,
    );
  }
}
