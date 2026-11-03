import 'dart:convert';

import 'package:http/http.dart' as http;

import '../../../app/session/app_company_link_status.dart';
import '../../../app/session/app_session_user.dart';
import '../../../l10n/app_strings.dart';
import '../../../services/api_constants.dart';
import '../../../services/auth_headers.dart';
import '../../../services/jwt_claims.dart';
import '../../../services/public_auth_support.dart';
import '../domain/passenger_onboarding_models.dart';

abstract class PassengerOnboardingGateway {
  Future<PassengerOnboardingStatusRecord> fetchStatus(AppSessionUser session);
  Future<List<PassengerOnboardingCompanyRecord>> searchCompanies(
    AppSessionUser session, {
    String? query,
  });
  Future<PassengerOnboardingStatusRecord> createRequest(
    AppSessionUser session, {
    required int companyId,
  });
  Future<PassengerOnboardingStatusRecord> redeemInviteCode(
    AppSessionUser session, {
    required String code,
  });
  Future<AppSessionUser> upgradeSession(
    AppSessionUser session, {
    int? motoristaId,
    String? companyName,
  });
}

class PassengerOnboardingService implements PassengerOnboardingGateway {
  PassengerOnboardingService({http.Client? client})
    : _client = client ?? http.Client();

  final http.Client _client;

  @override
  Future<PassengerOnboardingStatusRecord> fetchStatus(AppSessionUser session) =>
      _authorizedGet(
        session,
        Uri.parse('$baseUrl/passageiro-onboarding/status'),
      ).then(_decodeStatus);

  @override
  Future<List<PassengerOnboardingCompanyRecord>> searchCompanies(
    AppSessionUser session, {
    String? query,
  }) async {
    final uri = Uri.parse('$baseUrl/passageiro-onboarding/companies').replace(
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
        .map(PassengerOnboardingCompanyRecord.fromJson)
        .toList(growable: false);
  }

  @override
  Future<PassengerOnboardingStatusRecord> createRequest(
    AppSessionUser session, {
    required int companyId,
  }) async {
    final response = await _authorizedSend(
      session,
      method: 'POST',
      uri: Uri.parse('$baseUrl/passageiro-onboarding/requests'),
      body: {'companyId': companyId},
      expectedStatusCodes: const {201},
    );
    return _decodeStatus(response);
  }

  @override
  Future<PassengerOnboardingStatusRecord> redeemInviteCode(
    AppSessionUser session, {
    required String code,
  }) async {
    final response = await _authorizedSend(
      session,
      method: 'POST',
      uri: Uri.parse('$baseUrl/passageiro-onboarding/invite-codes/redeem'),
      body: {'code': code.trim()},
      expectedStatusCodes: const {200},
    );
    return _decodeStatus(response);
  }

  @override
  Future<AppSessionUser> upgradeSession(
    AppSessionUser session, {
    int? motoristaId,
    String? companyName,
  }) async {
    final response = await _authorizedSend(
      session,
      method: 'POST',
      uri: Uri.parse('$baseUrl/passageiro-onboarding/session-upgrade'),
      expectedStatusCodes: const {200},
    );
    final data = jsonDecode(response) as Map<String, dynamic>;
    final accessToken = data['accessToken'] as String?;
    if (accessToken == null || accessToken.isEmpty) {
      throw PassengerOnboardingException(
        AppStrings.passageiro.naoFoiPossivelPromoverSessao,
      );
    }

    return session.copyWith(
      accessToken: accessToken,
      refreshToken: data['refreshToken'] as String?,
      companyId: extractCompanyIdFromAccessToken(accessToken),
      motoristaId: motoristaId,
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

  PassengerOnboardingStatusRecord _decodeStatus(String body) {
    final decoded = jsonDecode(body) as Map<String, dynamic>;
    return PassengerOnboardingStatusRecord.fromJson(decoded);
  }

  PassengerOnboardingException _toException(String body) {
    final error = PublicAuthApiError.tryParse(body);
    return PassengerOnboardingException(
      error?.message ?? AppStrings.passageiro.naoFoiPossivelConcluirOnboarding,
    );
  }
}
