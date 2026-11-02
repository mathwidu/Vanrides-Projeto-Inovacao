import 'admin_motorista_record.dart';

class AdminAccessRequestRecord {
  const AdminAccessRequestRecord({
    required this.id,
    required this.passageiroId,
    required this.passageiroNome,
    required this.passageiroEmail,
    required this.companyName,
    required this.source,
    required this.emailVerified,
    this.requestedAt,
  });

  final int id;
  final int passageiroId;
  final String passageiroNome;
  final String passageiroEmail;
  final String companyName;
  final String source;
  final bool emailVerified;
  final DateTime? requestedAt;

  factory AdminAccessRequestRecord.fromJson(Map<String, dynamic> json) {
    return AdminAccessRequestRecord(
      id: (json['id'] as num).toInt(),
      passageiroId: (json['passageiroId'] as num).toInt(),
      passageiroNome: (json['passageiroNome'] as String?)?.trim() ?? '',
      passageiroEmail: (json['passageiroEmail'] as String?)?.trim() ?? '',
      companyName: (json['companyName'] as String?)?.trim() ?? '',
      source: (json['source'] as String?)?.trim() ?? 'REQUEST',
      emailVerified: json['emailVerified'] == true,
      requestedAt: DateTime.tryParse((json['requestedAt'] as String?) ?? ''),
    );
  }
}

class AdminAccessRequestDetailRecord {
  const AdminAccessRequestDetailRecord({
    required this.id,
    required this.passageiroId,
    required this.passageiroNome,
    required this.passageiroEmail,
    required this.passageiroTelefone,
    required this.companyName,
    required this.source,
    required this.status,
    required this.emailVerified,
    this.requestedAt,
    this.motoristaAtual,
  });

  final int id;
  final int passageiroId;
  final String passageiroNome;
  final String passageiroEmail;
  final String passageiroTelefone;
  final String companyName;
  final String source;
  final String status;
  final bool emailVerified;
  final DateTime? requestedAt;
  final AdminMotoristaRecord? motoristaAtual;

  factory AdminAccessRequestDetailRecord.fromJson(Map<String, dynamic> json) {
    return AdminAccessRequestDetailRecord(
      id: (json['id'] as num).toInt(),
      passageiroId: (json['passageiroId'] as num).toInt(),
      passageiroNome: (json['passageiroNome'] as String?)?.trim() ?? '',
      passageiroEmail: (json['passageiroEmail'] as String?)?.trim() ?? '',
      passageiroTelefone: (json['passageiroTelefone'] as String?)?.trim() ?? '',
      companyName: (json['companyName'] as String?)?.trim() ?? '',
      source: (json['source'] as String?)?.trim() ?? 'REQUEST',
      status: (json['status'] as String?)?.trim() ?? 'PENDING',
      emailVerified: json['emailVerified'] == true,
      requestedAt: DateTime.tryParse((json['requestedAt'] as String?) ?? ''),
      motoristaAtual:
          json['motoristaAtual'] is Map<String, dynamic>
              ? AdminMotoristaRecord.fromJson(
                json['motoristaAtual'] as Map<String, dynamic>,
              )
              : null,
    );
  }
}

class AdminInviteCodeRecord {
  const AdminInviteCodeRecord({
    required this.id,
    required this.code,
    required this.status,
    required this.useCount,
    this.maxUses,
    this.expiresAt,
    this.createdAt,
  });

  final int id;
  final String code;
  final String status;
  final int useCount;
  final int? maxUses;
  final DateTime? expiresAt;
  final DateTime? createdAt;

  factory AdminInviteCodeRecord.fromJson(Map<String, dynamic> json) {
    return AdminInviteCodeRecord(
      id: (json['id'] as num).toInt(),
      code: (json['code'] as String?)?.trim() ?? '',
      status: (json['status'] as String?)?.trim() ?? 'ACTIVE',
      useCount: (json['useCount'] as num?)?.toInt() ?? 0,
      maxUses: (json['maxUses'] as num?)?.toInt(),
      expiresAt: DateTime.tryParse((json['expiresAt'] as String?) ?? ''),
      createdAt: DateTime.tryParse((json['createdAt'] as String?) ?? ''),
    );
  }
}

class AdminInviteCodeUsageRecord {
  const AdminInviteCodeUsageRecord({
    required this.id,
    required this.passageiroId,
    required this.passageiroNome,
    required this.passageiroEmail,
    required this.emailStatus,
    this.usedAt,
  });

  final int id;
  final int passageiroId;
  final String passageiroNome;
  final String passageiroEmail;
  final String emailStatus;
  final DateTime? usedAt;

  factory AdminInviteCodeUsageRecord.fromJson(Map<String, dynamic> json) {
    return AdminInviteCodeUsageRecord(
      id: (json['id'] as num).toInt(),
      passageiroId: (json['passageiroId'] as num).toInt(),
      passageiroNome: (json['passageiroNome'] as String?)?.trim() ?? '',
      passageiroEmail: (json['passageiroEmail'] as String?)?.trim() ?? '',
      emailStatus: (json['emailStatus'] as String?)?.trim() ?? 'UNKNOWN',
      usedAt: DateTime.tryParse((json['usedAt'] as String?) ?? ''),
    );
  }
}

class AdminInviteCodeDetailRecord extends AdminInviteCodeRecord {
  const AdminInviteCodeDetailRecord({
    required super.id,
    required super.code,
    required super.status,
    required super.useCount,
    super.maxUses,
    super.expiresAt,
    super.createdAt,
    this.lastUsedAt,
    this.usages = const [],
  });

  final DateTime? lastUsedAt;
  final List<AdminInviteCodeUsageRecord> usages;

  factory AdminInviteCodeDetailRecord.fromJson(Map<String, dynamic> json) {
    return AdminInviteCodeDetailRecord(
      id: (json['id'] as num).toInt(),
      code: (json['code'] as String?)?.trim() ?? '',
      status: (json['status'] as String?)?.trim() ?? 'ACTIVE',
      useCount: (json['useCount'] as num?)?.toInt() ?? 0,
      maxUses: (json['maxUses'] as num?)?.toInt(),
      expiresAt: DateTime.tryParse((json['expiresAt'] as String?) ?? ''),
      createdAt: DateTime.tryParse((json['createdAt'] as String?) ?? ''),
      lastUsedAt: DateTime.tryParse((json['lastUsedAt'] as String?) ?? ''),
      usages: (json['usages'] as List<dynamic>? ?? const [])
          .whereType<Map<String, dynamic>>()
          .map(AdminInviteCodeUsageRecord.fromJson)
          .toList(growable: false),
    );
  }
}

class AdminDriverInviteRecord {
  const AdminDriverInviteRecord({
    required this.id,
    required this.companyId,
    required this.companyName,
    required this.status,
    this.token,
    this.shareUrl,
    this.expiresAt,
    this.createdAt,
    this.acceptedAt,
    this.acceptedByMotoristaId,
    this.acceptedByMotoristaName,
  });

  final int id;
  final int companyId;
  final String companyName;
  final String? token;
  final String? shareUrl;
  final String status;
  final DateTime? expiresAt;
  final DateTime? createdAt;
  final DateTime? acceptedAt;
  final int? acceptedByMotoristaId;
  final String? acceptedByMotoristaName;

  factory AdminDriverInviteRecord.fromJson(Map<String, dynamic> json) {
    return AdminDriverInviteRecord(
      id: (json['id'] as num).toInt(),
      companyId: (json['companyId'] as num).toInt(),
      companyName: (json['companyName'] as String?)?.trim() ?? '',
      token: _optionalTrimmedString(json['token']),
      shareUrl: _optionalTrimmedString(json['shareUrl']),
      status: (json['status'] as String?)?.trim() ?? 'ACTIVE',
      expiresAt: DateTime.tryParse((json['expiresAt'] as String?) ?? ''),
      createdAt: DateTime.tryParse((json['createdAt'] as String?) ?? ''),
      acceptedAt: DateTime.tryParse((json['acceptedAt'] as String?) ?? ''),
      acceptedByMotoristaId: (json['acceptedByMotoristaId'] as num?)?.toInt(),
      acceptedByMotoristaName: _optionalTrimmedString(
        json['acceptedByMotoristaName'],
      ),
    );
  }
}

class AdminDriverLinkRequestRecord {
  const AdminDriverLinkRequestRecord({
    required this.id,
    required this.motoristaId,
    required this.motoristaNome,
    required this.motoristaEmail,
    required this.motoristaTelefone,
    required this.companyId,
    required this.companyName,
    required this.status,
    required this.emailVerified,
    this.requestedAt,
    this.reviewedAt,
    this.reviewNotes,
  });

  final int id;
  final int motoristaId;
  final String motoristaNome;
  final String motoristaEmail;
  final String motoristaTelefone;
  final int companyId;
  final String companyName;
  final String status;
  final DateTime? requestedAt;
  final DateTime? reviewedAt;
  final bool emailVerified;
  final String? reviewNotes;

  factory AdminDriverLinkRequestRecord.fromJson(Map<String, dynamic> json) {
    return AdminDriverLinkRequestRecord(
      id: (json['id'] as num).toInt(),
      motoristaId: (json['motoristaId'] as num).toInt(),
      motoristaNome: (json['motoristaNome'] as String?)?.trim() ?? '',
      motoristaEmail: (json['motoristaEmail'] as String?)?.trim() ?? '',
      motoristaTelefone: (json['motoristaTelefone'] as String?)?.trim() ?? '',
      companyId: (json['companyId'] as num).toInt(),
      companyName: (json['companyName'] as String?)?.trim() ?? '',
      status: (json['status'] as String?)?.trim() ?? 'PENDING',
      requestedAt: DateTime.tryParse((json['requestedAt'] as String?) ?? ''),
      reviewedAt: DateTime.tryParse((json['reviewedAt'] as String?) ?? ''),
      emailVerified: json['emailVerified'] == true,
      reviewNotes: _optionalTrimmedString(json['reviewNotes']),
    );
  }
}

String? _optionalTrimmedString(Object? value) {
  final text = (value as String?)?.trim();
  return text == null || text.isEmpty ? null : text;
}
