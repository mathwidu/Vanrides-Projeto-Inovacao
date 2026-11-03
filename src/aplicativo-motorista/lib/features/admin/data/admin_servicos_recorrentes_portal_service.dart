import 'dart:convert';

import 'package:http/http.dart' as http;

import '../../../services/api_constants.dart';
import '../domain/admin_motorista_record.dart';
import '../domain/admin_passageiro_record.dart';
import '../domain/admin_portal_session.dart';
import '../domain/admin_servico_recorrente_input.dart';
import '../domain/admin_servico_recorrente_record.dart';
import 'admin_portal_http_support.dart';

abstract class AdminServicosRecorrentesGateway {
  Future<List<AdminServicoRecorrenteRecord>> fetchServicos();
  Future<List<AdminMotoristaRecord>> fetchMotoristas();
  Future<List<AdminPassageiroRecord>> fetchPassageiros();
  Future<AdminServicoRecorrenteRecord> createServico(
    AdminServicoRecorrenteInput input,
  );
  Future<AdminServicoRecorrenteRecord> editServico(
    int servicoId,
    AdminServicoRecorrenteInput input,
  );
  Future<void> deleteServico(int servicoId);
}

class AdminServicosRecorrentesPortalService
    implements AdminServicosRecorrentesGateway {
  AdminServicosRecorrentesPortalService({
    required AdminPortalSession session,
    http.Client? client,
  }) : _session = session,
       _client = client ?? http.Client();

  final AdminPortalSession _session;
  final http.Client _client;

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
  Future<AdminServicoRecorrenteRecord> createServico(
    AdminServicoRecorrenteInput input,
  ) async {
    final response = await _client.post(
      Uri.parse('$baseUrl/company-admins/servicos-recorrentes'),
      headers: adminJsonHeaders(_session),
      body: jsonEncode(input.toJson()),
    );

    if (response.statusCode != 201) {
      throwPortalHttpError(
        response,
        fallback: 'Nao foi possivel criar o servico recorrente agora.',
      );
    }

    return AdminServicoRecorrenteRecord.fromJson(
      jsonDecode(decodeAdminPortalBody(response)) as Map<String, dynamic>,
    );
  }

  @override
  Future<AdminServicoRecorrenteRecord> editServico(
    int servicoId,
    AdminServicoRecorrenteInput input,
  ) async {
    final response = await _client.put(
      Uri.parse('$baseUrl/company-admins/servicos-recorrentes/$servicoId'),
      headers: adminJsonHeaders(_session),
      body: jsonEncode(input.toJson()),
    );

    if (response.statusCode != 200) {
      throwPortalHttpError(
        response,
        fallback: 'Nao foi possivel atualizar o servico recorrente agora.',
      );
    }

    return AdminServicoRecorrenteRecord.fromJson(
      jsonDecode(decodeAdminPortalBody(response)) as Map<String, dynamic>,
    );
  }

  @override
  Future<void> deleteServico(int servicoId) async {
    final response = await _client.delete(
      Uri.parse('$baseUrl/company-admins/servicos-recorrentes/$servicoId'),
      headers: adminAuthHeaders(_session),
    );

    if (response.statusCode != 204) {
      throwPortalHttpError(
        response,
        fallback: 'Nao foi possivel inativar o servico recorrente agora.',
      );
    }
  }
}
