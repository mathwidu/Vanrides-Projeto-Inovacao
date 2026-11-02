import 'dart:convert';

import 'package:http/http.dart' as http;

import '../../../services/api_constants.dart';
import '../../../services/api_response_decoding.dart';
import '../../../services/auth_headers.dart';
import '../../../services/operation_date.dart';
import '../../tracking/domain/trip_tracking_exception.dart';
import '../domain/driver_trip_record.dart';

abstract class DriverTripGateway {
  Future<DriverTripRecord?> fetchTodayTrip(int motoristaId);
  Future<List<DriverTripRecord>> fetchDayTrips(int motoristaId);
  Future<DriverTripRecord> reorderTrip(int tripId, List<int> stopIds);
  Future<DriverTripRecord> startTrip(int tripId);
  Future<DriverTripRecord> completeStop(int tripId, int stopId);
  Future<DriverTripRecord> skipStop(int tripId, int stopId);
  Future<DriverTripRecord> noShowStop(int tripId, int stopId);
  Future<DriverTripRecord> endTrip(int tripId);
}

class DriverTripService implements DriverTripGateway {
  DriverTripService({
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
  Future<DriverTripRecord?> fetchTodayTrip(int motoristaId) async {
    final dataOperacao = _formatOperationDate(_now());
    final response = await _client.get(
      Uri.parse(
        '$baseUrl/viagens/motorista/$motoristaId/hoje?data=$dataOperacao',
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
          fallback: 'Nao foi possivel carregar a viagem do motorista.',
        ),
      );
    }

    return _parseTripResponse(decodeApiResponseBody(response));
  }

  @override
  Future<List<DriverTripRecord>> fetchDayTrips(int motoristaId) async {
    final dataOperacao = _formatOperationDate(_now());
    final response = await _client.get(
      Uri.parse(
        '$baseUrl/viagens/motorista/$motoristaId/dia?data=$dataOperacao',
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
          fallback: 'Nao foi possivel carregar a agenda do motorista.',
        ),
      );
    }

    return _parseTripListResponse(decodeApiResponseBody(response));
  }

  @override
  Future<DriverTripRecord> reorderTrip(int tripId, List<int> stopIds) async {
    final response = await _client.put(
      Uri.parse('$baseUrl/viagens/$tripId/rota'),
      headers: {
        'Authorization': buildAuthorizationHeader(
          accessToken: _accessToken,
          email: _email,
          senha: _senha,
        ),
        'Content-Type': 'application/json',
      },
      body: jsonEncode({'paradaIds': stopIds}),
    );

    if (response.statusCode != 200) {
      throw TripTrackingException(
        _extractMessage(
          decodeApiResponseBody(response),
          fallback: 'Nao foi possivel salvar a rota do dia.',
        ),
      );
    }

    return _parseTripResponse(decodeApiResponseBody(response));
  }

  @override
  Future<DriverTripRecord> startTrip(int tripId) async {
    return _postLifecycleAction(
      '/viagens/$tripId/iniciar',
      fallback: 'Nao foi possivel iniciar a corrida.',
    );
  }

  @override
  Future<DriverTripRecord> completeStop(int tripId, int stopId) async {
    return _postLifecycleAction(
      '/viagens/$tripId/paradas/$stopId/concluir',
      fallback: 'Nao foi possivel concluir a parada atual.',
    );
  }

  @override
  Future<DriverTripRecord> skipStop(int tripId, int stopId) async {
    return _postLifecycleAction(
      '/viagens/$tripId/paradas/$stopId/pular',
      fallback: 'Nao foi possivel pular a parada atual.',
    );
  }

  @override
  Future<DriverTripRecord> noShowStop(int tripId, int stopId) async {
    return _postLifecycleAction(
      '/viagens/$tripId/paradas/$stopId/no-show',
      fallback: 'Nao foi possivel marcar que o passageiro nao embarcou.',
    );
  }

  @override
  Future<DriverTripRecord> endTrip(int tripId) async {
    return _postLifecycleAction(
      '/viagens/$tripId/encerrar',
      fallback: 'Nao foi possivel encerrar a corrida.',
    );
  }

  Future<DriverTripRecord> _postLifecycleAction(
    String path, {
    required String fallback,
  }) async {
    final response = await _client.post(
      Uri.parse('$baseUrl$path'),
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
        _extractMessage(decodeApiResponseBody(response), fallback: fallback),
      );
    }

    return _parseTripResponse(decodeApiResponseBody(response));
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

  DriverTripRecord _parseTripResponse(String body) {
    final decoded = jsonDecode(body);
    if (decoded is! Map<String, dynamic>) {
      throw const TripTrackingException(
        'Resposta inesperada ao carregar a operacao do motorista.',
      );
    }

    return DriverTripRecord.fromJson(decoded);
  }

  List<DriverTripRecord> _parseTripListResponse(String body) {
    final decoded = jsonDecode(body);
    if (decoded is! List<dynamic>) {
      throw const TripTrackingException(
        'Resposta inesperada ao carregar a agenda do motorista.',
      );
    }

    return decoded
        .whereType<Map<String, dynamic>>()
        .map(DriverTripRecord.fromJson)
        .toList(growable: false);
  }

  String _formatOperationDate(DateTime value) {
    return formatVanRidesOperationDate(
      value,
      override: _operationDateOverride,
    );
  }
}
