import 'dart:convert';

import 'package:http/http.dart' as http;

import '../../../services/api_constants.dart';
import '../domain/admin_motorista_record.dart';
import '../domain/admin_passageiro_record.dart';
import '../domain/admin_portal_session.dart';
import '../domain/admin_rota_base_create_input.dart';
import '../domain/admin_rota_base_record.dart';
import 'admin_portal_http_support.dart';

abstract class AdminRotasGateway {
  Future<List<AdminRotaBaseRecord>> fetchRotas();
  Future<List<AdminMotoristaRecord>> fetchMotoristas();
  Future<List<AdminPassageiroRecord>> fetchPassageiros();
  Future<AdminRotaBaseRecord> createRota(AdminRotaBaseCreateInput input);
  Future<AdminRotaBaseRecord> editRota(int rotaId, AdminRotaBaseCreateInput input);
  Future<void> deleteRota(int rotaId);
}

class AdminRotasPortalService implements AdminRotasGateway {
  AdminRotasPortalService({
    required AdminPortalSession session,
    http.Client? client,
  }) : _session = session,
       _client = client ?? http.Client();

  final AdminPortalSession _session;
  final http.Client _client;

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
  Future<List<AdminPassageiroRecord>> fetchPassageiros() async {
    final response = await _client.get(
      Uri.parse('$baseUrl/company-admins/passageiros'),
      headers: adminAuthHeaders(_session),
    );

    if (response.statusCode != 200) {
      throwPortalHttpError(
        response,
        fallback: 'Nao foi possivel carregar os passageiros.',
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
  Future<AdminRotaBaseRecord> createRota(AdminRotaBaseCreateInput input) async {
    final response = await _client.post(
      Uri.parse('$baseUrl/company-admins/rotas-base'),
      headers: adminJsonHeaders(_session),
      body: jsonEncode(input.toJson()),
    );

    if (response.statusCode != 201) {
      throwPortalHttpError(
        response,
        fallback: 'Nao foi possivel criar a rota-base agora.',
      );
    }

    return AdminRotaBaseRecord.fromJson(
      jsonDecode(decodeAdminPortalBody(response)) as Map<String, dynamic>,
    );
  }

  @override
  Future<AdminRotaBaseRecord> editRota(
    int rotaId,
    AdminRotaBaseCreateInput input,
  ) async {
    final response = await _client.put(
      Uri.parse('$baseUrl/company-admins/rotas-base/$rotaId'),
      headers: adminJsonHeaders(_session),
      body: jsonEncode(input.toJson()),
    );

    if (response.statusCode != 200) {
      throwPortalHttpError(
        response,
        fallback: 'Nao foi possivel atualizar a rota-base agora.',
      );
    }

    return AdminRotaBaseRecord.fromJson(
      jsonDecode(decodeAdminPortalBody(response)) as Map<String, dynamic>,
    );
  }

  @override
  Future<void> deleteRota(int rotaId) async {
    final response = await _client.delete(
      Uri.parse('$baseUrl/company-admins/rotas-base/$rotaId'),
      headers: adminAuthHeaders(_session),
    );

    if (response.statusCode != 204) {
      throwPortalHttpError(
        response,
        fallback: 'Nao foi possivel excluir a rota-base agora.',
      );
    }
  }
}
