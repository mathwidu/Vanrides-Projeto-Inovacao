import 'dart:convert';

import 'package:http/http.dart' as http;

import '../../../services/api_constants.dart';
import '../domain/admin_company_link_flow_models.dart';
import '../domain/admin_portal_session.dart';
import 'admin_portal_http_support.dart';

abstract class AdminDriverLinkGateway {
  Future<List<AdminDriverInviteRecord>> fetchDriverInvites();
  Future<AdminDriverInviteRecord> createDriverInvite({DateTime? expiresAt});
  Future<AdminDriverInviteRecord> cancelDriverInvite(int id);
  Future<List<AdminDriverLinkRequestRecord>> fetchDriverLinkRequests();
  Future<AdminDriverLinkRequestRecord> approveDriverLinkRequest({
    required int requestId,
    String? reason,
  });
  Future<AdminDriverLinkRequestRecord> rejectDriverLinkRequest({
    required int requestId,
    String? reason,
  });
}

class AdminDriverLinkPortalService implements AdminDriverLinkGateway {
  AdminDriverLinkPortalService({
    required AdminPortalSession session,
    http.Client? client,
  }) : _session = session,
       _client = client ?? http.Client();

  final AdminPortalSession _session;
  final http.Client _client;

  @override
  Future<List<AdminDriverInviteRecord>> fetchDriverInvites() async {
    final response = await _client.get(
      Uri.parse('$baseUrl/company-admins/driver-invites'),
      headers: adminAuthHeaders(_session),
    );

    if (response.statusCode != 200) {
      throwPortalHttpError(
        response,
        fallback: 'Nao foi possivel carregar os convites de motoristas.',
      );
    }

    final data = jsonDecode(decodeAdminPortalBody(response)) as List<dynamic>;
    return data
        .map(
          (item) =>
              AdminDriverInviteRecord.fromJson(item as Map<String, dynamic>),
        )
        .toList(growable: false);
  }

  @override
  Future<AdminDriverInviteRecord> createDriverInvite({
    DateTime? expiresAt,
  }) async {
    final response = await _client.post(
      Uri.parse('$baseUrl/company-admins/driver-invites'),
      headers: adminJsonHeaders(_session),
      body: jsonEncode({'expiresAt': expiresAt?.toUtc().toIso8601String()}),
    );

    if (response.statusCode != 201) {
      throwPortalHttpError(
        response,
        fallback: 'Nao foi possivel gerar o convite de motorista agora.',
      );
    }

    final data =
        jsonDecode(decodeAdminPortalBody(response)) as Map<String, dynamic>;
    return AdminDriverInviteRecord.fromJson(data);
  }

  @override
  Future<AdminDriverInviteRecord> cancelDriverInvite(int id) async {
    final response = await _client.post(
      Uri.parse('$baseUrl/company-admins/driver-invites/$id/cancel'),
      headers: adminJsonHeaders(_session),
    );

    if (response.statusCode != 200) {
      throwPortalHttpError(
        response,
        fallback: 'Nao foi possivel cancelar o convite de motorista agora.',
      );
    }

    final data =
        jsonDecode(decodeAdminPortalBody(response)) as Map<String, dynamic>;
    return AdminDriverInviteRecord.fromJson(data);
  }

  @override
  Future<List<AdminDriverLinkRequestRecord>> fetchDriverLinkRequests() async {
    final response = await _client.get(
      Uri.parse('$baseUrl/company-admins/driver-link-requests'),
      headers: adminAuthHeaders(_session),
    );

    if (response.statusCode != 200) {
      throwPortalHttpError(
        response,
        fallback: 'Nao foi possivel carregar as solicitacoes de motoristas.',
      );
    }

    final data = jsonDecode(decodeAdminPortalBody(response)) as List<dynamic>;
    return data
        .map(
          (item) => AdminDriverLinkRequestRecord.fromJson(
            item as Map<String, dynamic>,
          ),
        )
        .toList(growable: false);
  }

  @override
  Future<AdminDriverLinkRequestRecord> approveDriverLinkRequest({
    required int requestId,
    String? reason,
  }) async {
    return _reviewDriverLinkRequest(
      requestId: requestId,
      action: 'approve',
      reason: reason,
      fallback: 'Nao foi possivel aprovar a solicitacao de motorista.',
    );
  }

  @override
  Future<AdminDriverLinkRequestRecord> rejectDriverLinkRequest({
    required int requestId,
    String? reason,
  }) async {
    return _reviewDriverLinkRequest(
      requestId: requestId,
      action: 'reject',
      reason: reason,
      fallback: 'Nao foi possivel rejeitar a solicitacao de motorista.',
    );
  }

  Future<AdminDriverLinkRequestRecord> _reviewDriverLinkRequest({
    required int requestId,
    required String action,
    required String fallback,
    String? reason,
  }) async {
    final response = await _client.post(
      Uri.parse(
        '$baseUrl/company-admins/driver-link-requests/$requestId/$action',
      ),
      headers: adminJsonHeaders(_session),
      body: jsonEncode({
        'reason': reason?.trim().isEmpty == true ? null : reason?.trim(),
      }),
    );

    if (response.statusCode != 200) {
      throwPortalHttpError(response, fallback: fallback);
    }

    final data =
        jsonDecode(decodeAdminPortalBody(response)) as Map<String, dynamic>;
    return AdminDriverLinkRequestRecord.fromJson(data);
  }
}
