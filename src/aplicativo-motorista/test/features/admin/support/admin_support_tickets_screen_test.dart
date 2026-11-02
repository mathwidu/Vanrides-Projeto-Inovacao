import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_portal_session.dart';
import 'package:vanrides_frontend_dev/features/admin/presentation/admin_support_tickets_screen.dart';
import 'package:vanrides_frontend_dev/features/support/data/support_ticket_service.dart';

void main() {
  testWidgets('admin ve fila de atendimento e responde ticket aberto', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1440, 1200));
    final gateway = _FakeAdminSupportTicketGateway();

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: AdminSupportTicketsScreen(
            session: const AdminPortalSession(
              adminId: 90,
              adminName: 'Admin',
              companyId: 66,
              companyName: 'VanRides',
              companySlug: 'vanrides',
              email: 'admin@vanrides.dev',
              accessToken: 'token.admin',
            ),
            gateway: gateway,
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Atendimento'), findsWidgets);
    expect(find.text('Usuario aguardando retorno'), findsWidgets);
    expect(find.text('Van atrasada'), findsWidgets);

    await tester.enterText(
      find.byKey(AdminSupportTicketsScreen.replyFieldKey),
      'Vou falar com o motorista.',
    );
    await tester.ensureVisible(
      find.byKey(AdminSupportTicketsScreen.replyButtonKey),
    );
    await tester.tap(find.byKey(AdminSupportTicketsScreen.replyButtonKey));
    await tester.pumpAndSettle();

    expect(gateway.replyMessages, ['Vou falar com o motorista.']);
    expect(find.text('Em atendimento'), findsWidgets);
    expect(find.text('Resposta registrada para o usuario.'), findsOneWidget);
  });
}

class _FakeAdminSupportTicketGateway implements AdminSupportTicketGateway {
  final replyMessages = <String>[];
  SupportTicket _ticket = _supportTicket(status: SupportTicketStatus.open);

  @override
  Future<List<SupportTicketSummary>> fetchTickets({String? status}) async {
    return [_ticket.toSummary()];
  }

  @override
  Future<SupportTicket> fetchTicket(int ticketId) async {
    return _ticket;
  }

  @override
  Future<SupportTicket> replyTicket({
    required int ticketId,
    required String message,
    String? status,
  }) async {
    replyMessages.add(message);
    _ticket = _supportTicket(status: SupportTicketStatus.inProgress);
    return _ticket;
  }

  @override
  Future<SupportTicket> updateTicketStatus({
    required int ticketId,
    required String status,
  }) async {
    _ticket = _supportTicket(status: SupportTicketStatus.resolved);
    return _ticket;
  }
}

SupportTicket _supportTicket({required SupportTicketStatus status}) {
  return SupportTicket(
    id: 100,
    status: status,
    category: 'ATRASO',
    subject: 'Van atrasada',
    createdByRole: 'PASSENGER',
    createdByUserId: 21,
    createdByName: 'Matheus Duarte',
    createdAt: DateTime.utc(2026, 6, 19, 12),
    updatedAt: DateTime.utc(2026, 6, 19, 12, 5),
    messages: [
      SupportTicketMessage(
        id: 501,
        authorRole: 'PASSENGER',
        authorUserId: 21,
        authorName: 'Matheus Duarte',
        message: 'A van ainda nao passou.',
        createdAt: DateTime.utc(2026, 6, 19, 12),
      ),
    ],
  );
}
