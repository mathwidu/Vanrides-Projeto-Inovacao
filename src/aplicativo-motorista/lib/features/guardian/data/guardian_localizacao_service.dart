import 'dart:convert';

import 'package:http/http.dart' as http;

import '../../../l10n/app_strings.dart';
import '../../../services/api_constants.dart';
import '../../../services/api_response_decoding.dart';
import '../../../services/auth_headers.dart';
import '../domain/guardian_experience_exception.dart';
import '../domain/guardian_localizacao_snapshot.dart';
import '../domain/guardian_session.dart';

abstract class GuardianLocalizacaoGateway {
  Future<List<GuardianLocalizacaoSnapshot>> fetchHistoricoPorMotorista(
    int motoristaId,
  );
}

class GuardianLocalizacaoService implements GuardianLocalizacaoGateway {
  GuardianLocalizacaoService({
    required GuardianSession session,
    http.Client? client,
  }) : _session = session,
       _client = client ?? http.Client();

  final GuardianSession _session;
  final http.Client _client;

  @override
  Future<List<GuardianLocalizacaoSnapshot>> fetchHistoricoPorMotorista(
    int motoristaId,
  ) async {
    final response = await _client.get(
      Uri.parse('$baseUrl/api/localizacao/$motoristaId/historico'),
      headers: {
        'Authorization': buildAuthorizationHeader(
          accessToken: _session.accessToken,
          email: _session.email,
          senha: '',
        ),
      },
    );

    if (response.statusCode != 200) {
      throw GuardianExperienceException(
        _extractMessage(
          decodeApiResponseBody(response),
          fallback:
              AppStrings.guardian.naoFoiPossivelCarregarLocalizacaoMotorista,
        ),
      );
    }

    final decoded = jsonDecode(decodeApiResponseBody(response));
    if (decoded is! List) {
      throw GuardianExperienceException(
        AppStrings.guardian.respostaInesperadaHistorico,
      );
    }

    return decoded
        .whereType<Map<String, dynamic>>()
        .map(GuardianLocalizacaoSnapshot.fromJson)
        .toList(growable: false);
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
      // texto puro
    }

    return trimmed;
  }
}
