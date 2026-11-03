import 'dart:convert';

import 'package:http/http.dart' as http;

import '../../../l10n/app_strings.dart';
import '../../../services/api_constants.dart';
import '../../../services/api_response_decoding.dart';
import '../../../services/auth_headers.dart';
import '../domain/guardian_experience_exception.dart';
import '../domain/guardian_presenca_record.dart';
import '../domain/guardian_session.dart';

abstract class GuardianPresencaGateway {
  Future<List<GuardianPresencaRecord>> fetchPresencasByPassageiro(
    int passageiroId,
  );
  Future<void> confirmarPresenca({
    required int passageiroId,
    required DateTime dataPresenca,
  });
  Future<void> removerPresenca(int presencaId);
}

class GuardianPresencaService implements GuardianPresencaGateway {
  GuardianPresencaService({
    required GuardianSession session,
    http.Client? client,
  }) : _session = session,
       _client = client ?? http.Client();

  final GuardianSession _session;
  final http.Client _client;

  @override
  Future<List<GuardianPresencaRecord>> fetchPresencasByPassageiro(
    int passageiroId,
  ) async {
    final response = await _client.get(
      Uri.parse('$baseUrl/presencas/passageiro/$passageiroId'),
      headers: {'Authorization': _authorizationHeader},
    );

    if (response.statusCode != 200) {
      throw GuardianExperienceException(
        _extractMessage(
          decodeApiResponseBody(response),
          fallback: AppStrings.guardian.naoFoiPossivelCarregarPresencas,
        ),
      );
    }

    final data = jsonDecode(decodeApiResponseBody(response)) as List<dynamic>;
    return data
        .map(
          (item) =>
              GuardianPresencaRecord.fromJson(item as Map<String, dynamic>),
        )
        .toList(growable: false);
  }

  @override
  Future<void> confirmarPresenca({
    required int passageiroId,
    required DateTime dataPresenca,
  }) async {
    final response = await _client.post(
      Uri.parse('$baseUrl/presencas'),
      headers: {
        'Authorization': _authorizationHeader,
        'Content-Type': 'application/json',
      },
      body: jsonEncode({
        'passageiro': {'id': passageiroId},
        'dataPresenca': _dateOnly(dataPresenca),
        'confirmado': true,
      }),
    );

    if (response.statusCode != 200) {
      throw GuardianExperienceException(
        _extractMessage(
          decodeApiResponseBody(response),
          fallback: AppStrings.guardian.naoFoiPossivelConfirmarPresenca,
        ),
      );
    }
  }

  @override
  Future<void> removerPresenca(int presencaId) async {
    final response = await _client.delete(
      Uri.parse('$baseUrl/presencas/$presencaId'),
      headers: {'Authorization': _authorizationHeader},
    );

    if (response.statusCode != 204) {
      throw GuardianExperienceException(
        _extractMessage(
          decodeApiResponseBody(response),
          fallback: AppStrings.guardian.naoFoiPossivelRemoverPresenca,
        ),
      );
    }
  }

  String get _authorizationHeader => buildAuthorizationHeader(
    accessToken: _session.accessToken,
    email: _session.email,
    senha: '',
  );

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
}
