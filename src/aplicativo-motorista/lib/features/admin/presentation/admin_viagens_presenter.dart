import '../domain/admin_portal_exception.dart';
import '../domain/admin_viagem_create_input.dart';
import '../domain/admin_viagem_record.dart';
import 'admin_viagens_status_rules.dart';

enum AdminViagensMode { list, create, detail }

class AdminViagensListSummary {
  const AdminViagensListSummary({
    required this.inProgressCount,
    required this.completedCount,
  });

  final int inProgressCount;
  final int completedCount;

  factory AdminViagensListSummary.fromTrips(List<AdminViagemRecord> trips) {
    return AdminViagensListSummary(
      inProgressCount:
          trips
              .where((trip) => adminViagensIsTripInProgressStatus(trip.status))
              .length,
      completedCount:
          trips
              .where((trip) => adminViagensIsTripCompletedStatus(trip.status))
              .length,
    );
  }
}

class AdminViagensPresenter {
  const AdminViagensPresenter();

  DateTime normalizeDate(DateTime date) {
    return DateTime(date.year, date.month, date.day);
  }

  DateTime operationDate(DateTime? selectedDate, DateTime now) {
    return normalizeDate(selectedDate ?? now);
  }

  AdminViagemRecord? findExistingTripForInput(
    List<AdminViagemRecord> trips,
    AdminViagemCreateInput input,
  ) {
    final targetDate = normalizeDate(input.dataOperacao);
    for (final trip in trips) {
      final sameDate = normalizeDate(trip.dataOperacao) == targetDate;
      final sameService =
          input.servicoId != null && trip.servicoId == input.servicoId;
      final sameLegacyRoute =
          input.servicoId == null &&
          input.rotaBaseId != null &&
          trip.rotaBaseId == input.rotaBaseId;
      if (sameDate && (sameService || sameLegacyRoute)) {
        return trip;
      }
    }
    return null;
  }

  String createConflictMaintenanceMessage(AdminPortalException error) {
    final message = error.message.trim();
    final technicalConflict =
        message.toLowerCase() == 'conflict' || error.code == 'CONFLICT';
    if (technicalConflict) {
      return 'Abra a viagem existente e use Sincronizar lista para manter o serviço do dia. Não apague nem recrie a viagem planejada.';
    }
    return '$message Abra a viagem existente e use Sincronizar lista.';
  }

  String presenceUpdateMessage(String status) {
    return switch (status.toUpperCase()) {
      'CONFIRMED' => 'Presença marcada como vai.',
      'ABSENT' => 'Presença marcada como ausente.',
      'CANCELED_BY_COMPANY' => 'Presença cancelada pela empresa.',
      _ => 'Presença atualizada.',
    };
  }
}
