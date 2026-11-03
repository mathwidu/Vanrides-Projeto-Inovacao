import 'dart:convert';

import 'package:http/http.dart' as http;

import '../../../l10n/app_strings.dart';
import '../../../services/api_constants.dart';
import '../../../services/api_response_decoding.dart';
import '../../../services/auth_headers.dart';
import '../domain/guardian_experience_exception.dart';
import '../domain/guardian_linked_passageiro_record.dart';
import '../domain/guardian_session.dart';

abstract class GuardianPassageiroGateway {
  Future<List<GuardianLinkedPassageiroRecord>> fetchLinkedPassageiros();
}

class GuardianPassageiroService implements GuardianPassageiroGateway {
  GuardianPassageiroService({
    required GuardianSession session,
    http.Client? client,
  }) : _session = session,
       _client = client ?? http.Client();

  final GuardianSession _session;
  final http.Client _client;

  @override
  Future<List<GuardianLinkedPassageiroRecord>> fetchLinkedPassageiros() async {
    final response = await _client.get(
      Uri.parse('$baseUrl/passageiros'),
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
              AppStrings
                  .guardian
                  .naoFoiPossivelCarregarPassageirosDoResponsavel,
        ),
      );
    }

    final data = jsonDecode(decodeApiResponseBody(response)) as List<dynamic>;
    return data
        .map(
          (item) => GuardianLinkedPassageiroRecord.fromJson(
            item as Map<String, dynamic>,
          ),
        )
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
