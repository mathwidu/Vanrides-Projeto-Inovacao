import 'dart:convert';

import 'package:http/http.dart' as http;

import '../../../services/api_constants.dart';
import '../../../services/public_auth_support.dart';

abstract class MotoristaCadastroGateway {
  Future<MotoristaCadastroResult> cadastrar(MotoristaCadastroPayload payload);
}

class HttpMotoristaCadastroGateway implements MotoristaCadastroGateway {
  HttpMotoristaCadastroGateway({http.Client? client})
    : _client = client ?? http.Client();

  final http.Client _client;

  @override
  Future<MotoristaCadastroResult> cadastrar(
    MotoristaCadastroPayload payload,
  ) async {
    try {
      final response = await _client.post(
        Uri.parse('$baseUrl/motoristas'),
        headers: const {'Content-Type': 'application/json'},
        body: jsonEncode(payload.toJson()),
      );
      final responseBody = decodePublicAuthResponseBody(response);

      if (response.statusCode == 200) {
        return MotoristaCadastroOk(
          initialResendCooldown:
              extractInitialEmailVerificationCooldownFromBody(responseBody),
        );
      }

      if (response.statusCode >= 500) {
        return const MotoristaCadastroNetworkError();
      }

      final error = PublicAuthApiError.tryParse(responseBody);
      return MotoristaCadastroValidationError(
        message: error?.message ?? 'Erro: $responseBody',
      );
    } catch (_) {
      return const MotoristaCadastroNetworkError();
    }
  }
}

class MotoristaCadastroPayload {
  const MotoristaCadastroPayload({
    required this.nome,
    required this.email,
    required this.telefone,
    required this.dataNascimento,
    required this.senha,
  });

  final String nome;
  final String email;
  final String telefone;
  final String? dataNascimento;
  final String senha;

  Map<String, dynamic> toJson() {
    return {
      'nome': nome,
      'email': email,
      'telefone': telefone,
      'dataNascimento': dataNascimento,
      'senha': senha,
    };
  }
}

sealed class MotoristaCadastroResult {
  const MotoristaCadastroResult();
}

class MotoristaCadastroOk extends MotoristaCadastroResult {
  const MotoristaCadastroOk({this.initialResendCooldown});

  final Duration? initialResendCooldown;
}

class MotoristaCadastroValidationError extends MotoristaCadastroResult {
  const MotoristaCadastroValidationError({required this.message});

  final String message;
}

class MotoristaCadastroNetworkError extends MotoristaCadastroResult {
  const MotoristaCadastroNetworkError({
    this.message = 'Erro de conexão com o servidor.',
  });

  final String message;
}
