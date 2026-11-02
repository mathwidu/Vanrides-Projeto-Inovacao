import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_portal_session.dart';
import 'package:vanrides_frontend_dev/features/support/data/support_ticket_service.dart';

void main() {
  test('lista e cria tickets do usuario autenticado', () async {
    final requests = <http.BaseRequest>[];
    final service = UserSupportTicketService(
      email: 'passageiro@vanrides.dev',
      senha: 'Secret123',
      accessToken: 'token.user',
      client: MockClient((request) async {
        requests.add(request);
        if (request.method == 'GET') {
          expect(request.url.path, '/support/tickets');
          expect(request.headers['Authorization'], 'Bearer token.user');
          return http.Response.bytes(
            utf8.encode(jsonEncode([_ticketSummaryJson()])),
            200,
            headers: {'content-type': 'application/json; charset=utf-8'},
          );
        }

        expect(request.method, 'POST');
        expect(request.url.path, '/support/tickets');
        final body = jsonDecode(request.body) as Map<String, dynamic>;
        expect(body['category'], 'ATRASO');
        expect(body['subject'], 'Van atrasada');
        expect(body['message'], 'A van ainda nao passou.');
        return http.Response.bytes(
          utf8.encode(jsonEncode(_ticketDetailJson(status: 'OPEN'))),
          201,
          headers: {'content-type': 'application/json; charset=utf-8'},
        );
      }),
    );

    final summaries = await service.fetchMyTickets();
    final created = await service.createTicket(
      category: 'ATRASO',
      subject: 'Van atrasada',
      message: 'A van ainda nao passou.',
    );

    expect(summaries.single.subject, 'Van atrasada');
    expect(created.messages.single.message, 'A van ainda nao passou.');
    expect(requests.map((request) => request.method), ['GET', 'POST']);
  });

  test('admin lista fila e responde ticket da empresa', () async {
    final service = AdminSupportTicketPortalService(
      session: const AdminPortalSession(
        adminId: 90,
        adminName: 'Admin',
        companyId: 66,
        companyName: 'VanRides',
        companySlug: 'vanrides',
        email: 'admin@vanrides.dev',
        accessToken: 'token.admin',
      ),
      client: MockClient((request) async {
        expect(request.headers['Authorization'], 'Bearer token.admin');
        if (request.method == 'GET' &&
            request.url.path == '/company-admins/support/tickets') {
          expect(request.url.queryParameters['status'], 'OPEN');
          return http.Response.bytes(
            utf8.encode(jsonEncode([_ticketSummaryJson()])),
            200,
            headers: {'content-type': 'application/json; charset=utf-8'},
          );
        }

        if (request.method == 'POST' &&
            request.url.path ==
                '/company-admins/support/tickets/100/responses') {
          final body = jsonDecode(request.body) as Map<String, dynamic>;
          expect(body['message'], 'Vou falar com o motorista.');
          expect(body['status'], 'IN_PROGRESS');
          return http.Response.bytes(
            utf8.encode(jsonEncode(_ticketDetailJson(status: 'IN_PROGRESS'))),
            200,
            headers: {'content-type': 'application/json; charset=utf-8'},
          );
        }

        fail('Requisicao inesperada: ${request.method} ${request.url}');
      }),
    );

    final queue = await service.fetchTickets(status: 'OPEN');
    final updated = await service.replyTicket(
      ticketId: 100,
      message: 'Vou falar com o motorista.',
      status: 'IN_PROGRESS',
    );

    expect(queue.single.status.label, 'Aberto');
    expect(updated.status.label, 'Em atendimento');
  });
}

Map<String, dynamic> _ticketSummaryJson() {
  return {
    'id': 100,
    'status': 'OPEN',
    'category': 'ATRASO',
    'subject': 'Van atrasada',
    'createdByRole': 'PASSENGER',
    'createdByUserId': 21,
    'createdByName': 'Matheus Duarte',
    'createdAt': '2026-06-19T12:00:00Z',
    'updatedAt': '2026-06-19T12:05:00Z',
    'resolvedAt': null,
  };
}

Map<String, dynamic> _ticketDetailJson({required String status}) {
  return {
    ..._ticketSummaryJson(),
    'status': status,
    'messages': [
      {
        'id': 501,
        'authorRole': 'PASSENGER',
        'authorUserId': 21,
        'authorName': 'Matheus Duarte',
        'message': 'A van ainda nao passou.',
        'createdAt': '2026-06-19T12:00:00Z',
      },
    ],
  };
}
