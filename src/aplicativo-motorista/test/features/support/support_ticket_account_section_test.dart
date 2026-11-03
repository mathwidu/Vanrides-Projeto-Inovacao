import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vanrides_frontend_dev/features/support/data/support_ticket_service.dart';
import 'package:vanrides_frontend_dev/features/support/presentation/support_ticket_account_section.dart';

void main() {
  testWidgets('usuario abre atendimento pela conta e ve status claro', (
    tester,
  ) async {
    final gateway = _FakeSupportTicketGateway();

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: SupportTicketAccountSection(gateway: gateway, enabled: true),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Nenhum atendimento aberto'), findsOneWidget);

    await tester.tap(find.byKey(SupportTicketAccountSection.openButtonKey));
    await tester.pumpAndSettle();

    await tester.enterText(
      find.byKey(SupportTicketCreateSheet.subjectKey),
      'Van atrasada',
    );
    await tester.enterText(
      find.byKey(SupportTicketCreateSheet.messageKey),
      'A van ainda nao passou.',
    );
    await tester.tap(find.byKey(SupportTicketCreateSheet.submitButtonKey));
    await tester.pumpAndSettle();

    expect(gateway.createdSubjects, ['Van atrasada']);
    expect(find.text('Recebemos sua mensagem.'), findsWidgets);
    expect(find.text('Van atrasada'), findsOneWidget);
    expect(find.text('Aberto'), findsOneWidget);
  });
}

class _FakeSupportTicketGateway implements SupportTicketGateway {
  final createdSubjects = <String>[];
  var _tickets = <SupportTicketSummary>[];

  @override
  Future<SupportTicket> createTicket({
    required String category,
    required String subject,
    required String message,
  }) async {
    createdSubjects.add(subject);
    final ticket = _ticket(subject: subject, message: message);
    _tickets = [ticket.toSummary()];
    return ticket;
  }

  @override
  Future<List<SupportTicketSummary>> fetchMyTickets() async {
    return _tickets;
  }
}

SupportTicket _ticket({required String subject, required String message}) {
  return SupportTicket(
    id: 100,
    status: SupportTicketStatus.open,
    category: 'ATRASO',
    subject: subject,
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
        message: message,
        createdAt: DateTime.utc(2026, 6, 19, 12),
      ),
    ],
  );
}
