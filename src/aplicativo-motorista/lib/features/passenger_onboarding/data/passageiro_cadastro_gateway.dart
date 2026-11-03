import 'dart:convert';

import 'package:google_place/google_place.dart';
import 'package:http/http.dart' as http;

import '../../../services/api_constants.dart';
import '../../../services/google_api_config.dart';
import '../../../services/public_auth_support.dart';

abstract class PassageiroCadastroGateway {
  Future<PassageiroCadastroResult> cadastrar(PassageiroCadastroPayload payload);
}

class HttpPassageiroCadastroGateway implements PassageiroCadastroGateway {
  HttpPassageiroCadastroGateway({http.Client? client})
    : _client = client ?? http.Client();

  final http.Client _client;

  @override
  Future<PassageiroCadastroResult> cadastrar(
    PassageiroCadastroPayload payload,
  ) async {
    try {
      final response = await _client.post(
        Uri.parse('$baseUrl/passageiros'),
        headers: const {'Content-Type': 'application/json'},
        body: jsonEncode(payload.toJson()),
      );
      final responseBody = decodePublicAuthResponseBody(response);

      if (response.statusCode == 200) {
        return PassageiroCadastroOk(
          initialResendCooldown:
              extractInitialEmailVerificationCooldownFromBody(responseBody),
        );
      }

      if (response.statusCode >= 500) {
        return const PassageiroCadastroNetworkError();
      }

      final error = PublicAuthApiError.tryParse(responseBody);
      return PassageiroCadastroValidationError(
        message: error?.message ?? 'Erro: $responseBody',
      );
    } catch (_) {
      return const PassageiroCadastroNetworkError();
    }
  }
}

class PassageiroCadastroPayload {
  const PassageiroCadastroPayload({
    required this.nome,
    required this.email,
    required this.telefone,
    required this.dataNascimento,
    required this.senha,
    required this.endereco,
    required this.bairro,
    required this.cidade,
    required this.estado,
    required this.cep,
    required this.latitude,
    required this.longitude,
  });

  final String nome;
  final String email;
  final String telefone;
  final String? dataNascimento;
  final String senha;
  final String endereco;
  final String bairro;
  final String cidade;
  final String estado;
  final String cep;
  final double latitude;
  final double longitude;

  Map<String, dynamic> toJson() {
    return {
      'nome': nome,
      'email': email,
      'telefone': telefone,
      'dataNascimento': dataNascimento,
      'senha': senha,
      'endereco': endereco,
      'bairro': bairro,
      'cidade': cidade,
      'estado': estado,
      'cep': cep,
      'latitude': latitude,
      'longitude': longitude,
    };
  }
}

sealed class PassageiroCadastroResult {
  const PassageiroCadastroResult();
}

class PassageiroCadastroOk extends PassageiroCadastroResult {
  const PassageiroCadastroOk({this.initialResendCooldown});

  final Duration? initialResendCooldown;
}

class PassageiroCadastroValidationError extends PassageiroCadastroResult {
  const PassageiroCadastroValidationError({required this.message});

  final String message;
}

class PassageiroCadastroNetworkError extends PassageiroCadastroResult {
  const PassageiroCadastroNetworkError({
    this.message = 'Erro de conexão com o servidor.',
  });

  final String message;
}

abstract class PlacesAutocompleteGateway {
  bool get isAvailable;
  Future<List<PassageiroPlacePrediction>> autocomplete(String input);
  Future<PassageiroPlaceDetails?> fetchDetails(String placeId);
}

class GooglePlacesAutocompleteGateway implements PlacesAutocompleteGateway {
  GooglePlacesAutocompleteGateway({String apiKey = vanridesGooglePlacesApiKey})
    : _googlePlace = apiKey.trim().isEmpty ? null : GooglePlace(apiKey.trim());

  final GooglePlace? _googlePlace;

  @override
  bool get isAvailable => _googlePlace != null;

  @override
  Future<List<PassageiroPlacePrediction>> autocomplete(String input) async {
    if (_googlePlace == null || input.trim().isEmpty) {
      return const [];
    }

    try {
      final result = await _googlePlace.autocomplete.get(
        input.trim(),
        language: 'pt',
        components: [Component('country', 'br')],
      );

      return (result?.predictions ?? const <AutocompletePrediction>[])
          .where((prediction) => prediction.placeId?.isNotEmpty == true)
          .map(
            (prediction) => PassageiroPlacePrediction(
              placeId: prediction.placeId!,
              description: prediction.description ?? '',
            ),
          )
          .toList(growable: false);
    } catch (_) {
      throw const PlacesAutocompleteException(
        'Não foi possível buscar endereços agora.',
      );
    }
  }

  @override
  Future<PassageiroPlaceDetails?> fetchDetails(String placeId) async {
    if (_googlePlace == null || placeId.trim().isEmpty) {
      return null;
    }

    try {
      final details = await _googlePlace.details.get(placeId.trim());
      final result = details?.result;
      final location = result?.geometry?.location;
      if (location == null) {
        return null;
      }

      final components = result?.addressComponents;
      return PassageiroPlaceDetails(
        latitude: location.lat ?? 0,
        longitude: location.lng ?? 0,
        endereco:
            '${_addressComponent(components, ['route']) ?? ''}, '
                    '${_addressComponent(components, ['street_number']) ?? ''}'
                .trim(),
        bairro:
            _addressComponent(components, [
              'sublocality_level_1',
              'sublocality',
            ]) ??
            '',
        cidade:
            _addressComponent(components, ['locality']) ??
            _addressComponent(components, ['administrative_area_level_2']) ??
            '',
        estado:
            _addressComponent(components, ['administrative_area_level_1']) ??
            '',
        cep: _addressComponent(components, ['postal_code']) ?? '',
      );
    } catch (_) {
      throw const PlacesAutocompleteException(
        'Não foi possível carregar o endereço selecionado.',
      );
    }
  }

  String? _addressComponent(
    List<AddressComponent>? components,
    List<String> types,
  ) {
    for (final component in components ?? const <AddressComponent>[]) {
      final componentTypes = component.types ?? const <String>[];
      if (types.any(componentTypes.contains)) {
        return component.longName;
      }
    }
    return null;
  }
}

class PassageiroPlacePrediction {
  const PassageiroPlacePrediction({
    required this.placeId,
    required this.description,
  });

  final String placeId;
  final String description;
}

class PassageiroPlaceDetails {
  const PassageiroPlaceDetails({
    required this.latitude,
    required this.longitude,
    required this.endereco,
    required this.bairro,
    required this.cidade,
    required this.estado,
    required this.cep,
  });

  final double latitude;
  final double longitude;
  final String endereco;
  final String bairro;
  final String cidade;
  final String estado;
  final String cep;
}

class PlacesAutocompleteException implements Exception {
  const PlacesAutocompleteException(this.message);

  final String message;
}
