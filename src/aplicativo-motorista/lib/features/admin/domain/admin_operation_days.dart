const Map<String, String> _uiToBackendOperationDay = {
  'SEG': 'MONDAY',
  'TER': 'TUESDAY',
  'QUA': 'WEDNESDAY',
  'QUI': 'THURSDAY',
  'SEX': 'FRIDAY',
  'SAB': 'SATURDAY',
  'DOM': 'SUNDAY',
};

const Map<String, String> _backendToUiOperationDay = {
  'MONDAY': 'SEG',
  'TUESDAY': 'TER',
  'WEDNESDAY': 'QUA',
  'THURSDAY': 'QUI',
  'FRIDAY': 'SEX',
  'SATURDAY': 'SAB',
  'SUNDAY': 'DOM',
};

String encodeAdminOperationDay(String value) {
  final normalized = value.trim().toUpperCase();
  return _uiToBackendOperationDay[normalized] ?? normalized;
}

String decodeAdminOperationDay(String value) {
  final normalized = value.trim().toUpperCase();
  return _backendToUiOperationDay[normalized] ?? normalized;
}

List<String> encodeAdminOperationDays(Iterable<String> values) {
  return values.map(encodeAdminOperationDay).toList(growable: false);
}

List<String> decodeAdminOperationDays(Iterable<dynamic> values) {
  return values
      .map((value) => decodeAdminOperationDay(value.toString()))
      .toList(growable: false);
}
