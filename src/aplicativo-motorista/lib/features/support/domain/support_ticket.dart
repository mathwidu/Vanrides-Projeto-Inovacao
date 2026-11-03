enum SupportTicketStatus { open, inProgress, resolved, unknown }

extension SupportTicketStatusX on SupportTicketStatus {
  String get backendValue => switch (this) {
    SupportTicketStatus.open => 'OPEN',
    SupportTicketStatus.inProgress => 'IN_PROGRESS',
    SupportTicketStatus.resolved => 'RESOLVED',
    SupportTicketStatus.unknown => 'UNKNOWN',
  };

  String get label => switch (this) {
    SupportTicketStatus.open => 'Aberto',
    SupportTicketStatus.inProgress => 'Em atendimento',
    SupportTicketStatus.resolved => 'Resolvido',
    SupportTicketStatus.unknown => 'Status indefinido',
  };

  String get userSummary => switch (this) {
    SupportTicketStatus.open => 'Recebemos sua mensagem.',
    SupportTicketStatus.inProgress => 'Empresa está analisando.',
    SupportTicketStatus.resolved => 'Atendimento resolvido.',
    SupportTicketStatus.unknown => 'Aguardando atualização.',
  };

  String get adminSummary => switch (this) {
    SupportTicketStatus.open => 'Usuario aguardando retorno',
    SupportTicketStatus.inProgress => 'Empresa esta analisando',
    SupportTicketStatus.resolved => 'Atendimento resolvido',
    SupportTicketStatus.unknown => 'Revisar status',
  };

  static SupportTicketStatus fromBackend(String? value) {
    return switch ((value ?? '').trim().toUpperCase()) {
      'OPEN' => SupportTicketStatus.open,
      'IN_PROGRESS' => SupportTicketStatus.inProgress,
      'RESOLVED' => SupportTicketStatus.resolved,
      _ => SupportTicketStatus.unknown,
    };
  }
}

class SupportTicketSummary {
  const SupportTicketSummary({
    required this.id,
    required this.status,
    required this.category,
    required this.subject,
    required this.createdByRole,
    required this.createdByUserId,
    required this.createdByName,
    required this.createdAt,
    required this.updatedAt,
    this.resolvedAt,
  });

  final int id;
  final SupportTicketStatus status;
  final String category;
  final String subject;
  final String createdByRole;
  final int createdByUserId;
  final String createdByName;
  final DateTime createdAt;
  final DateTime updatedAt;
  final DateTime? resolvedAt;

  factory SupportTicketSummary.fromJson(Map<String, dynamic> json) {
    return SupportTicketSummary(
      id: (json['id'] as num).toInt(),
      status: SupportTicketStatusX.fromBackend(json['status'] as String?),
      category: (json['category'] as String? ?? '').trim(),
      subject: (json['subject'] as String? ?? '').trim(),
      createdByRole: (json['createdByRole'] as String? ?? '').trim(),
      createdByUserId: (json['createdByUserId'] as num?)?.toInt() ?? 0,
      createdByName: (json['createdByName'] as String? ?? '').trim(),
      createdAt: _parseDate(json['createdAt']),
      updatedAt: _parseDate(json['updatedAt']),
      resolvedAt: _parseNullableDate(json['resolvedAt']),
    );
  }
}

class SupportTicket extends SupportTicketSummary {
  const SupportTicket({
    required super.id,
    required super.status,
    required super.category,
    required super.subject,
    required super.createdByRole,
    required super.createdByUserId,
    required super.createdByName,
    required super.createdAt,
    required super.updatedAt,
    super.resolvedAt,
    required this.messages,
  });

  final List<SupportTicketMessage> messages;

  factory SupportTicket.fromJson(Map<String, dynamic> json) {
    final messages = (json['messages'] as List<dynamic>? ?? const [])
        .map(
          (item) => SupportTicketMessage.fromJson(item as Map<String, dynamic>),
        )
        .toList(growable: false);
    return SupportTicket(
      id: (json['id'] as num).toInt(),
      status: SupportTicketStatusX.fromBackend(json['status'] as String?),
      category: (json['category'] as String? ?? '').trim(),
      subject: (json['subject'] as String? ?? '').trim(),
      createdByRole: (json['createdByRole'] as String? ?? '').trim(),
      createdByUserId: (json['createdByUserId'] as num?)?.toInt() ?? 0,
      createdByName: (json['createdByName'] as String? ?? '').trim(),
      createdAt: _parseDate(json['createdAt']),
      updatedAt: _parseDate(json['updatedAt']),
      resolvedAt: _parseNullableDate(json['resolvedAt']),
      messages: messages,
    );
  }

  SupportTicketSummary toSummary() {
    return SupportTicketSummary(
      id: id,
      status: status,
      category: category,
      subject: subject,
      createdByRole: createdByRole,
      createdByUserId: createdByUserId,
      createdByName: createdByName,
      createdAt: createdAt,
      updatedAt: updatedAt,
      resolvedAt: resolvedAt,
    );
  }
}

class SupportTicketMessage {
  const SupportTicketMessage({
    required this.id,
    required this.authorRole,
    required this.authorUserId,
    required this.authorName,
    required this.message,
    required this.createdAt,
  });

  final int id;
  final String authorRole;
  final int authorUserId;
  final String authorName;
  final String message;
  final DateTime createdAt;

  factory SupportTicketMessage.fromJson(Map<String, dynamic> json) {
    return SupportTicketMessage(
      id: (json['id'] as num).toInt(),
      authorRole: (json['authorRole'] as String? ?? '').trim(),
      authorUserId: (json['authorUserId'] as num?)?.toInt() ?? 0,
      authorName: (json['authorName'] as String? ?? '').trim(),
      message: (json['message'] as String? ?? '').trim(),
      createdAt: _parseDate(json['createdAt']),
    );
  }
}

DateTime _parseDate(Object? value) {
  return _parseNullableDate(value) ?? DateTime.fromMillisecondsSinceEpoch(0);
}

DateTime? _parseNullableDate(Object? value) {
  if (value is! String || value.trim().isEmpty) {
    return null;
  }
  return DateTime.tryParse(value);
}

class SupportTicketException implements Exception {
  const SupportTicketException(this.message);

  final String message;

  @override
  String toString() => message;
}
