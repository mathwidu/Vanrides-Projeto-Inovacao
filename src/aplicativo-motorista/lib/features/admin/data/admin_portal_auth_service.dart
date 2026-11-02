import 'dart:convert';

import 'package:http/http.dart' as http;

import '../../../services/api_constants.dart';
import '../../../services/jwt_claims.dart';
import '../domain/admin_company_admin_bootstrap_input.dart';
import '../domain/admin_company_admin_bootstrap_result.dart';
import '../domain/admin_portal_exception.dart';
import '../domain/admin_portal_session.dart';
import 'admin_portal_http_support.dart';

abstract class AdminPortalAuthGateway {
  Future<AdminPortalSession> login({
    required String email,
    required String senha,
  });
  Future<AdminCompanyAdminBootstrapResult> bootstrap(
    AdminCompanyAdminBootstrapInput input,
  );
  Future<String> resendVerification({required String email});
  Future<String> changePendingEmail({
    required String currentEmail,
    required String senha,
    required String newEmail,
  });
}

class AdminPortalAuthService implements AdminPortalAuthGateway {
  AdminPortalAuthService({http.Client? client})
    : _client = client ?? http.Client();

  final http.Client _client;

  @override
  Future<AdminPortalSession> login({
    required String email,
    required String senha,
  }) async {
    final normalizedEmail = email.trim().toLowerCase();
    final response = await _client.post(
      Uri.parse('$baseUrl/login'),
      headers: const {'Content-Type': 'application/json'},
      body: jsonEncode({'email': normalizedEmail, 'senha': senha}),
    );
    final responseBody = decodeAdminPortalBody(response);

    if (response.statusCode != 200) {
      final error = extractAdminPortalError(
        responseBody,
        fallback: 'Nao foi possivel entrar no portal agora.',
      );
      throw AdminPortalException(
        error.message,
        code: error.code,
        email: error.email ?? normalizedEmail,
      );
    }

    final data = jsonDecode(responseBody) as Map<String, dynamic>;
    final tipoUsuario = (data['tipoUsuario'] as String?)?.trim().toLowerCase();
    if (tipoUsuario != 'admin') {
      throw const AdminPortalException(
        'Este login não pertence a um administrador da empresa.',
      );
    }

    final accessToken = data['accessToken'] as String?;
    final companyId = extractCompanyIdFromAccessToken(accessToken);
    if (accessToken == null || accessToken.isEmpty || companyId == null) {
      throw const AdminPortalException(
        'Não encontramos uma empresa vinculada para abrir o portal.',
      );
    }

    return AdminPortalSession(
      adminId: (data['id'] as num).toInt(),
      adminName: _extractAdminName(data['mensagem'] as String?),
      companyId: companyId,
      companyName: _extractOptionalString(data, 'companyName') ?? '',
      companySlug: _extractOptionalString(data, 'companySlug'),
      email: normalizedEmail,
      accessToken: accessToken,
      backendRoleLabel: data['tipoUsuario'] as String? ?? 'Admin',
    );
  }

  @override
  Future<AdminCompanyAdminBootstrapResult> bootstrap(
    AdminCompanyAdminBootstrapInput input,
  ) async {
    final response = await _client.post(
      Uri.parse('$baseUrl/company-admins/bootstrap'),
      headers: const {'Content-Type': 'application/json'},
      body: jsonEncode(input.toJson()),
    );
    final responseBody = decodeAdminPortalBody(response);

    if (response.statusCode != 201) {
      throw AdminPortalException(
        _extractMessage(
          responseBody,
          fallback: 'Nao foi possivel criar o primeiro admin agora.',
        ),
      );
    }

    final data = jsonDecode(responseBody) as Map<String, dynamic>;
    return AdminCompanyAdminBootstrapResult.fromJson(data);
  }

  @override
  Future<String> resendVerification({required String email}) async {
    final normalizedEmail = email.trim().toLowerCase();
    final response = await _client.post(
      Uri.parse('$baseUrl/auth/email-verifications/resend'),
      headers: const {'Content-Type': 'application/json'},
      body: jsonEncode({'email': normalizedEmail}),
    );
    final responseBody = decodeAdminPortalBody(response);

    if (response.statusCode != 202) {
      final error = extractAdminPortalError(
        responseBody,
        fallback: 'Nao foi possivel reenviar a verificacao agora.',
      );
      throw AdminPortalException(
        error.message,
        code: error.code,
        email: error.email ?? normalizedEmail,
      );
    }

    return _extractMessage(
      responseBody,
      fallback:
          'Se existir uma conta pendente para este e-mail, uma nova confirmação será enviada.',
    );
  }

  @override
  Future<String> changePendingEmail({
    required String currentEmail,
    required String senha,
    required String newEmail,
  }) async {
    final response = await _client.post(
      Uri.parse('$baseUrl/auth/email-verifications/change-email'),
      headers: const {'Content-Type': 'application/json'},
      body: jsonEncode({
        'email': currentEmail.trim().toLowerCase(),
        'senha': senha,
        'newEmail': newEmail.trim().toLowerCase(),
      }),
    );
    final responseBody = decodeAdminPortalBody(response);

    if (response.statusCode != 200) {
      final error = extractAdminPortalError(
        responseBody,
        fallback: 'Nao foi possivel atualizar o e-mail pendente agora.',
      );
      throw AdminPortalException(
        error.message,
        code: error.code,
        email: error.email,
      );
    }

    final data = jsonDecode(responseBody) as Map<String, dynamic>;
    return (data['email'] as String?)?.trim().toLowerCase() ??
        newEmail.trim().toLowerCase();
  }

  static String _extractAdminName(String? rawMessage) {
    final message = rawMessage?.trim();
    if (message == null || message.isEmpty) {
      return 'Administrador da empresa';
    }

    final match = RegExp(r'Bem-vindo,\s*Admin\s*(.+)!').firstMatch(message);
    if (match != null) {
      return match.group(1)?.trim().isNotEmpty == true
          ? match.group(1)!.trim()
          : 'Administrador da empresa';
    }

    return message;
  }

  static String? _extractOptionalString(Map<String, dynamic> data, String key) {
    final value = data[key];
    if (value is String && value.trim().isNotEmpty) {
      return value.trim();
    }
    return null;
  }

  static String _extractMessage(String body, {required String fallback}) {
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
      // fallback para texto puro
    }

    return trimmed;
  }
}
