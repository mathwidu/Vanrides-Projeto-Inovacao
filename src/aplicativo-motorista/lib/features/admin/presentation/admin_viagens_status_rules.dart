bool adminViagensIsTripInProgressStatus(String status) {
  return _adminViagensMatchesTripStatus(status, const [
    'IN_PROGRESS',
    'EM_PROGRESSO',
    'EM_TRANSITO',
    'EM TRÂNSITO',
    'TRANSITO',
    'TRÂNSITO',
    'ANDAMENTO',
  ]);
}

bool adminViagensIsTripCompletedStatus(String status) {
  return _adminViagensMatchesTripStatus(status, const [
    'COMPLETED',
    'COMPLETE',
    'CONCLUIDA',
    'CONCLUÍDA',
    'CONCLUIDO',
    'CONCLUÍDO',
    'CONCL',
  ]);
}

bool adminViagensIsTripCanceledStatus(String status) {
  return _adminViagensMatchesTripStatus(status, const [
    'CANCELED',
    'CANCELLED',
    'CANCELADA',
    'CANCELADO',
    'CANCEL',
  ]);
}

bool _adminViagensMatchesTripStatus(
  String status,
  List<String> acceptedValues,
) {
  final normalized = status.trim().toUpperCase();
  return acceptedValues.any((value) => normalized.contains(value));
}
