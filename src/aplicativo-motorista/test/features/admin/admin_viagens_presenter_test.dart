import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_motorista_record.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_portal_exception.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_viagem_create_input.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_viagem_record.dart';
import 'package:vanrides_frontend_dev/features/admin/presentation/admin_viagens_presenter.dart';

void main() {
  const presenter = AdminViagensPresenter();

  test('normaliza data operacional sem horario', () {
    final date = presenter.operationDate(
      DateTime(2026, 6, 19, 18, 45),
      DateTime(2026, 1, 1, 9),
    );

    expect(date, DateTime(2026, 6, 19));
  });

  test('resume viagens por status operacional', () {
    final summary = AdminViagensListSummary.fromTrips([
      _trip(id: 1, status: 'EM_TRANSITO'),
      _trip(id: 2, status: 'COMPLETED'),
      _trip(id: 3, status: 'PLANEJADA'),
    ]);

    expect(summary.inProgressCount, 1);
    expect(summary.completedCount, 1);
  });

  test('encontra viagem existente por servico e data normalizada', () {
    final trip = _trip(
      id: 31,
      servicoId: 44,
      dataOperacao: DateTime(2026, 6, 19, 9, 30),
    );

    final existing = presenter.findExistingTripForInput(
      [trip, _trip(id: 32, servicoId: 45)],
      AdminViagemCreateInput(
        servicoId: 44,
        dataOperacao: DateTime(2026, 6, 19, 21),
      ),
    );

    expect(existing?.id, 31);
  });

  test('encontra viagem legada por rota quando input nao tem servico', () {
    final existing = presenter.findExistingTripForInput(
      [_trip(id: 41, rotaBaseId: 7, servicoId: null)],
      AdminViagemCreateInput(
        rotaBaseId: 7,
        dataOperacao: DateTime(2026, 6, 19),
      ),
    );

    expect(existing?.id, 41);
  });

  test('traduz conflitos tecnicos para orientacao operacional', () {
    expect(
      presenter.createConflictMaintenanceMessage(
        const AdminPortalException('Conflict', code: 'CONFLICT'),
      ),
      contains('Não apague nem recrie'),
    );
  });

  test('traduz status de presenca para feedback humano', () {
    expect(
      presenter.presenceUpdateMessage('ABSENT'),
      'Presença marcada como ausente.',
    );
    expect(presenter.presenceUpdateMessage('UNKNOWN'), 'Presença atualizada.');
  });
}

AdminViagemRecord _trip({
  required int id,
  String status = 'PLANEJADA',
  DateTime? dataOperacao,
  int? rotaBaseId = 10,
  int? servicoId = 20,
}) {
  return AdminViagemRecord(
    id: id,
    rotaBaseId: rotaBaseId,
    servicoId: servicoId,
    rotaNome: 'Rota $id',
    servicoNome: 'Serviço $id',
    dataOperacao: dataOperacao ?? DateTime(2026, 6, 19),
    status: status,
    motorista: const AdminMotoristaRecord(
      id: 1,
      nome: 'Motorista',
      email: 'm@vanrides.test',
      telefone: '51999990000',
    ),
    quantidadeParadas: 2,
  );
}
