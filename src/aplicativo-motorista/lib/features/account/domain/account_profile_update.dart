class AccountProfileUpdate {
  const AccountProfileUpdate({
    this.nome,
    this.telefone,
    this.dataNascimento,
    this.fotoPerfilUrl,
  });

  final String? nome;
  final String? telefone;
  final String? dataNascimento;
  final String? fotoPerfilUrl;

  Map<String, Object?> toJson() {
    final payload = <String, Object?>{};
    if (nome != null) {
      payload['nome'] = nome!.trim();
    }
    if (telefone != null) {
      payload['telefone'] = telefone!.trim();
    }
    if (dataNascimento != null) {
      payload['dataNascimento'] = normalizeAccountProfileDateInput(
        dataNascimento,
      );
    }
    if (fotoPerfilUrl != null) {
      payload['fotoPerfilUrl'] = fotoPerfilUrl!.trim();
    }
    return payload;
  }
}

String? normalizeAccountProfileDateInput(String? value) {
  final trimmed = value?.trim();
  if (trimmed == null || trimmed.isEmpty) {
    return null;
  }
  final date = parseAccountProfileDateInput(trimmed)!;
  final year = date.year.toString().padLeft(4, '0');
  final month = date.month.toString().padLeft(2, '0');
  final day = date.day.toString().padLeft(2, '0');
  return '$year-$month-$day';
}

String? formatAccountProfileDate(DateTime? value) {
  if (value == null) {
    return null;
  }
  final year = value.year.toString().padLeft(4, '0');
  final month = value.month.toString().padLeft(2, '0');
  final day = value.day.toString().padLeft(2, '0');
  return '$year-$month-$day';
}

String formatAccountProfileDateBr(String? value) {
  final normalized = normalizeAccountProfileDateInput(value);
  if (normalized == null) {
    return 'Não informado';
  }

  final parts = normalized.split('-');
  return '${parts[2]}/${parts[1]}/${parts[0]}';
}

DateTime? parseAccountProfileDateInput(String value) {
  final trimmed = value.trim();
  if (trimmed.isEmpty) {
    return null;
  }

  final isoMatch = RegExp(r'^(\d{4})-(\d{2})-(\d{2})$').firstMatch(trimmed);
  if (isoMatch != null) {
    return _buildDate(
      isoMatch.group(1)!,
      isoMatch.group(2)!,
      isoMatch.group(3)!,
    );
  }

  final brMatch = RegExp(r'^(\d{2})/(\d{2})/(\d{4})$').firstMatch(trimmed);
  if (brMatch != null) {
    return _buildDate(brMatch.group(3)!, brMatch.group(2)!, brMatch.group(1)!);
  }

  throw const FormatException('Informe uma data válida.');
}

DateTime _buildDate(String yearValue, String monthValue, String dayValue) {
  final year = int.parse(yearValue);
  final month = int.parse(monthValue);
  final day = int.parse(dayValue);
  final date = DateTime(year, month, day);
  if (date.year != year || date.month != month || date.day != day) {
    throw const FormatException('Informe uma data válida.');
  }
  return date;
}
