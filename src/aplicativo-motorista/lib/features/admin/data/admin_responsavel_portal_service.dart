import 'dart:convert';

import 'package:http/http.dart' as http;

import '../../../services/api_constants.dart';
import '../domain/admin_portal_session.dart';
import '../domain/admin_responsavel_create_input.dart';
import '../domain/admin_responsavel_record.dart';
import '../domain/admin_responsavel_update_input.dart';
import 'admin_portal_http_support.dart';

abstract class AdminResponsavelGateway {
  Future<List<AdminResponsavelRecord>> fetchResponsaveis();
  Future<AdminResponsavelRecord> fetchResponsavelDetail(int responsavelId);
  Future<AdminResponsavelRecord> createResponsavel(
    AdminResponsavelCreateInput input,
  );
  Future<AdminResponsavelRecord> updateResponsavel(
    int responsavelId,
    AdminResponsavelUpdateInput input,
  );
  Future<void> deleteResponsavel(int responsavelId);
}

class AdminResponsavelPortalService implements AdminResponsavelGateway {
  AdminResponsavelPortalService({
    required AdminPortalSession session,
    http.Client? client,
  }) : _session = session,
       _client = client ?? http.Client();

  final AdminPortalSession _session;
  final http.Client _client;

  @override
  Future<List<AdminResponsavelRecord>> fetchResponsaveis() async {
    final response = await _client.get(
      Uri.parse('$baseUrl/company-admins/responsaveis'),
      headers: adminAuthHeaders(_session),
    );

    if (response.statusCode != 200) {
      throwPortalHttpError(
        response,
        fallback: 'Nao foi possivel carregar os responsaveis da empresa.',
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
  Future<AdminResponsavelRecord> fetchResponsavelDetail(
    int responsavelId,
  ) async {
    final response = await _client.get(
      Uri.parse('$baseUrl/company-admins/responsaveis/$responsavelId'),
      headers: adminAuthHeaders(_session),
    );

    if (response.statusCode != 200) {
      throwPortalHttpError(
        response,
        fallback: 'Nao foi possivel carregar o detalhe do responsavel.',
      );
    }

    return AdminResponsavelRecord.fromJson(
      jsonDecode(decodeAdminPortalBody(response)) as Map<String, dynamic>,
    );
  }

  @override
  Future<AdminResponsavelRecord> createResponsavel(
    AdminResponsavelCreateInput input,
  ) async {
    final response = await _client.post(
      Uri.parse('$baseUrl/company-admins/responsaveis'),
      headers: adminJsonHeaders(_session),
      body: jsonEncode(input.toJson()),
    );

    if (response.statusCode != 201) {
      throwPortalHttpError(
        response,
        fallback: 'Nao foi possivel criar o responsavel agora.',
      );
    }

    return AdminResponsavelRecord.fromJson(
      jsonDecode(decodeAdminPortalBody(response)) as Map<String, dynamic>,
    );
  }

  @override
  Future<AdminResponsavelRecord> updateResponsavel(
    int responsavelId,
    AdminResponsavelUpdateInput input,
  ) async {
    final response = await _client.put(
      Uri.parse('$baseUrl/company-admins/responsaveis/$responsavelId'),
      headers: adminJsonHeaders(_session),
      body: jsonEncode(input.toJson()),
    );

    if (response.statusCode != 200) {
      throwPortalHttpError(
        response,
        fallback: 'Nao foi possivel atualizar o responsavel agora.',
      );
    }

    return AdminResponsavelRecord.fromJson(
      jsonDecode(decodeAdminPortalBody(response)) as Map<String, dynamic>,
    );
  }

  @override
  Future<void> deleteResponsavel(int responsavelId) async {
    final response = await _client.delete(
      Uri.parse('$baseUrl/company-admins/responsaveis/$responsavelId'),
      headers: adminAuthHeaders(_session),
    );

    if (response.statusCode != 204) {
      throwPortalHttpError(
        response,
        fallback: 'Nao foi possivel remover o responsavel agora.',
      );
    }
  }
}
