import 'package:flutter/material.dart';

import '../../domain/admin_company_link_flow_models.dart';
import 'admin_portal_layout.dart';
import 'admin_vinculos_onboarding_widgets.dart';

class AdminPassageiroEntrySection extends StatelessWidget {
  const AdminPassageiroEntrySection({
    super.key,
    required this.loading,
    required this.mutating,
    required this.errorMessage,
    required this.requests,
    required this.inviteCodes,
    required this.onRefresh,
    required this.onCreateInvite,
    required this.onOpenRequest,
    required this.onOpenInvite,
  });

  final bool loading;
  final bool mutating;
  final String? errorMessage;
  final List<AdminAccessRequestRecord> requests;
  final List<AdminInviteCodeRecord> inviteCodes;
  final Future<void> Function() onRefresh;
  final Future<void> Function() onCreateInvite;
  final Future<void> Function(AdminAccessRequestRecord request) onOpenRequest;
  final Future<void> Function(AdminInviteCodeRecord invite) onOpenInvite;

  @override
  Widget build(BuildContext context) {
    final pendingRequests = requests
        .where((request) => request.source.trim().isNotEmpty)
        .toList(growable: false);
    final activeInviteCount =
        inviteCodes
            .where((invite) => invite.status.toUpperCase() == 'ACTIVE')
            .length;
    final verifiedRequests =
        pendingRequests.where((request) => request.emailVerified).length;

    return AdminPortalSurface(
      radius: 32,
      padding: const EdgeInsets.all(22),
      color: AdminPortalColors.elevatedSurface,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(
                width: 42,
                height: 42,
                decoration: BoxDecoration(
                  color: AdminPortalColors.blueSoft,
                  borderRadius: BorderRadius.circular(16),
                ),
                child: const Icon(
                  Icons.person_add_alt_1_rounded,
                  color: AdminPortalColors.blue,
                ),
              ),
              const SizedBox(width: 14),
              const Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Entrada de passageiros',
                      style: TextStyle(
                        color: AdminPortalColors.text,
                        fontSize: 20,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    SizedBox(height: 4),
                    Text(
                      'Solicitações pelo app e convites ficam no mesmo lugar em que a empresa mantém os passageiros.',
                      style: TextStyle(
                        color: AdminPortalColors.textMuted,
                        height: 1.35,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 12),
              IconButton(
                tooltip: 'Atualizar entradas',
                onPressed: loading || mutating ? null : onRefresh,
                icon: const Icon(Icons.refresh_rounded),
                color: AdminPortalColors.textMuted,
              ),
              const SizedBox(width: 8),
              AdminPortalPrimaryButton(
                label: 'Novo convite',
                icon: Icons.key_rounded,
                onPressed: loading || mutating ? null : onCreateInvite,
              ),
            ],
          ),
          if (errorMessage != null) ...[
            const SizedBox(height: 16),
            _PassengerEntryNotice(
              icon: Icons.warning_amber_rounded,
              color: AdminPortalColors.orange,
              text: errorMessage!,
            ),
          ],
          const SizedBox(height: 20),
          Wrap(
            spacing: 12,
            runSpacing: 12,
            children: [
              _PassengerEntryMetric(
                label: 'Solicitações de acesso',
                value: '${pendingRequests.length}',
                icon: Icons.assignment_ind_outlined,
                color: AdminPortalColors.orange,
              ),
              _PassengerEntryMetric(
                label: 'Códigos de convite',
                value: '$activeInviteCount',
                icon: Icons.confirmation_number_outlined,
                color: AdminPortalColors.blue,
              ),
              _PassengerEntryMetric(
                label: 'E-mails verificados',
                value: '$verifiedRequests',
                icon: Icons.verified_user_outlined,
                color: AdminPortalColors.green,
              ),
            ],
          ),
          const SizedBox(height: 18),
          if (loading)
            const Padding(
              padding: EdgeInsets.symmetric(vertical: 24),
              child: Center(child: CircularProgressIndicator()),
            )
          else
            LayoutBuilder(
              builder: (context, constraints) {
                final compact = constraints.maxWidth < 980;
                final children = [
                  _PassengerRequestsColumn(
                    requests: pendingRequests,
                    mutating: mutating,
                    onOpenRequest: onOpenRequest,
                  ),
                  _PassengerInviteCodesColumn(
                    inviteCodes: inviteCodes,
                    mutating: mutating,
                    onOpenInvite: onOpenInvite,
                  ),
                ];

                if (compact) {
                  return Column(
                    children: [
                      children[0],
                      const SizedBox(height: 16),
                      children[1],
                    ],
                  );
                }

                return Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Expanded(child: children[0]),
                    const SizedBox(width: 18),
                    Expanded(child: children[1]),
                  ],
                );
              },
            ),
        ],
      ),
    );
  }
}

class _PassengerEntryMetric extends StatelessWidget {
  const _PassengerEntryMetric({
    required this.label,
    required this.value,
    required this.icon,
    required this.color,
  });

  final String label;
  final String value;
  final IconData icon;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Container(
      constraints: const BoxConstraints(minWidth: 190),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.11),
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: color.withValues(alpha: 0.24)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, color: color, size: 20),
          const SizedBox(width: 10),
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                value,
                style: const TextStyle(
                  color: AdminPortalColors.text,
                  fontSize: 20,
                  fontWeight: FontWeight.w900,
                ),
              ),
              Text(
                label,
                style: const TextStyle(
                  color: AdminPortalColors.textMuted,
                  fontSize: 12,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _PassengerRequestsColumn extends StatelessWidget {
  const _PassengerRequestsColumn({
    required this.requests,
    required this.mutating,
    required this.onOpenRequest,
  });

  final List<AdminAccessRequestRecord> requests;
  final bool mutating;
  final Future<void> Function(AdminAccessRequestRecord request) onOpenRequest;

  @override
  Widget build(BuildContext context) {
    return _PassengerEntryColumn(
      title: 'Solicitações de acesso',
      emptyText:
          'Quando um passageiro pedir entrada pelo app, a empresa revisa aqui.',
      children: requests
          .map(
            (request) => _PassengerEntryCard(
              icon:
                  request.emailVerified
                      ? Icons.verified_user_outlined
                      : Icons.mark_email_unread_outlined,
              iconColor:
                  request.emailVerified
                      ? AdminPortalColors.green
                      : AdminPortalColors.orange,
              title: request.passageiroNome,
              subtitle: request.passageiroEmail,
              meta:
                  request.emailVerified
                      ? 'E-mail verificado'
                      : 'E-mail pendente',
              actionLabel: 'Revisar',
              onTap: mutating ? null : () => onOpenRequest(request),
            ),
          )
          .toList(growable: false),
    );
  }
}

class _PassengerInviteCodesColumn extends StatelessWidget {
  const _PassengerInviteCodesColumn({
    required this.inviteCodes,
    required this.mutating,
    required this.onOpenInvite,
  });

  final List<AdminInviteCodeRecord> inviteCodes;
  final bool mutating;
  final Future<void> Function(AdminInviteCodeRecord invite) onOpenInvite;

  @override
  Widget build(BuildContext context) {
    return _PassengerEntryColumn(
      title: 'Códigos de convite',
      emptyText:
          'Gere um convite quando a empresa quiser mandar o primeiro acesso por conta própria.',
      children: inviteCodes
          .map(
            (invite) => _PassengerEntryCard(
              icon: Icons.confirmation_number_outlined,
              iconColor:
                  invite.status.toUpperCase() == 'ACTIVE'
                      ? AdminPortalColors.blue
                      : AdminPortalColors.textMuted,
              title: invite.code,
              subtitle:
                  invite.expiresAt == null
                      ? 'Sem expiração definida'
                      : 'Expira em ${adminVinculosFormatAdminDateTime(invite.expiresAt)}',
              meta: adminVinculosInviteStatusLabel(invite.status),
              actionLabel: 'Detalhes',
              onTap: mutating ? null : () => onOpenInvite(invite),
            ),
          )
          .toList(growable: false),
    );
  }
}

class _PassengerEntryColumn extends StatelessWidget {
  const _PassengerEntryColumn({
    required this.title,
    required this.emptyText,
    required this.children,
  });

  final String title;
  final String emptyText;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AdminPortalColors.canvas.withValues(alpha: 0.82),
        borderRadius: BorderRadius.circular(24),
        border: Border.all(color: AdminPortalColors.divider),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            title,
            style: const TextStyle(
              color: AdminPortalColors.text,
              fontSize: 16,
              fontWeight: FontWeight.w800,
            ),
          ),
          const SizedBox(height: 12),
          if (children.isEmpty)
            Text(
              emptyText,
              style: const TextStyle(
                color: AdminPortalColors.textMuted,
                height: 1.45,
              ),
            )
          else
            ...children,
        ],
      ),
    );
  }
}

class _PassengerEntryCard extends StatelessWidget {
  const _PassengerEntryCard({
    required this.icon,
    required this.iconColor,
    required this.title,
    required this.subtitle,
    required this.meta,
    required this.actionLabel,
    required this.onTap,
  });

  final IconData icon;
  final Color iconColor;
  final String title;
  final String subtitle;
  final String meta;
  final String actionLabel;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(20),
        child: Ink(
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(
            color: AdminPortalColors.elevatedSurface,
            borderRadius: BorderRadius.circular(20),
            border: Border.all(color: AdminPortalColors.outline),
          ),
          child: Row(
            children: [
              Container(
                width: 40,
                height: 40,
                decoration: BoxDecoration(
                  color: iconColor.withValues(alpha: 0.14),
                  borderRadius: BorderRadius.circular(14),
                ),
                child: Icon(icon, color: iconColor, size: 20),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      title,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        color: AdminPortalColors.text,
                        fontSize: 15,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      subtitle,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        color: AdminPortalColors.textMuted,
                        fontSize: 12,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                    const SizedBox(height: 8),
                    AdminPortalTag(
                      label: meta,
                      background: iconColor.withValues(alpha: 0.13),
                      foreground: iconColor,
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 10),
              TextButton(onPressed: onTap, child: Text(actionLabel)),
            ],
          ),
        ),
      ),
    );
  }
}

class _PassengerEntryNotice extends StatelessWidget {
  const _PassengerEntryNotice({
    required this.icon,
    required this.color,
    required this.text,
  });

  final IconData icon;
  final Color color;
  final String text;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.11),
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: color.withValues(alpha: 0.22)),
      ),
      child: Row(
        children: [
          Icon(icon, color: color, size: 20),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              text,
              style: TextStyle(
                color: color,
                fontSize: 13,
                height: 1.4,
                fontWeight: FontWeight.w700,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
