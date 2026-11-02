import 'dart:convert';

import 'package:http/http.dart' as http;

import '../../../services/api_constants.dart';
import '../domain/admin_motorista_record.dart';
import '../domain/admin_portal_session.dart';
import '../domain/admin_rota_base_record.dart';
import '../domain/admin_servico_recorrente_record.dart';
import '../domain/admin_viagem_bulk_create_result.dart';
import '../domain/admin_viagem_create_input.dart';
import '../domain/admin_viagem_detail_record.dart';
import '../domain/admin_viagem_manifest_preview_record.dart';
import '../domain/admin_viagem_record.dart';
import 'admin_portal_http_support.dart';

abstract class AdminViagensGateway {
  Future<List<AdminViagemRecord>> fetchViagens({DateTime? data});
  Future<List<AdminRotaBaseRecord>> fetchRotas();
  Future<List<AdminServicoRecorrenteRecord>> fetchServicos();
  Future<List<AdminMotoristaRecord>> fetchMotoristas();
  Future<AdminViagemRecord> createViagem(AdminViagemCreateInput input);
  Future<AdminViagemBulkCreateResult> generateAutomaticViagens(
    DateTime dataOperacao,
  );
  Future<AdminViagemDetailRecord> fetchViagemDetail(int viagemId);
  Future<AdminViagemDetailRecord> updateViagemMotorista(
    int viagemId,
    int motoristaId,
  );
  Future<AdminViagemManifestPreviewRecord> fetchManifestPreview(int viagemId);
  Future<AdminViagemDetailRecord> syncManifesto(int viagemId);
  Future<AdminViagemDetailRecord> cancelViagem(int viagemId);
  Future<AdminViagemDetailRecord> updateManifestPresence({
    required int viagemId,
    required int servicoId,
    required int passageiroId,
    required DateTime dataOperacao,
    required int? presencaId,
    required String status,
  });
}

class AdminViagensPortalService implements AdminViagensGateway {
  AdminViagensPortalService({
    required AdminPortalSession session,
    http.Client? client,
  }) : _session = session,
       _client = client ?? http.Client();

  final AdminPortalSession _session;
  final http.Client _client;

  @override
  Future<List<AdminViagemRecord>> fetchViagens({DateTime? data}) async {
    final uri = Uri.parse('$baseUrl/company-admins/viagens').replace(
      queryParameters:
          data == null
              ? null
              : {
                'data':
                    '${data.year.toString().padLeft(4, '0')}-${data.month.toString().padLeft(2, '0')}-${data.day.toString().padLeft(2, '0')}',
              },
    );
    final response = await _client.get(
      uri,
      headers: adminAuthHeaders(_session),
    );

    if (response.statusCode != 200) {
      throwPortalHttpError(
        response,
        fallback: 'Nao foi possivel carregar as viagens.',
      );
    }

    final dataList =
        jsonDecode(decodeAdminPortalBody(response)) as List<dynamic>;
    return dataList
        .map((item) => AdminViagemRecord.fromJson(item as Map<String, dynamic>))
        .toList(growable: false);
  }

  @override
  Future<List<AdminRotaBaseRecord>> fetchRotas() async {
    final response = await _client.get(
      Uri.parse('$baseUrl/company-admins/rotas-base'),
      headers: adminAuthHeaders(_session),
    );

    if (response.statusCode != 200) {
      throwPortalHttpError(
        response,
        fallback: 'Nao foi possivel carregar as rotas-base.',
      );
    }

    final data = jsonDecode(decodeAdminPortalBody(response)) as List<dynamic>;
    return data
        .map(
          (item) => AdminRotaBaseRecord.fromJson(item as Map<String, dynamic>),
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
  Future<List<AdminMotoristaRecord>> fetchMotoristas() async {
    final response = await _client.get(
      Uri.parse('$baseUrl/company-admins/motoristas'),
      headers: adminAuthHeaders(_session),
    );

    if (response.statusCode != 200) {
      throwPortalHttpError(
        response,
        fallback: 'Nao foi possivel carregar os motoristas.',
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
  Future<AdminViagemRecord> createViagem(AdminViagemCreateInput input) async {
    final response = await _client.post(
      Uri.parse('$baseUrl/company-admins/viagens'),
      headers: adminJsonHeaders(_session),
      body: jsonEncode(input.toJson()),
    );

    if (response.statusCode != 201) {
      throwPortalHttpError(
        response,
        fallback:
            'Não foi possível criar a exceção. Abra a viagem existente e use Sincronizar lista; não apague nem recrie a viagem planejada.',
      );
    }

    return AdminViagemRecord.fromJson(
      jsonDecode(decodeAdminPortalBody(response)) as Map<String, dynamic>,
    );
  }

  @override
  Future<AdminViagemBulkCreateResult> generateAutomaticViagens(
    DateTime dataOperacao,
  ) async {
    final response = await _client.post(
      Uri.parse('$baseUrl/company-admins/viagens/gerar-automaticas'),
      headers: adminJsonHeaders(_session),
      body: jsonEncode({'dataOperacao': _formatDateOnly(dataOperacao)}),
    );

    if (response.statusCode != 200) {
      throwPortalHttpError(
        response,
        fallback: 'Nao foi possivel sincronizar as viagens recorrentes do dia.',
      );
    }

    return AdminViagemBulkCreateResult.fromJson(
      jsonDecode(decodeAdminPortalBody(response)) as Map<String, dynamic>,
    );
  }

  @override
  Future<AdminViagemDetailRecord> fetchViagemDetail(int viagemId) async {
    final response = await _client.get(
      Uri.parse('$baseUrl/company-admins/viagens/$viagemId'),
      headers: adminAuthHeaders(_session),
    );

    if (response.statusCode != 200) {
      throwPortalHttpError(
        response,
        fallback: 'Nao foi possivel carregar o detalhe da viagem.',
      );
    }

    return AdminViagemDetailRecord.fromJson(
      jsonDecode(decodeAdminPortalBody(response)) as Map<String, dynamic>,
    );
  }

  @override
  Future<AdminViagemDetailRecord> updateViagemMotorista(
    int viagemId,
    int motoristaId,
  ) async {
    final response = await _client.put(
      Uri.parse('$baseUrl/company-admins/viagens/$viagemId/motorista'),
      headers: adminJsonHeaders(_session),
      body: jsonEncode({'motoristaId': motoristaId}),
    );

    if (response.statusCode != 200) {
      throwPortalHttpError(
        response,
        fallback: 'Nao foi possivel trocar o motorista desta viagem.',
      );
    }

    return AdminViagemDetailRecord.fromJson(
      jsonDecode(decodeAdminPortalBody(response)) as Map<String, dynamic>,
    );
  }

  @override
  Future<AdminViagemManifestPreviewRecord> fetchManifestPreview(
    int viagemId,
  ) async {
    final response = await _client.get(
      Uri.parse('$baseUrl/company-admins/viagens/$viagemId/manifesto-preview'),
      headers: adminAuthHeaders(_session),
    );

    if (response.statusCode != 200) {
      throwPortalHttpError(
        response,
        fallback:
            'Nao foi possivel calcular o impacto da atualizacao da lista.',
      );
    }

    return AdminViagemManifestPreviewRecord.fromJson(
      jsonDecode(decodeAdminPortalBody(response)) as Map<String, dynamic>,
    );
  }

  @override
  Future<AdminViagemDetailRecord> syncManifesto(int viagemId) async {
    final response = await _client.post(
      Uri.parse(
        '$baseUrl/company-admins/viagens/$viagemId/sincronizar-manifesto',
      ),
      headers: adminJsonHeaders(_session),
    );

    if (response.statusCode != 200) {
      throwPortalHttpError(
        response,
        fallback:
            'Não foi possível usar Sincronizar lista nesta viagem planejada. Confira se ela ainda está planejada e vinculada a um serviço.',
      );
    }

    return AdminViagemDetailRecord.fromJson(
      jsonDecode(decodeAdminPortalBody(response)) as Map<String, dynamic>,
    );
  }

  @override
  Future<AdminViagemDetailRecord> cancelViagem(int viagemId) async {
    final response = await _client.post(
      Uri.parse('$baseUrl/company-admins/viagens/$viagemId/cancelar'),
      headers: adminJsonHeaders(_session),
    );

    if (response.statusCode != 200) {
      throwPortalHttpError(
        response,
        fallback: 'Nao foi possivel cancelar esta viagem.',
      );
    }

    return AdminViagemDetailRecord.fromJson(
      jsonDecode(decodeAdminPortalBody(response)) as Map<String, dynamic>,
    );
  }

  @override
  Future<AdminViagemDetailRecord> updateManifestPresence({
    required int viagemId,
    required int servicoId,
    required int passageiroId,
    required DateTime dataOperacao,
    required int? presencaId,
    required String status,
  }) async {
    final response =
        presencaId == null
            ? await _client.post(
              Uri.parse('$baseUrl/presencas-servico'),
              headers: adminJsonHeaders(_session),
              body: jsonEncode({
                'servicoId': servicoId,
                'passageiroId': passageiroId,
                'dataOperacao': _formatDateOnly(dataOperacao),
                'status': status,
              }),
            )
            : await _client.put(
              Uri.parse('$baseUrl/presencas-servico/$presencaId'),
              headers: adminJsonHeaders(_session),
              body: jsonEncode({'status': status}),
            );

    final expectedStatus = presencaId == null ? 201 : 200;
    if (response.statusCode != expectedStatus) {
      throwPortalHttpError(
        response,
        fallback: 'Nao foi possivel atualizar a presenca desta viagem.',
      );
    }

    return fetchViagemDetail(viagemId);
  }
}

String _formatDateOnly(DateTime value) {
  final year = value.year.toString().padLeft(4, '0');
  final month = value.month.toString().padLeft(2, '0');
  final day = value.day.toString().padLeft(2, '0');
  return '$year-$month-$day';
}
