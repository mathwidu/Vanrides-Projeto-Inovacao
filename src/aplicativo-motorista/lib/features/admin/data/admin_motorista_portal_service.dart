import 'dart:convert';

import 'package:http/http.dart' as http;

import '../../../services/api_constants.dart';
import '../domain/admin_motorista_create_input.dart';
import '../domain/admin_motorista_record.dart';
import '../domain/admin_portal_session.dart';
import 'admin_portal_http_support.dart';

abstract class AdminMotoristaGateway {
  Future<List<AdminMotoristaRecord>> fetchMotoristas();
  Future<AdminMotoristaRecord> createMotorista(AdminMotoristaCreateInput input);
  Future<AdminMotoristaRecord> editMotorista(
    int motoristaId, {
    required String nome,
    required String email,
    required String telefone,
  });
  Future<void> deleteMotorista(int motoristaId);
}

class AdminMotoristaPortalService implements AdminMotoristaGateway {
  AdminMotoristaPortalService({
    required AdminPortalSession session,
    http.Client? client,
  }) : _session = session,
       _client = client ?? http.Client();

  final AdminPortalSession _session;
  final http.Client _client;

  @override
  Future<List<AdminMotoristaRecord>> fetchMotoristas() async {
    final response = await _client.get(
      Uri.parse('$baseUrl/company-admins/motoristas'),
      headers: adminAuthHeaders(_session),
    );

    if (response.statusCode != 200) {
      throw AdminMotoristaPortalException(
        extractAdminPortalMessage(
          decodeAdminPortalBody(response),
          fallback: 'Nao foi possivel carregar os motoristas agora.',
        ),
      );
    }

    final data = jsonDecode(decodeAdminPortalBody(response)) as List<dynamic>;
    return data
        .map((item) => AdminMotoristaRecord.fromJson(item as Map<String, dynamic>))
        .toList(growable: false);
  }

  @override
  Future<AdminMotoristaRecord> createMotorista(
    AdminMotoristaCreateInput input,
  ) async {
    final response = await _client.post(
      Uri.parse('$baseUrl/company-admins/motoristas'),
      headers: adminJsonHeaders(_session),
      body: jsonEncode(input.toJson()),
    );

    if (response.statusCode != 201) {
      throw AdminMotoristaPortalException(
        extractAdminPortalMessage(
          decodeAdminPortalBody(response),
          fallback: 'Nao foi possivel criar o motorista agora.',
        ),
      );
    }

    final data = jsonDecode(decodeAdminPortalBody(response))
        as Map<String, dynamic>;
    return AdminMotoristaRecord.fromJson(data);
  }

  @override
  Future<AdminMotoristaRecord> editMotorista(
    int motoristaId, {
    required String nome,
    required String email,
    required String telefone,
  }) async {
    final response = await _client.put(
      Uri.parse('$baseUrl/company-admins/motoristas/$motoristaId'),
      headers: adminJsonHeaders(_session),
      body: jsonEncode({'nome': nome, 'email': email, 'telefone': telefone}),
    );

    if (response.statusCode != 200) {
      throw AdminMotoristaPortalException(
        extractAdminPortalMessage(
          decodeAdminPortalBody(response),
          fallback: 'Nao foi possivel atualizar o motorista agora.',
        ),
      );
    }

    return AdminMotoristaRecord.fromJson(
      jsonDecode(decodeAdminPortalBody(response)) as Map<String, dynamic>,
    );
  }

  @override
  Future<void> deleteMotorista(int motoristaId) async {
    final response = await _client.delete(
      Uri.parse('$baseUrl/company-admins/motoristas/$motoristaId'),
      headers: adminAuthHeaders(_session),
    );

    if (response.statusCode != 204) {
      throw AdminMotoristaPortalException(
        extractAdminPortalMessage(
          decodeAdminPortalBody(response),
          fallback: 'Nao foi possivel excluir o motorista agora.',
        ),
      );
    }
  }
}

class AdminMotoristaPortalException implements Exception {
  const AdminMotoristaPortalException(this.message);

  final String message;

  @override
  String toString() => message;
}
