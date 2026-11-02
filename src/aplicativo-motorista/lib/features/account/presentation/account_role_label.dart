String presentAccountRoleLabel({
  String? tipoUsuario,
  String? papel,
  String fallback = 'Conta',
}) {
  final token = _roleToken(tipoUsuario) ?? _roleToken(papel);
  return switch (token) {
    'passenger' || 'passageiro' => 'Passageiro',
    'driver' || 'motorista' => 'Motorista',
    'guardian' || 'responsavel' || 'responsável' => 'Responsável',
    'admin' ||
    'companyadmin' ||
    'company_admin' ||
    'company-admin' => 'Administrador',
    _ => _cleanLabel(tipoUsuario) ?? _cleanLabel(papel) ?? fallback,
  };
}

String? _roleToken(String? value) {
  final cleaned = _cleanLabel(value);
  if (cleaned == null) {
    return null;
  }
  return cleaned.toLowerCase().replaceAll(' ', '');
}

String? _cleanLabel(String? value) {
  final cleaned = value?.trim();
  return cleaned == null || cleaned.isEmpty ? null : cleaned;
}
