import 'dart:convert';

import 'package:http/http.dart' as http;

import '../../../app/session/app_company_link_status.dart';
import '../../../app/session/app_session_user.dart';
import '../../../services/api_constants.dart';
import '../../../services/auth_headers.dart';
import '../../../services/jwt_claims.dart';
import '../../../services/public_auth_support.dart';
import '../domain/driver_onboarding_models.dart';

abstract class DriverOnboardingGateway {
  Future<DriverInvitePreviewRecord> previewInvite(String token);
  Future<DriverOnboardingStatusRecord> redeemInvite(
    AppSessionUser session, {
    required String token,
  });
  Future<DriverOnboardingStatusRecord> fetchStatus(AppSessionUser session);
  Future<List<DriverOnboardingCompanyRecord>> searchCompanies(
    AppSessionUser session, {
    String? query,
  });
  Future<DriverOnboardingStatusRecord> createRequest(
    AppSessionUser session, {
    required int companyId,
  });
  Future<AppSessionUser> upgradeSession(
    AppSessionUser session, {
    String? companyName,
  });
}

class DriverOnboardingService implements DriverOnboardingGateway {
  DriverOnboardingService({http.Client? client})
    : _client = client ?? http.Client();

  final http.Client _client;

  @override
  Future<DriverInvitePreviewRecord> previewInvite(String token) async {
    final response = await _client.get(
      Uri.parse(
        '$baseUrl/driver-onboarding/invites/${_tokenSegment(token)}'
        '/preview',
      ),
    );
    final body = decodePublicAuthResponseBody(response);
    if (response.statusCode != 200) {
      throw _toException(body);
    }
    final decoded = jsonDecode(body) as Map<String, dynamic>;
    return DriverInvitePreviewRecord.fromJson(decoded);
  }

  @override
  Future<DriverOnboardingStatusRecord> redeemInvite(
    AppSessionUser session, {
    required String token,
  }) async {
    final response = await _authorizedSend(
      session,
      method: 'POST',
      uri: Uri.parse(
        '$baseUrl/driver-onboarding/invites/'
        '${_tokenSegment(token)}/redeem',
      ),
      expectedStatusCodes: const {200},
    );
    return _decodeStatus(response);
  }

  @override
  Future<DriverOnboardingStatusRecord> fetchStatus(AppSessionUser session) =>
      _authorizedGet(
        session,
        Uri.parse('$baseUrl/driver-onboarding/status'),
      ).then(_decodeStatus);

  @override
  Future<List<DriverOnboardingCompanyRecord>> searchCompanies(
    AppSessionUser session, {
    String? query,
  }) async {
    final uri = Uri.parse('$baseUrl/driver-onboarding/companies').replace(
      queryParameters: {
        if (query != null && query.trim().isNotEmpty) 'query': query.trim(),
      },
    );

    final response = await _authorizedGet(session, uri);
    final decoded = jsonDecode(response);
    if (decoded is! List<dynamic>) {
      return const [];
    }
    return decoded
        .whereType<Map<String, dynamic>>()
        .map(DriverOnboardingCompanyRecord.fromJson)
        .toList(growable: false);
  }

  @override
  Future<DriverOnboardingStatusRecord> createRequest(
    AppSessionUser session, {
    required int companyId,
  }) async {
    final response = await _authorizedSend(
      session,
      method: 'POST',
      uri: Uri.parse('$baseUrl/driver-onboarding/requests'),
      body: {'companyId': companyId},
      expectedStatusCodes: const {201},
    );
    return _decodeStatus(response);
  }

  @override
  Future<AppSessionUser> upgradeSession(
    AppSessionUser session, {
    String? companyName,
  }) async {
    final response = await _authorizedSend(
      session,
      method: 'POST',
      uri: Uri.parse('$baseUrl/driver-onboarding/session-upgrade'),
      expectedStatusCodes: const {200},
    );
    final data = jsonDecode(response) as Map<String, dynamic>;
    final accessToken = data['accessToken'] as String?;
    if (accessToken == null || accessToken.isEmpty) {
      throw const DriverOnboardingException(
        'Nao foi possivel atualizar a sessao do motorista.',
      );
    }

    return session.copyWith(
      accessToken: accessToken,
      refreshToken: data['refreshToken'] as String?,
      companyId: extractCompanyIdFromAccessToken(accessToken),
      companyLinkStatus: AppCompanyLinkStatus.companyLinked,
    );
  }

  Future<String> _authorizedGet(AppSessionUser session, Uri uri) async {
    final response = await _client.get(
      uri,
      headers: {
        'Authorization': buildAuthorizationHeader(
          accessToken: session.accessToken,
          email: session.email,
          senha: session.password,
        ),
      },
    );
    final body = decodePublicAuthResponseBody(response);
    if (response.statusCode != 200) {
      throw _toException(body);
    }
    return body;
  }

  Future<String> _authorizedSend(
    AppSessionUser session, {
    required String method,
    required Uri uri,
    Map<String, dynamic>? body,
    Set<int> expectedStatusCodes = const {200},
  }) async {
    final request = http.Request(method, uri)
      ..headers.addAll({
        'Content-Type': 'application/json',
        'Authorization': buildAuthorizationHeader(
          accessToken: session.accessToken,
          email: session.email,
          senha: session.password,
        ),
      });
    if (body != null) {
      request.body = jsonEncode(body);
    }

    final streamed = await _client.send(request);
    final response = await http.Response.fromStream(streamed);
    final responseBody = decodePublicAuthResponseBody(response);
    if (!expectedStatusCodes.contains(response.statusCode)) {
      throw _toException(responseBody);
    }
    return responseBody;
  }

  DriverOnboardingStatusRecord _decodeStatus(String body) {
    final decoded = jsonDecode(body) as Map<String, dynamic>;
    return DriverOnboardingStatusRecord.fromJson(decoded);
  }

  DriverOnboardingException _toException(String body) {
    final error = PublicAuthApiError.tryParse(body);
    return DriverOnboardingException(
      error?.message ?? 'Nao foi possivel concluir o vinculo do motorista.',
    );
  }

  String _tokenSegment(String token) => Uri.encodeComponent(token.trim());
}
