import 'dart:convert';

import 'package:http/http.dart' as http;

import '../../../services/api_constants.dart';
import '../domain/admin_company_link_flow_models.dart';
import '../domain/admin_motorista_record.dart';
import '../domain/admin_passageiro_motorista_link_record.dart';
import '../domain/admin_passageiro_record.dart';
import '../domain/admin_passageiro_servico_record.dart';
import '../domain/admin_portal_session.dart';
import '../domain/admin_responsavel_passageiro_link_record.dart';
import '../domain/admin_responsavel_record.dart';
import '../domain/admin_servico_recorrente_record.dart';
import 'admin_portal_http_support.dart';

abstract class AdminResponsavelPassageiroLinkGateway {
  Future<void> linkResponsavelPassageiro({
    required int responsavelId,
    required int passageiroId,
  });
  Future<void> unlinkResponsavelPassageiro({
    required int responsavelId,
    required int passageiroId,
  });
  Future<List<AdminPassageiroRecord>> fetchPassageiros();
  Future<List<AdminResponsavelRecord>> fetchResponsaveis();
}

abstract class AdminPassengerOnboardingGateway {
  Future<List<AdminAccessRequestRecord>> fetchAccessRequests();
  Future<AdminAccessRequestDetailRecord> fetchAccessRequestDetail(int id);
  Future<AdminAccessRequestDetailRecord> approveAccessRequest({
    required int requestId,
    int? motoristaId,
    String? reason,
    List<int>? servicoIds,
  });
  Future<AdminAccessRequestDetailRecord> rejectAccessRequest({
    required int requestId,
    String? reason,
  });
  Future<List<AdminInviteCodeRecord>> fetchInviteCodes();
  Future<AdminInviteCodeDetailRecord> fetchInviteCodeDetail(int id);
  Future<AdminInviteCodeDetailRecord> createInviteCode({
    DateTime? expiresAt,
    int? maxUses,
  });
  Future<AdminInviteCodeDetailRecord> cancelInviteCode(int id);
  Future<AdminInviteCodeDetailRecord> regenerateInviteCode(int id);
}

abstract class AdminVinculoGateway
    implements
        AdminResponsavelPassageiroLinkGateway,
        AdminPassengerOnboardingGateway {
  Future<List<AdminPassageiroMotoristaLinkRecord>>
  fetchPassageiroMotoristaLinks();
  Future<void> unlinkPassageiroMotorista(int passageiroId);
  Future<List<AdminResponsavelPassageiroLinkRecord>>
  fetchResponsavelPassageiroLinks();
  @override
  Future<void> linkResponsavelPassageiro({
    required int responsavelId,
    required int passageiroId,
  });
  @override
  Future<void> unlinkResponsavelPassageiro({
    required int responsavelId,
    required int passageiroId,
  });
  @override
  Future<List<AdminPassageiroRecord>> fetchPassageiros();
  @override
  Future<List<AdminResponsavelRecord>> fetchResponsaveis();
  Future<List<AdminMotoristaRecord>> fetchMotoristas();
  Future<List<AdminServicoRecorrenteRecord>> fetchServicos();
  Future<List<AdminPassageiroServicoRecord>> updatePassageiroServicos({
    required int passageiroId,
    required List<int> servicoIds,
  });
}

class AdminVinculoPortalService implements AdminVinculoGateway {
  AdminVinculoPortalService({
    required AdminPortalSession session,
    http.Client? client,
  }) : _session = session,
       _client = client ?? http.Client();

  final AdminPortalSession _session;
  final http.Client _client;

  @override
  Future<List<AdminPassageiroMotoristaLinkRecord>>
  fetchPassageiroMotoristaLinks() async {
    final response = await _client.get(
      Uri.parse('$baseUrl/company-admins/vinculos/passageiros-motoristas'),
      headers: adminAuthHeaders(_session),
    );

    if (response.statusCode != 200) {
      throwPortalHttpError(
        response,
        fallback: 'Nao foi possivel carregar os vinculos passageiro-motorista.',
      );
    }

    final data = jsonDecode(decodeAdminPortalBody(response)) as List<dynamic>;
    return data
        .map(
          (item) => AdminPassageiroMotoristaLinkRecord.fromJson(
            item as Map<String, dynamic>,
          ),
        )
        .toList(growable: false);
  }

  @override
  Future<void> unlinkPassageiroMotorista(int passageiroId) async {
    final response = await _client.delete(
      Uri.parse('$baseUrl/company-admins/passageiros/$passageiroId/motorista'),
      headers: adminAuthHeaders(_session),
    );

    if (response.statusCode != 204) {
      throwPortalHttpError(
        response,
        fallback:
            'Nao foi possivel remover o vínculo do passageiro com o motorista.',
      );
    }
  }

  @override
  Future<List<AdminResponsavelPassageiroLinkRecord>>
  fetchResponsavelPassageiroLinks() async {
    final response = await _client.get(
      Uri.parse('$baseUrl/company-admins/vinculos/responsaveis-passageiros'),
      headers: adminAuthHeaders(_session),
    );

    if (response.statusCode != 200) {
      throwPortalHttpError(
        response,
        fallback: 'Nao foi possivel carregar os vínculos de responsaveis.',
      );
    }

    final data = jsonDecode(decodeAdminPortalBody(response)) as List<dynamic>;
    return data
        .map(
          (item) => AdminResponsavelPassageiroLinkRecord.fromJson(
            item as Map<String, dynamic>,
          ),
        )
        .toList(growable: false);
  }

  @override
  Future<void> linkResponsavelPassageiro({
    required int responsavelId,
    required int passageiroId,
  }) async {
    final response = await _client.post(
      Uri.parse(
        '$baseUrl/company-admins/responsaveis/$responsavelId/passageiros',
      ),
      headers: adminJsonHeaders(_session),
      body: jsonEncode({'passageiroId': passageiroId}),
    );

    if (response.statusCode != 201) {
      throwPortalHttpError(
        response,
        fallback: 'Nao foi possivel criar o vínculo responsavel-passageiro.',
      );
    }
  }

  @override
  Future<void> unlinkResponsavelPassageiro({
    required int responsavelId,
    required int passageiroId,
  }) async {
    final response = await _client.delete(
      Uri.parse(
        '$baseUrl/company-admins/responsaveis/$responsavelId/passageiros/$passageiroId',
      ),
      headers: adminAuthHeaders(_session),
    );

    if (response.statusCode != 204) {
      throwPortalHttpError(
        response,
        fallback: 'Nao foi possivel remover o vínculo responsavel-passageiro.',
      );
    }
  }

  @override
  Future<List<AdminPassageiroRecord>> fetchPassageiros() async {
    final response = await _client.get(
      Uri.parse('$baseUrl/company-admins/passageiros'),
      headers: adminAuthHeaders(_session),
    );

    if (response.statusCode != 200) {
      throwPortalHttpError(
        response,
        fallback: 'Nao foi possivel carregar os passageiros para os vínculos.',
      );
    }

    final data = jsonDecode(decodeAdminPortalBody(response)) as List<dynamic>;
    return data
        .map(
          (item) =>
              AdminPassageiroRecord.fromJson(item as Map<String, dynamic>),
        )
        .toList(growable: false);
  }

  @override
  Future<List<AdminResponsavelRecord>> fetchResponsaveis() async {
    final response = await _client.get(
      Uri.parse('$baseUrl/company-admins/responsaveis'),
      headers: adminAuthHeaders(_session),
    );

    if (response.statusCode != 200) {
      throwPortalHttpError(
        response,
        fallback: 'Nao foi possivel carregar os responsaveis para os vínculos.',
      );
    }

    final data = jsonDecode(decodeAdminPortalBody(response)) as List<dynamic>;
    return data
        .map(
          (item) =>
              AdminResponsavelRecord.fromJson(item as Map<String, dynamic>),
        )
        .toList(growable: false);
  }

  @override
  Future<List<AdminMotoristaRecord>> fetchMotoristas() async {
    final response = await _client.get(
      Uri.parse('$baseUrl/company-admins/motoristas'),
      headers: adminAuthHeaders(_session),
    );

    if (response.statusCode != 200) {
      throwPortalHttpError(
        response,
        fallback: 'Nao foi possivel carregar os motoristas do portal.',
      );
    }

    final data = jsonDecode(decodeAdminPortalBody(response)) as List<dynamic>;
    return data
        .map(
          (item) => AdminMotoristaRecord.fromJson(item as Map<String, dynamic>),
        )
        .toList(growable: false);
  }

  @override
  Future<List<AdminServicoRecorrenteRecord>> fetchServicos() async {
    final response = await _client.get(
      Uri.parse('$baseUrl/company-admins/servicos-recorrentes'),
      headers: adminAuthHeaders(_session),
    );

    if (response.statusCode != 200) {
      throwPortalHttpError(
        response,
        fallback: 'Nao foi possivel carregar os servicos recorrentes.',
      );
    }

    final data = jsonDecode(decodeAdminPortalBody(response)) as List<dynamic>;
    return data
        .map(
          (item) => AdminServicoRecorrenteRecord.fromJson(
            item as Map<String, dynamic>,
          ),
        )
        .toList(growable: false);
  }

  @override
  Future<List<AdminPassageiroServicoRecord>> updatePassageiroServicos({
    required int passageiroId,
    required List<int> servicoIds,
  }) async {
    final response = await _client.put(
      Uri.parse('$baseUrl/company-admins/passageiros/$passageiroId/servicos'),
      headers: adminJsonHeaders(_session),
      body: jsonEncode({'servicoIds': servicoIds}),
    );

    if (response.statusCode != 200) {
      throwPortalHttpError(
        response,
        fallback: 'Nao foi possivel atualizar os servicos do passageiro.',
      );
    }

    final data = jsonDecode(decodeAdminPortalBody(response)) as List<dynamic>;
    return data
        .map(
          (item) => AdminPassageiroServicoRecord.fromJson(
            item as Map<String, dynamic>,
          ),
        )
        .toList(growable: false);
  }

  @override
  Future<List<AdminAccessRequestRecord>> fetchAccessRequests() async {
    final response = await _client.get(
      Uri.parse('$baseUrl/company-admins/access-requests'),
      headers: adminAuthHeaders(_session),
    );

    if (response.statusCode != 200) {
      throwPortalHttpError(
        response,
        fallback: 'Nao foi possivel carregar as solicitacoes de acesso.',
      );
    }

    final data = jsonDecode(decodeAdminPortalBody(response)) as List<dynamic>;
    return data
        .map(
          (item) =>
              AdminAccessRequestRecord.fromJson(item as Map<String, dynamic>),
        )
        .toList(growable: false);
  }

  @override
  Future<AdminAccessRequestDetailRecord> fetchAccessRequestDetail(
    int id,
  ) async {
    final response = await _client.get(
      Uri.parse('$baseUrl/company-admins/access-requests/$id'),
      headers: adminAuthHeaders(_session),
    );

    if (response.statusCode != 200) {
      throwPortalHttpError(
        response,
        fallback: 'Nao foi possivel carregar os detalhes da solicitacao.',
      );
    }

    final data =
        jsonDecode(decodeAdminPortalBody(response)) as Map<String, dynamic>;
    return AdminAccessRequestDetailRecord.fromJson(data);
  }

  @override
  Future<AdminAccessRequestDetailRecord> approveAccessRequest({
    required int requestId,
    int? motoristaId,
    String? reason,
    List<int>? servicoIds,
  }) async {
    final response = await _client.post(
      Uri.parse('$baseUrl/company-admins/access-requests/$requestId/approve'),
      headers: adminJsonHeaders(_session),
      body: jsonEncode({
        'motoristaId': motoristaId,
        'reason': reason?.trim().isEmpty == true ? null : reason?.trim(),
        if (servicoIds != null) 'servicoIds': servicoIds,
      }),
    );

    if (response.statusCode != 200) {
      throwPortalHttpError(
        response,
        fallback: 'Nao foi possivel aprovar a solicitacao.',
      );
    }

    final data =
        jsonDecode(decodeAdminPortalBody(response)) as Map<String, dynamic>;
    return AdminAccessRequestDetailRecord.fromJson(data);
  }

  @override
  Future<AdminAccessRequestDetailRecord> rejectAccessRequest({
    required int requestId,
    String? reason,
  }) async {
    final response = await _client.post(
      Uri.parse('$baseUrl/company-admins/access-requests/$requestId/reject'),
      headers: adminJsonHeaders(_session),
      body: jsonEncode({
        'reason': reason?.trim().isEmpty == true ? null : reason?.trim(),
      }),
    );

    if (response.statusCode != 200) {
      throwPortalHttpError(
        response,
        fallback: 'Nao foi possivel rejeitar a solicitacao.',
      );
    }

    final data =
        jsonDecode(decodeAdminPortalBody(response)) as Map<String, dynamic>;
    return AdminAccessRequestDetailRecord.fromJson(data);
  }

  @override
  Future<List<AdminInviteCodeRecord>> fetchInviteCodes() async {
    final response = await _client.get(
      Uri.parse('$baseUrl/company-admins/invite-codes'),
      headers: adminAuthHeaders(_session),
    );

    if (response.statusCode != 200) {
      throwPortalHttpError(
        response,
        fallback: 'Nao foi possivel carregar os convites da empresa.',
      );
    }

    final data = jsonDecode(decodeAdminPortalBody(response)) as List<dynamic>;
    return data
        .map(
          (item) =>
              AdminInviteCodeRecord.fromJson(item as Map<String, dynamic>),
        )
        .toList(growable: false);
  }

  @override
  Future<AdminInviteCodeDetailRecord> fetchInviteCodeDetail(int id) async {
    final response = await _client.get(
      Uri.parse('$baseUrl/company-admins/invite-codes/$id'),
      headers: adminAuthHeaders(_session),
    );

    if (response.statusCode != 200) {
      throwPortalHttpError(
        response,
        fallback: 'Nao foi possivel carregar os detalhes do convite.',
      );
    }

    final data =
        jsonDecode(decodeAdminPortalBody(response)) as Map<String, dynamic>;
    return AdminInviteCodeDetailRecord.fromJson(data);
  }

  @override
  Future<AdminInviteCodeDetailRecord> createInviteCode({
    DateTime? expiresAt,
    int? maxUses,
  }) async {
    final response = await _client.post(
      Uri.parse('$baseUrl/company-admins/invite-codes'),
      headers: adminJsonHeaders(_session),
      body: jsonEncode({
        'expiresAt': expiresAt?.toUtc().toIso8601String(),
        'maxUses': maxUses,
      }),
    );

    if (response.statusCode != 201) {
      throwPortalHttpError(
        response,
        fallback: 'Nao foi possivel gerar um novo convite agora.',
      );
    }

    final data =
        jsonDecode(decodeAdminPortalBody(response)) as Map<String, dynamic>;
    return AdminInviteCodeDetailRecord.fromJson(data);
  }

  @override
  Future<AdminInviteCodeDetailRecord> cancelInviteCode(int id) async {
    final response = await _client.post(
      Uri.parse('$baseUrl/company-admins/invite-codes/$id/cancel'),
      headers: adminJsonHeaders(_session),
    );

    if (response.statusCode != 200) {
      throwPortalHttpError(
        response,
        fallback: 'Nao foi possivel cancelar o convite agora.',
      );
    }

    final data =
        jsonDecode(decodeAdminPortalBody(response)) as Map<String, dynamic>;
    return AdminInviteCodeDetailRecord.fromJson(data);
  }

  @override
  Future<AdminInviteCodeDetailRecord> regenerateInviteCode(int id) async {
    final response = await _client.post(
      Uri.parse('$baseUrl/company-admins/invite-codes/$id/regenerate'),
      headers: adminJsonHeaders(_session),
    );

    if (response.statusCode != 200) {
      throwPortalHttpError(
        response,
        fallback: 'Nao foi possivel regenerar o convite agora.',
      );
    }

    final data =
        jsonDecode(decodeAdminPortalBody(response)) as Map<String, dynamic>;
    return AdminInviteCodeDetailRecord.fromJson(data);
  }
}
