import 'dart:convert';

import 'package:http/http.dart' as http;

import 'api_constants.dart';

const emailVerificationResendCooldown = Duration(minutes: 15);

String decodePublicAuthResponseBody(http.Response response) {
  return utf8.decode(response.bodyBytes);
}

class PublicAuthApiError {
  const PublicAuthApiError({required this.message, this.code, this.email});

  final String message;
  final String? code;
  final String? email;

  bool get isEmailNotVerified => code == 'EMAIL_NOT_VERIFIED';

  static PublicAuthApiError? tryParse(String body) {
    final trimmed = body.trim();
    if (trimmed.isEmpty) {
      return null;
    }

    try {
      final decoded = jsonDecode(trimmed);
      if (decoded is! Map<String, dynamic>) {
        return null;
      }

      String? pickString(String key) {
        final value = decoded[key];
        if (value is String && value.trim().isNotEmpty) {
          return value.trim();
        }
        return null;
      }

      final message =
          pickString('message') ??
          pickString('mensagem') ??
          pickString('error');
      final code = pickString('code');
      final email = pickString('email');

      if (message == null && code == null && email == null) {
        return null;
      }

      return PublicAuthApiError(
        message: message ?? trimmed,
        code: code,
        email: email,
      );
    } catch (_) {
      return PublicAuthApiError(message: trimmed);
    }
  }
}

class EmailVerificationResendResult {
  const EmailVerificationResendResult({required this.message, this.email});

  final String message;
  final String? email;
}

Duration? extractInitialEmailVerificationCooldownFromBody(
  String responseBody, {
  DateTime? now,
}) {
  final trimmed = responseBody.trim();
  if (trimmed.isEmpty) {
    return null;
  }

  try {
    final decoded = jsonDecode(trimmed);
    if (decoded is! Map<String, dynamic>) {
      return null;
    }

    final rawTimestamp = decoded['emailVerificationLastSentAt'];
    if (rawTimestamp is! String || rawTimestamp.trim().isEmpty) {
      return null;
    }

    final lastSentAt = DateTime.tryParse(rawTimestamp.trim());
    if (lastSentAt == null) {
      return null;
    }

    final remaining = lastSentAt
        .add(emailVerificationResendCooldown)
        .difference(now ?? DateTime.now());

    if (remaining <= Duration.zero) {
      return Duration.zero;
    }

    return remaining;
  } catch (_) {
    return null;
  }
}

Future<EmailVerificationResendResult> resendEmailVerification({
  required String email,
}) async {
  final response = await http.post(
    Uri.parse('$baseUrl/auth/email-verifications/resend'),
    headers: const {'Content-Type': 'application/json'},
    body: jsonEncode({'email': email.trim().toLowerCase()}),
  );
  final responseBody = decodePublicAuthResponseBody(response);

  if (response.statusCode != 202) {
    final error = PublicAuthApiError.tryParse(responseBody);
    throw Exception(
      error?.message ?? 'Não foi possível reenviar o e-mail de verificação.',
    );
  }

  final decoded = jsonDecode(responseBody);
  if (decoded is! Map<String, dynamic>) {
    return const EmailVerificationResendResult(
      message:
          'Se existir uma conta pendente para este e-mail, uma nova confirmação será enviada.',
    );
  }

  return EmailVerificationResendResult(
    message:
        decoded['message'] as String? ??
        'Se existir uma conta pendente para este e-mail, uma nova confirmação será enviada.',
    email: decoded['email'] as String?,
  );
}
