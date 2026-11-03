import 'dart:convert';

import 'package:http/http.dart' as http;

import '../../../services/api_constants.dart';
import '../../../services/api_response_decoding.dart';
import '../../../services/auth_headers.dart';
import '../../admin/data/admin_portal_http_support.dart';
import '../../admin/domain/admin_portal_session.dart';
import '../domain/support_ticket.dart';

export '../domain/support_ticket.dart';

abstract class SupportTicketGateway {
  Future<List<SupportTicketSummary>> fetchMyTickets();

  Future<SupportTicket> createTicket({
    required String category,
    required String subject,
    required String message,
  });
}

abstract class AdminSupportTicketGateway {
  Future<List<SupportTicketSummary>> fetchTickets({String? status});
  Future<SupportTicket> fetchTicket(int ticketId);

  Future<SupportTicket> replyTicket({
    required int ticketId,
    required String message,
    String? status,
  });

  Future<SupportTicket> updateTicketStatus({
    required int ticketId,
    required String status,
  });
}

class UserSupportTicketService implements SupportTicketGateway {
  UserSupportTicketService({
    required String email,
    required String senha,
    String? accessToken,
    http.Client? client,
  }) : _email = email,
       _senha = senha,
       _accessToken = accessToken,
       _client = client ?? http.Client();

  final String _email;
  final String _senha;
  final String? _accessToken;
  final http.Client _client;

  Map<String, String> get _authHeaders {
    return {
      'Authorization': buildAuthorizationHeader(
        accessToken: _accessToken,
        email: _email,
        senha: _senha,
      ),
    };
  }

  @override
  Future<List<SupportTicketSummary>> fetchMyTickets() async {
    final response = await _client.get(
      Uri.parse('$baseUrl/support/tickets'),
      headers: _authHeaders,
    );
    final body = decodeApiResponseBody(response);
    if (response.statusCode != 200) {
      throw SupportTicketException(
        _extractMessage(body, 'Nao foi possivel carregar seus atendimentos.'),
      );
    }

    final decoded = jsonDecode(body) as List<dynamic>;
    return decoded
        .map(
          (item) => SupportTicketSummary.fromJson(item as Map<String, dynamic>),
        )
        .toList(growable: false);
  }

  @override
  Future<SupportTicket> createTicket({
    required String category,
    required String subject,
    required String message,
  }) async {
    final response = await _client.post(
      Uri.parse('$baseUrl/support/tickets'),
      headers: {
        ..._authHeaders,
        'Content-Type': 'application/json; charset=utf-8',
      },
      body: jsonEncode({
        'category': category,
        'subject': subject,
        'message': message,
      }),
    );
    final body = decodeApiResponseBody(response);
    if (response.statusCode != 201) {
      throw SupportTicketException(
        _extractMessage(body, 'Nao foi possivel abrir o atendimento.'),
      );
    }

    return SupportTicket.fromJson(jsonDecode(body) as Map<String, dynamic>);
  }
}

class AdminSupportTicketPortalService implements AdminSupportTicketGateway {
  AdminSupportTicketPortalService({
    required AdminPortalSession session,
    http.Client? client,
  }) : _session = session,
       _client = client ?? http.Client();

  final AdminPortalSession _session;
  final http.Client _client;

  @override
  Future<List<SupportTicketSummary>> fetchTickets({String? status}) async {
    final uri = Uri.parse('$baseUrl/company-admins/support/tickets').replace(
      queryParameters:
          status == null || status.trim().isEmpty ? null : {'status': status},
    );
    final response = await _client.get(
      uri,
      headers: adminAuthHeaders(_session),
    );

    if (response.statusCode != 200) {
      throwPortalHttpError(
        response,
        fallback: 'Nao foi possivel carregar a fila de atendimento.',
      );
    }

    final data = jsonDecode(decodeAdminPortalBody(response)) as List<dynamic>;
    return data
        .map(
          (item) => SupportTicketSummary.fromJson(item as Map<String, dynamic>),
        )
        .toList(growable: false);
  }

  @override
  Future<SupportTicket> fetchTicket(int ticketId) async {
    final response = await _client.get(
      Uri.parse('$baseUrl/company-admins/support/tickets/$ticketId'),
      headers: adminAuthHeaders(_session),
    );
    if (response.statusCode != 200) {
      throwPortalHttpError(
        response,
        fallback: 'Nao foi possivel carregar o atendimento.',
      );
    }
    return SupportTicket.fromJson(
      jsonDecode(decodeAdminPortalBody(response)) as Map<String, dynamic>,
    );
  }

  @override
  Future<SupportTicket> replyTicket({
    required int ticketId,
    required String message,
    String? status,
  }) async {
    final response = await _client.post(
      Uri.parse('$baseUrl/company-admins/support/tickets/$ticketId/responses'),
      headers: adminJsonHeaders(_session),
      body: jsonEncode({
        'message': message,
        if (status != null) 'status': status,
      }),
    );
    if (response.statusCode != 200) {
      throwPortalHttpError(
        response,
        fallback: 'Nao foi possivel responder o atendimento.',
      );
    }
    return SupportTicket.fromJson(
      jsonDecode(decodeAdminPortalBody(response)) as Map<String, dynamic>,
    );
  }

  @override
  Future<SupportTicket> updateTicketStatus({
    required int ticketId,
    required String status,
  }) async {
    final response = await _client.post(
      Uri.parse('$baseUrl/company-admins/support/tickets/$ticketId/status'),
      headers: adminJsonHeaders(_session),
      body: jsonEncode({'status': status}),
    );
    if (response.statusCode != 200) {
      throwPortalHttpError(
        response,
        fallback: 'Nao foi possivel atualizar o atendimento.',
      );
    }
    return SupportTicket.fromJson(
      jsonDecode(decodeAdminPortalBody(response)) as Map<String, dynamic>,
    );
  }
}

String _extractMessage(String body, String fallback) {
  if (body.trim().isEmpty) {
    return fallback;
  }
  try {
    final decoded = jsonDecode(body);
    if (decoded is Map<String, dynamic>) {
      final message =
          decoded['message'] ?? decoded['error'] ?? decoded['mensagem'];
      if (message is String && message.trim().isNotEmpty) {
        return message.trim();
      }
    }
  } catch (_) {
    // Text-only response.
  }
  return body.trim();
}
