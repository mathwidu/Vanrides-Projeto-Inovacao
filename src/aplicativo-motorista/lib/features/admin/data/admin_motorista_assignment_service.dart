import '../domain/admin_portal_session.dart';
import '../domain/admin_servico_recorrente_record.dart';
import '../domain/admin_viagem_record.dart';
import 'admin_servicos_recorrentes_portal_service.dart';
import 'admin_viagens_portal_service.dart';

abstract class AdminMotoristaAssignmentGateway {
  Future<List<AdminServicoRecorrenteRecord>> fetchServicos();
  Future<List<AdminViagemRecord>> fetchViagens({DateTime? data});
}

class AdminMotoristaAssignmentPortalService
    implements AdminMotoristaAssignmentGateway {
  AdminMotoristaAssignmentPortalService({required AdminPortalSession session})
    : _servicosGateway = AdminServicosRecorrentesPortalService(
        session: session,
      ),
      _viagensGateway = AdminViagensPortalService(session: session);

  final AdminServicosRecorrentesGateway _servicosGateway;
  final AdminViagensGateway _viagensGateway;

  @override
  Future<List<AdminServicoRecorrenteRecord>> fetchServicos() {
    return _servicosGateway.fetchServicos();
  }

  @override
  Future<List<AdminViagemRecord>> fetchViagens({DateTime? data}) {
    return _viagensGateway.fetchViagens(data: data);
  }
}
