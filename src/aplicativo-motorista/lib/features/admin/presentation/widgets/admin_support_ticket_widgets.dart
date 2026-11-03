import 'package:flutter/material.dart';

import '../../../support/domain/support_ticket.dart';
import 'admin_portal_layout.dart';

class AdminSupportSectionTitle extends StatelessWidget {
  const AdminSupportSectionTitle({
    super.key,
    required this.title,
    required this.subtitle,
  });

  final String title;
  final String subtitle;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          title,
          style: const TextStyle(
            color: AdminPortalColors.text,
            fontSize: 20,
            fontWeight: FontWeight.w800,
          ),
        ),
        const SizedBox(height: 4),
        Text(
          subtitle,
          style: const TextStyle(color: AdminPortalColors.textMuted),
        ),
      ],
    );
  }
}

class AdminSupportTicketQueueTile extends StatelessWidget {
  const AdminSupportTicketQueueTile({
    super.key,
    required this.ticket,
    required this.selected,
    required this.onTap,
  });

  final SupportTicketSummary ticket;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: InkWell(
        borderRadius: BorderRadius.circular(20),
        onTap: onTap,
        child: Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color:
                selected
                    ? AdminPortalColors.blueSoft
                    : AdminPortalColors.statusSurface,
            borderRadius: BorderRadius.circular(20),
            border: Border.all(
              color:
                  selected
                      ? AdminPortalColors.blueBright
                      : AdminPortalColors.outline,
            ),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Expanded(
                    child: Text(
                      ticket.subject,
                      style: const TextStyle(
                        color: AdminPortalColors.text,
                        fontSize: 16,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                  ),
                  _AdminSupportStatusPill(status: ticket.status),
                ],
              ),
              const SizedBox(height: 8),
              Text(
                ticket.status.adminSummary,
                style: const TextStyle(
                  color: AdminPortalColors.textMuted,
                  fontWeight: FontWeight.w700,
                ),
              ),
              const SizedBox(height: 8),
              Text(
                '${ticket.createdByName} • ${_presentRole(ticket.createdByRole)}',
                style: const TextStyle(color: AdminPortalColors.textFaint),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class AdminSupportTicketDetailHeader extends StatelessWidget {
  const AdminSupportTicketDetailHeader({super.key, required this.ticket});

  final SupportTicket ticket;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: AdminPortalColors.statusSurface,
        borderRadius: BorderRadius.circular(24),
        border: Border.all(color: AdminPortalColors.outline),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  ticket.subject,
                  style: const TextStyle(
                    color: AdminPortalColors.text,
                    fontSize: 24,
                    fontWeight: FontWeight.w900,
                  ),
                ),
              ),
              _AdminSupportStatusPill(status: ticket.status),
            ],
          ),
          const SizedBox(height: 8),
          Text(
            ticket.status.adminSummary,
            style: const TextStyle(
              color: AdminPortalColors.blueBright,
              fontWeight: FontWeight.w800,
            ),
          ),
          const SizedBox(height: 10),
          Text(
            '${ticket.createdByName} • ${_presentRole(ticket.createdByRole)} • ${ticket.category}',
            style: const TextStyle(color: AdminPortalColors.textMuted),
          ),
        ],
      ),
    );
  }
}

class AdminSupportTicketMessageBubble extends StatelessWidget {
  const AdminSupportTicketMessageBubble({super.key, required this.message});

  final SupportTicketMessage message;

  @override
  Widget build(BuildContext context) {
    final fromCompany = message.authorRole.toUpperCase() == 'COMPANY_ADMIN';
    return Align(
      alignment: fromCompany ? Alignment.centerRight : Alignment.centerLeft,
      child: Container(
        constraints: const BoxConstraints(maxWidth: 520),
        margin: const EdgeInsets.only(bottom: 10),
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color:
              fromCompany
                  ? AdminPortalColors.blueSoft
                  : AdminPortalColors.statusSurface,
          borderRadius: BorderRadius.circular(18),
          border: Border.all(color: AdminPortalColors.outline),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              message.authorName,
              style: const TextStyle(
                color: AdminPortalColors.text,
                fontWeight: FontWeight.w800,
              ),
            ),
            const SizedBox(height: 6),
            Text(
              message.message,
              style: const TextStyle(
                color: AdminPortalColors.textMuted,
                height: 1.45,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class AdminSupportFeedbackBanner extends StatelessWidget {
  const AdminSupportFeedbackBanner({
    super.key,
    required this.message,
    this.error = false,
  });

  final String message;
  final bool error;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: error ? AdminPortalColors.redSoft : AdminPortalColors.greenSoft,
        borderRadius: BorderRadius.circular(18),
      ),
      child: Text(
        message,
        style: TextStyle(
          color: error ? AdminPortalColors.red : AdminPortalColors.successText,
          fontWeight: FontWeight.w800,
        ),
      ),
    );
  }
}

class AdminSupportStateMessage extends StatelessWidget {
  const AdminSupportStateMessage({
    super.key,
    required this.icon,
    required this.title,
    required this.description,
  });

  final IconData icon;
  final String title;
  final String description;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: AdminPortalColors.statusSurface,
        borderRadius: BorderRadius.circular(22),
        border: Border.all(color: AdminPortalColors.outline),
      ),
      child: Row(
        children: [
          Icon(icon, color: AdminPortalColors.blueBright),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: const TextStyle(
                    color: AdminPortalColors.text,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  description,
                  style: const TextStyle(color: AdminPortalColors.textMuted),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _AdminSupportStatusPill extends StatelessWidget {
  const _AdminSupportStatusPill({required this.status});

  final SupportTicketStatus status;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 7),
      decoration: BoxDecoration(
        color:
            status == SupportTicketStatus.resolved
                ? AdminPortalColors.greenSoft
                : AdminPortalColors.blueSoft,
        borderRadius: BorderRadius.circular(999),
      ),
      child: Text(
        status.label,
        style: const TextStyle(
          color: AdminPortalColors.text,
          fontWeight: FontWeight.w800,
          fontSize: 12,
        ),
      ),
    );
  }
}

String _presentRole(String role) {
  return switch (role.trim().toUpperCase()) {
    'PASSENGER' => 'Passageiro',
    'GUARDIAN' => 'Responsável',
    'COMPANY_ADMIN' => 'Empresa',
    _ => 'Usuário',
  };
}
