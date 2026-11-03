import 'dart:convert';

import 'package:http/http.dart' as http;

import '../../../l10n/app_strings.dart';
import '../../../services/api_constants.dart';
import '../../../services/api_response_decoding.dart';
import '../../../services/auth_headers.dart';
import '../domain/trip_live_location_record.dart';

abstract class TripLiveLocationGateway {
  Future<TripLiveLocationRecord?> fetchLatestLocation(int motoristaId);
}

class TripLiveLocationService implements TripLiveLocationGateway {
  TripLiveLocationService({
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
  Future<TripLiveLocationRecord?> fetchLatestLocation(int motoristaId) async {
    final response = await _client.get(
      Uri.parse('$baseUrl/api/localizacao/$motoristaId'),
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
      throw TripLiveLocationException(
        AppStrings.passageiro.naoFoiPossivelCarregarLocalizacaoMotorista,
      );
    }

    final decoded = jsonDecode(decodeApiResponseBody(response));
    if (decoded is! Map<String, dynamic>) {
      throw TripLiveLocationException(
        AppStrings.passageiro.respostaInesperadaLocalizacaoMotorista,
      );
    }

    return TripLiveLocationRecord.fromJson(decoded);
  }
}

class TripLiveLocationException implements Exception {
  const TripLiveLocationException(this.message);

  final String message;
}
