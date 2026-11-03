import 'dart:convert';

import 'package:http/http.dart' as http;

import '../../../services/api_constants.dart';
import '../domain/admin_portal_session.dart';
import 'admin_portal_http_support.dart';

abstract class AdminPlacesGateway {
  Future<List<AdminPlacePrediction>> autocomplete(String input);
  Future<AdminPlaceDetails> fetchDetails(String placeId);
}

class AdminPlacesPortalService implements AdminPlacesGateway {
  AdminPlacesPortalService({
    required AdminPortalSession session,
    http.Client? client,
  }) : _session = session,
       _client = client ?? http.Client();

  final AdminPortalSession _session;
  final http.Client _client;

  @override
  Future<List<AdminPlacePrediction>> autocomplete(String input) async {
    final response = await _client.get(
      Uri.parse(
        '$baseUrl/company-admins/places/autocomplete',
      ).replace(queryParameters: {'input': input.trim()}),
      headers: adminAuthHeaders(_session),
    );

    if (response.statusCode != 200) {
      throwPortalHttpError(
        response,
        fallback: 'Nao foi possivel buscar destinos agora.',
      );
    }

    final data = jsonDecode(decodeAdminPortalBody(response)) as List<dynamic>;
    return data
        .map(
          (item) => AdminPlacePrediction.fromJson(item as Map<String, dynamic>),
        )
        .toList(growable: false);
  }

  @override
  Future<AdminPlaceDetails> fetchDetails(String placeId) async {
    final response = await _client.get(
      Uri.parse(
        '$baseUrl/company-admins/places/details',
      ).replace(queryParameters: {'placeId': placeId}),
      headers: adminAuthHeaders(_session),
    );

    if (response.statusCode != 200) {
      throwPortalHttpError(
        response,
        fallback: 'Nao foi possivel carregar o destino selecionado.',
      );
    }

    return AdminPlaceDetails.fromJson(
      jsonDecode(decodeAdminPortalBody(response)) as Map<String, dynamic>,
    );
  }
}

class AdminPlacePrediction {
  const AdminPlacePrediction({
    required this.placeId,
    required this.mainText,
    required this.secondaryText,
  });

  final String placeId;
  final String mainText;
  final String secondaryText;

  factory AdminPlacePrediction.fromJson(Map<String, dynamic> json) {
    return AdminPlacePrediction(
      placeId: json['placeId'] as String,
      mainText: json['mainText'] as String,
      secondaryText: json['secondaryText'] as String? ?? '',
    );
  }
}

class AdminPlaceDetails {
  const AdminPlaceDetails({
    required this.latitude,
    required this.longitude,
    this.endereco,
  });

  final double latitude;
  final double longitude;
  final String? endereco;

  factory AdminPlaceDetails.fromJson(Map<String, dynamic> json) {
    return AdminPlaceDetails(
      latitude: (json['latitude'] as num).toDouble(),
      longitude: (json['longitude'] as num).toDouble(),
      endereco: json['endereco'] as String?,
    );
  }
}
