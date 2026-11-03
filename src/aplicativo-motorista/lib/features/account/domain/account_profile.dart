class AccountProfile {
  const AccountProfile({
    required this.id,
    required this.tipoUsuario,
    required this.papel,
    required this.nome,
    required this.email,
    required this.emailVerified,
    this.telefone,
    this.dataNascimento,
    String? avatarUrl,
    String? fotoPerfilUrl,
    this.menorDeIdade,
    this.empresa,
    this.passageiroId,
    this.motoristaId,
    this.responsavelId,
    this.companyAdminId,
  }) : avatarUrl = avatarUrl ?? fotoPerfilUrl;

  final int id;
  final String tipoUsuario;
  final String papel;
  final String nome;
  final String email;
  final String? telefone;
  final bool emailVerified;
  final String? dataNascimento;
  final String? avatarUrl;
  final bool? menorDeIdade;
  final AccountCompany? empresa;
  final int? passageiroId;
  final int? motoristaId;
  final int? responsavelId;
  final int? companyAdminId;

  factory AccountProfile.fromJson(Map<String, dynamic> json) {
    return AccountProfile(
      id: _requiredInt(json, 'id'),
      tipoUsuario: _requiredString(json, 'tipoUsuario'),
      papel: _requiredString(json, 'papel'),
      nome: _requiredString(json, 'nome'),
      email: _requiredString(json, 'email'),
      telefone: _optionalString(json, 'telefone'),
      emailVerified: _optionalBool(json, 'emailVerified') ?? false,
      dataNascimento: _optionalDateString(json, 'dataNascimento'),
      avatarUrl:
          _optionalString(json, 'avatarUrl') ??
          _optionalString(json, 'fotoPerfilUrl'),
      menorDeIdade: _optionalBool(json, 'menorDeIdade'),
      empresa: _optionalCompany(json['empresa']),
      passageiroId: _optionalInt(json, 'passageiroId'),
      motoristaId: _optionalInt(json, 'motoristaId'),
      responsavelId: _optionalInt(json, 'responsavelId'),
      companyAdminId: _optionalInt(json, 'companyAdminId'),
    );
  }

  AccountProfile copyWith({
    String? nome,
    String? telefone,
    String? dataNascimento,
    String? avatarUrl,
    bool? menorDeIdade,
    bool clearDataNascimento = false,
    bool clearAvatarUrl = false,
  }) {
    return AccountProfile(
      id: id,
      tipoUsuario: tipoUsuario,
      papel: papel,
      nome: nome ?? this.nome,
      email: email,
      telefone: telefone ?? this.telefone,
      emailVerified: emailVerified,
      dataNascimento:
          clearDataNascimento ? null : dataNascimento ?? this.dataNascimento,
      avatarUrl: clearAvatarUrl ? null : avatarUrl ?? this.avatarUrl,
      menorDeIdade: menorDeIdade ?? this.menorDeIdade,
      empresa: empresa,
      passageiroId: passageiroId,
      motoristaId: motoristaId,
      responsavelId: responsavelId,
      companyAdminId: companyAdminId,
    );
  }

  String? get fotoPerfilUrl => avatarUrl;
}

class AccountCompany {
  const AccountCompany({required this.id, required this.nome, this.slug});

  final int id;
  final String nome;
  final String? slug;

  factory AccountCompany.fromJson(Map<String, dynamic> json) {
    return AccountCompany(
      id: _requiredInt(json, 'id'),
      nome: _requiredString(json, 'nome'),
      slug: _optionalString(json, 'slug'),
    );
  }
}

AccountCompany? _optionalCompany(Object? value) {
  if (value == null) {
    return null;
  }
  if (value is Map<String, dynamic>) {
    return AccountCompany.fromJson(value);
  }
  throw const FormatException('Campo opcional "empresa" invalido.');
}

int _requiredInt(Map<String, dynamic> data, String key) {
  final value = data[key];
  if (value is num) {
    return value.toInt();
  }
  throw FormatException('Campo obrigatorio "$key" ausente ou invalido.');
}

int? _optionalInt(Map<String, dynamic> data, String key) {
  final value = data[key];
  if (value == null) {
    return null;
  }
  if (value is num) {
    return value.toInt();
  }
  throw FormatException('Campo opcional "$key" invalido.');
}

String _requiredString(Map<String, dynamic> data, String key) {
  final value = _optionalString(data, key);
  if (value == null) {
    throw FormatException('Campo obrigatorio "$key" ausente ou invalido.');
  }
  return value;
}

String? _optionalString(Map<String, dynamic> data, String key) {
  final value = data[key];
  if (value == null) {
    return null;
  }
  if (value is String) {
    final normalized = value.trim();
    return normalized.isEmpty ? null : normalized;
  }
  throw FormatException('Campo opcional "$key" invalido.');
}

bool? _optionalBool(Map<String, dynamic> data, String key) {
  final value = data[key];
  if (value == null) {
    return null;
  }
  if (value is bool) {
    return value;
  }
  throw FormatException('Campo opcional "$key" invalido.');
}

String? _optionalDateString(Map<String, dynamic> data, String key) {
  final value = data[key];
  if (value == null) {
    return null;
  }
  if (value is! String) {
    throw FormatException('Campo opcional "$key" invalido.');
  }

  final normalized = value.trim();
  if (normalized.isEmpty) {
    return null;
  }

  final parsed = DateTime.tryParse(normalized);
  if (parsed == null) {
    throw FormatException('Campo opcional "$key" invalido.');
  }
  final year = parsed.year.toString().padLeft(4, '0');
  final month = parsed.month.toString().padLeft(2, '0');
  final day = parsed.day.toString().padLeft(2, '0');
  return '$year-$month-$day';
}
