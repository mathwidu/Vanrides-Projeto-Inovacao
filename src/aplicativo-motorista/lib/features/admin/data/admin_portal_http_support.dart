import 'dart:convert';

import 'package:http/http.dart' as http;

import '../domain/admin_portal_exception.dart';
import '../domain/admin_portal_session.dart';

class AdminPortalErrorPayload {
  const AdminPortalErrorPayload({required this.message, this.code, this.email});

  final String message;
  final String? code;
  final String? email;
}

Map<String, String> adminJsonHeaders(AdminPortalSession session) {
  return {
    'Content-Type': 'application/json',
    'Authorization': 'Bearer ${session.accessToken}',
  };
}

Map<String, String> adminAuthHeaders(AdminPortalSession session) {
  return {'Authorization': 'Bearer ${session.accessToken}'};
}

String decodeAdminPortalBody(http.Response response) {
  return utf8.decode(response.bodyBytes);
}

String extractAdminPortalMessage(String body, {required String fallback}) {
  return extractAdminPortalError(body, fallback: fallback).message;
}

AdminPortalErrorPayload extractAdminPortalError(
  String body, {
  required String fallback,
}) {
  final trimmed = body.trim();
  if (trimmed.isEmpty) {
    return AdminPortalErrorPayload(message: fallback);
  }

  try {
    final decoded = jsonDecode(trimmed);
    if (decoded is Map<String, dynamic>) {
      final message = _extractFirstString(decoded, const [
        'message',
        'error',
        'mensagem',
      ]);
      final code = _extractOptionalString(decoded['code']);
      final email = _extractOptionalString(decoded['email']);
      if (message != null) {
        if (_isGenericHttpTitle(message)) {
          return AdminPortalErrorPayload(
            message: fallback,
            code: code,
            email: email,
          );
        }
        return AdminPortalErrorPayload(
          message: message,
          code: code,
          email: email,
        );
      }
    }
  } catch (_) {
    // resposta de texto puro
  }

  if (_isGenericHttpTitle(trimmed)) {
    return AdminPortalErrorPayload(message: fallback);
  }

  return AdminPortalErrorPayload(message: trimmed);
}

Never throwPortalHttpError(http.Response response, {required String fallback}) {
  final error = extractAdminPortalError(
    decodeAdminPortalBody(response),
    fallback: fallback,
  );
  throw AdminPortalException(
    error.message,
    code: error.code,
    email: error.email,
  );
}

String? _extractFirstString(Map<String, dynamic> decoded, List<String> keys) {
  for (final key in keys) {
    final value = decoded[key];
    final normalized = _extractOptionalString(value);
    if (normalized != null) {
      return normalized;
    }
  }
  return null;
}

String? _extractOptionalString(Object? value) {
  if (value is String && value.trim().isNotEmpty) {
    return value.trim();
  }
  return null;
}

bool _isGenericHttpTitle(String value) {
  final normalized = value.trim().toLowerCase();
  return const {
    'bad request',
    'unauthorized',
    'forbidden',
    'not found',
    'conflict',
    'internal server error',
  }.contains(normalized);
}
