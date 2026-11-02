import 'dart:convert';

import 'package:http/http.dart' as http;

import '../../../services/api_constants.dart';
import '../../../services/api_response_decoding.dart';
import '../../../services/auth_headers.dart';
import '../domain/passenger_available_service_record.dart';

abstract class ServicePresenceGateway {
  Future<List<PassengerAvailableServiceRecord>> fetchAvailableServices({
    required int passageiroId,
    required DateTime data,
  });

  Future<void> setPresenceStatus({
    required PassengerAvailableServiceRecord service,
    required int passageiroId,
    required String status,
  });

  Future<void> clearPresence(int presencaId);
}

class ServicePresenceService implements ServicePresenceGateway {
  ServicePresenceService({
    required String email,
    required String senha,
    String? accessToken,
    http.Client? client,
  }) : _email = email,
       _senha = senha,
       _accessToken = accessToken,
       _client = client ?? http.Client();

  final String _email;
  final String _senha;
  final String? _accessToken;
  final http.Client _client;

  @override
  Future<List<PassengerAvailableServiceRecord>> fetchAvailableServices({
    required int passageiroId,
    required DateTime data,
  }) async {
    final uri = Uri.parse(
      '$baseUrl/passageiros/$passageiroId/servicos-disponiveis',
    ).replace(queryParameters: {'data': _dateOnly(data)});

    final response = await _client.get(uri, headers: _authHeaders);

    if (response.statusCode != 200) {
      throw ServicePresenceException(
        _extractMessage(
          decodeApiResponseBody(response),
          fallback: 'Não foi possível carregar os serviços disponíveis.',
        ),
      );
    }

    final dataList =
        jsonDecode(decodeApiResponseBody(response)) as List<dynamic>;
    return dataList
        .map(
          (item) => PassengerAvailableServiceRecord.fromJson(
            item as Map<String, dynamic>,
          ),
        )
        .where((item) => item.servicoId > 0 && item.requiresPresenceDecision)
        .toList(growable: false);
  }

  @override
  Future<void> setPresenceStatus({
    required PassengerAvailableServiceRecord service,
    required int passageiroId,
    required String status,
  }) async {
    final presencaId = service.presencaId;
    final response =
        presencaId == null
            ? await _client.post(
              Uri.parse('$baseUrl/presencas-servico'),
              headers: _jsonHeaders,
              body: jsonEncode({
                'servicoId': service.servicoId,
                'passageiroId': passageiroId,
                'dataOperacao': _dateOnly(service.dataOperacao),
                'status': status,
              }),
            )
            : await _client.put(
              Uri.parse('$baseUrl/presencas-servico/$presencaId'),
              headers: _jsonHeaders,
              body: jsonEncode({'status': status}),
            );

    final expectedStatus = presencaId == null ? 201 : 200;
    if (response.statusCode != expectedStatus) {
      throw ServicePresenceException(
        _extractMessage(
          decodeApiResponseBody(response),
          fallback: 'Não foi possível atualizar a presença do serviço.',
        ),
      );
    }
  }

  @override
  Future<void> clearPresence(int presencaId) async {
    final response = await _client.delete(
      Uri.parse('$baseUrl/presencas-servico/$presencaId'),
      headers: _authHeaders,
    );

    if (response.statusCode != 204) {
      throw ServicePresenceException(
        _extractMessage(
          decodeApiResponseBody(response),
          fallback: 'Não foi possível limpar a decisão de presença.',
        ),
      );
    }
  }

  Map<String, String> get _authHeaders => {
    'Authorization': buildAuthorizationHeader(
      accessToken: _accessToken,
      email: _email,
      senha: _senha,
    ),
  };

  Map<String, String> get _jsonHeaders => {
    ..._authHeaders,
    'Content-Type': 'application/json',
  };
}

class ServicePresenceException implements Exception {
  const ServicePresenceException(this.message);

  final String message;

  @override
  String toString() => message;
}

String _dateOnly(DateTime date) {
  final year = date.year.toString().padLeft(4, '0');
  final month = date.month.toString().padLeft(2, '0');
  final day = date.day.toString().padLeft(2, '0');
  return '$year-$month-$day';
}

String _extractMessage(String body, {required String fallback}) {
  final trimmed = body.trim();
  if (trimmed.isEmpty) {
    return fallback;
  }

  try {
    final decoded = jsonDecode(trimmed);
    if (decoded is Map<String, dynamic>) {
      for (final key in const ['message', 'error', 'mensagem']) {
        final value = decoded[key];
        if (value is String && value.trim().isNotEmpty) {
          return value.trim();
        }
      }
    }
  } catch (_) {
    // fallback to plain text
  }

  return trimmed;
}
