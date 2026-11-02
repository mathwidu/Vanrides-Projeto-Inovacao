import 'dart:convert';

import 'package:http/http.dart' as http;

import '../../../l10n/app_strings.dart';
import '../../../services/api_constants.dart';
import '../../../services/api_response_decoding.dart';
import '../../../services/auth_headers.dart';
import '../../../services/operation_date.dart';
import '../domain/trip_tracking_exception.dart';
import '../domain/trip_tracking_record.dart';

abstract class TripTrackingGateway {
  Future<TripTrackingRecord?> fetchCurrentTrip(int passageiroId);
  Future<List<TripTrackingRecord>> fetchDayTrips(int passageiroId);
}

class TripTrackingService implements TripTrackingGateway {
  TripTrackingService({
    required String email,
    required String senha,
    String? accessToken,
    http.Client? client,
    DateTime Function()? now,
    String? operationDateOverride,
  }) : _email = email,
       _senha = senha,
       _accessToken = accessToken,
       _client = client ?? http.Client(),
       _now = now ?? DateTime.now,
       _operationDateOverride =
           operationDateOverride ?? e2eOperationDateOverride;

  final String _email;
  final String _senha;
  final String? _accessToken;
  final http.Client _client;
  final DateTime Function() _now;
  final String _operationDateOverride;

  @override
  Future<TripTrackingRecord?> fetchCurrentTrip(int passageiroId) async {
    final dataOperacao = _formatOperationDate(_now());
    final response = await _client.get(
      Uri.parse(
        '$baseUrl/viagens/passageiro/$passageiroId/atual?data=$dataOperacao',
      ),
      headers: {
        'Authorization': buildAuthorizationHeader(
          accessToken: _accessToken,
          email: _email,
          senha: _senha,
        ),
      },
    );

    if (response.statusCode == 204) {
      return null;
    }

    if (response.statusCode != 200) {
      throw TripTrackingException(
        _extractMessage(
          decodeApiResponseBody(response),
          fallback: AppStrings.passageiro.naoFoiPossivelCarregarViagemAtual,
        ),
      );
    }

    final decoded = jsonDecode(decodeApiResponseBody(response));
    if (decoded is! Map<String, dynamic>) {
      throw TripTrackingException(
        AppStrings.passageiro.respostaInesperadaRastreio,
      );
    }

    return TripTrackingRecord.fromJson(decoded);
  }

  @override
  Future<List<TripTrackingRecord>> fetchDayTrips(int passageiroId) async {
    final dataOperacao = _formatOperationDate(_now());
    final response = await _client.get(
      Uri.parse(
        '$baseUrl/viagens/passageiro/$passageiroId/dia?data=$dataOperacao',
      ),
      headers: {
        'Authorization': buildAuthorizationHeader(
          accessToken: _accessToken,
          email: _email,
          senha: _senha,
        ),
      },
    );

    if (response.statusCode != 200) {
      throw TripTrackingException(
        _extractMessage(
          decodeApiResponseBody(response),
          fallback: AppStrings.passageiro.naoFoiPossivelCarregarViagemAtual,
        ),
      );
    }

    final decoded = jsonDecode(decodeApiResponseBody(response));
    if (decoded is! List<dynamic>) {
      throw TripTrackingException(
        AppStrings.passageiro.respostaInesperadaRastreio,
      );
    }

    return decoded
        .whereType<Map<String, dynamic>>()
        .map(TripTrackingRecord.fromJson)
        .toList(growable: false);
  }

  String _formatOperationDate(DateTime value) {
    return formatVanRidesOperationDate(
      value,
      override: _operationDateOverride,
    );
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
      // resposta em texto puro
    }

    return trimmed;
  }
}
