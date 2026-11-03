const String e2eOperationDateOverride = String.fromEnvironment(
  'VANRIDES_E2E_OPERATION_DATE',
  defaultValue: '',
);

String formatVanRidesOperationDate(DateTime value, {String? override}) {
  final candidate = (override ?? e2eOperationDateOverride).trim();
  if (_isIsoDate(candidate)) {
    return candidate;
  }

  final local = value.toLocal();
  final month = local.month.toString().padLeft(2, '0');
  final day = local.day.toString().padLeft(2, '0');
  return '${local.year}-$month-$day';
}

bool _isIsoDate(String value) {
  if (!RegExp(r'^\d{4}-\d{2}-\d{2}$').hasMatch(value)) {
    return false;
  }

  final parsed = DateTime.tryParse(value);
  return parsed != null &&
      parsed.year.toString().padLeft(4, '0') == value.substring(0, 4) &&
      parsed.month.toString().padLeft(2, '0') == value.substring(5, 7) &&
      parsed.day.toString().padLeft(2, '0') == value.substring(8, 10);
}
