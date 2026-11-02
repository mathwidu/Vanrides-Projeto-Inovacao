import 'dart:convert';

import 'package:http/http.dart' as http;

import '../../../services/api_constants.dart';
import '../domain/admin_motorista_record.dart';
import '../domain/admin_passageiro_create_input.dart';
import '../domain/admin_passageiro_record.dart';
import '../domain/admin_passageiro_servico_record.dart';
import '../domain/admin_passageiro_update_input.dart';
import '../domain/admin_portal_session.dart';
import '../domain/admin_servico_recorrente_record.dart';
import '../../service_presence/domain/passenger_available_service_record.dart';
import 'admin_portal_http_support.dart';

abstract class AdminPassageiroGateway {
  Future<List<AdminMotoristaRecord>> fetchMotoristas();
  Future<List<AdminServicoRecorrenteRecord>> fetchServicos();
  Future<List<AdminPassageiroRecord>> fetchPassageiros();
  Future<AdminPassageiroRecord> fetchPassageiroDetail(int passageiroId);
  Future<List<PassengerAvailableServiceRecord>> fetchAvailableServices({
    required int passageiroId,
    required DateTime data,
  });
  Future<AdminPassageiroRecord> createPassageiro(
    AdminPassageiroCreateInput input,
  );
  Future<AdminPassageiroRecord> updatePassageiro(
    int passageiroId,
    AdminPassageiroUpdateInput input,
  );
  Future<AdminPassageiroRecord> reassignMotorista(
    int passageiroId,
    int motoristaId,
  );
  Future<List<AdminPassageiroServicoRecord>> updatePassageiroServicos({
    required int passageiroId,
    required List<int> servicoIds,
  });
}

class AdminPassageiroPortalService implements AdminPassageiroGateway {
  AdminPassageiroPortalService({
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
      throwPortalHttpError(
        response,
        fallback: 'Nao foi possivel carregar os motoristas para o portal.',
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
  Future<List<AdminPassageiroRecord>> fetchPassageiros() async {
    final response = await _client.get(
      Uri.parse('$baseUrl/company-admins/passageiros'),
      headers: adminAuthHeaders(_session),
    );

    if (response.statusCode != 200) {
      throwPortalHttpError(
        response,
        fallback: 'Nao foi possivel carregar os passageiros da empresa.',
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
  Future<AdminPassageiroRecord> fetchPassageiroDetail(int passageiroId) async {
    final response = await _client.get(
      Uri.parse('$baseUrl/company-admins/passageiros/$passageiroId'),
      headers: adminAuthHeaders(_session),
    );

    if (response.statusCode != 200) {
      throwPortalHttpError(
        response,
        fallback: 'Nao foi possivel carregar o detalhe do passageiro.',
      );
    }

    return AdminPassageiroRecord.fromJson(
      jsonDecode(decodeAdminPortalBody(response)) as Map<String, dynamic>,
    );
  }

  @override
  Future<List<PassengerAvailableServiceRecord>> fetchAvailableServices({
    required int passageiroId,
    required DateTime data,
  }) async {
    final uri = Uri.parse(
      '$baseUrl/passageiros/$passageiroId/servicos-disponiveis',
    ).replace(queryParameters: {'data': _dateOnly(data)});

    final response = await _client.get(
      uri,
      headers: adminAuthHeaders(_session),
    );

    if (response.statusCode != 200) {
      throwPortalHttpError(
        response,
        fallback: 'Nao foi possivel carregar as presencas de hoje.',
      );
    }

    final dataList =
        jsonDecode(decodeAdminPortalBody(response)) as List<dynamic>;
    return dataList
        .map(
          (item) => PassengerAvailableServiceRecord.fromJson(
            item as Map<String, dynamic>,
          ),
        )
        .where((item) => item.servicoId > 0)
        .toList(growable: false);
  }

  @override
  Future<AdminPassageiroRecord> createPassageiro(
    AdminPassageiroCreateInput input,
  ) async {
    final response = await _client.post(
      Uri.parse('$baseUrl/company-admins/passageiros'),
      headers: adminJsonHeaders(_session),
      body: jsonEncode(input.toJson()),
    );

    if (response.statusCode != 201) {
      throwPortalHttpError(
        response,
        fallback: 'Nao foi possivel criar o passageiro agora.',
      );
    }

    return AdminPassageiroRecord.fromJson(
      jsonDecode(decodeAdminPortalBody(response)) as Map<String, dynamic>,
    );
  }

  @override
  Future<AdminPassageiroRecord> updatePassageiro(
    int passageiroId,
    AdminPassageiroUpdateInput input,
  ) async {
    final response = await _client.put(
      Uri.parse('$baseUrl/company-admins/passageiros/$passageiroId'),
      headers: adminJsonHeaders(_session),
      body: jsonEncode(input.toJson()),
    );

    if (response.statusCode != 200) {
      throwPortalHttpError(
        response,
        fallback: 'Nao foi possivel atualizar o passageiro agora.',
      );
    }

    return AdminPassageiroRecord.fromJson(
      jsonDecode(decodeAdminPortalBody(response)) as Map<String, dynamic>,
    );
  }

  @override
  Future<AdminPassageiroRecord> reassignMotorista(
    int passageiroId,
    int motoristaId,
  ) async {
    final response = await _client.put(
      Uri.parse('$baseUrl/company-admins/passageiros/$passageiroId/motorista'),
      headers: adminJsonHeaders(_session),
      body: jsonEncode({'motoristaId': motoristaId}),
    );

    if (response.statusCode != 200) {
      throwPortalHttpError(
        response,
        fallback: 'Nao foi possivel reatribuir o motorista agora.',
      );
    }

    return AdminPassageiroRecord.fromJson(
      jsonDecode(decodeAdminPortalBody(response)) as Map<String, dynamic>,
    );
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
}

String _dateOnly(DateTime value) =>
    '${value.year.toString().padLeft(4, '0')}-'
    '${value.month.toString().padLeft(2, '0')}-'
    '${value.day.toString().padLeft(2, '0')}';
