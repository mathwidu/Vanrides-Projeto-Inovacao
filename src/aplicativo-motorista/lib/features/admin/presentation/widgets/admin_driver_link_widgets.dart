import 'package:flutter/material.dart';

import '../../domain/admin_company_link_flow_models.dart';
import 'admin_portal_layout.dart';

class AdminDriverLinkPanel extends StatelessWidget {
  const AdminDriverLinkPanel({
    super.key,
    required this.loading,
    required this.mutating,
    required this.errorMessage,
    required this.invites,
    required this.requests,
    required this.onRefresh,
    required this.onCopyInvite,
    required this.onCancelInvite,
    required this.onApproveRequest,
    required this.onRejectRequest,
  });

  final bool loading;
  final bool mutating;
  final String? errorMessage;
  final List<AdminDriverInviteRecord> invites;
  final List<AdminDriverLinkRequestRecord> requests;
  final VoidCallback onRefresh;
  final ValueChanged<AdminDriverInviteRecord> onCopyInvite;
  final ValueChanged<AdminDriverInviteRecord> onCancelInvite;
  final ValueChanged<AdminDriverLinkRequestRecord> onApproveRequest;
  final ValueChanged<AdminDriverLinkRequestRecord> onRejectRequest;

  @override
  Widget build(BuildContext context) {
    final activeInviteCount =
        invites
            .where((invite) => invite.status.toUpperCase() == 'ACTIVE')
            .length;
    final pendingRequests = requests
        .where((request) => request.status.toUpperCase().contains('PENDING'))
        .toList(growable: false);
    final verifiedPendingCount =
        pendingRequests.where((request) => request.emailVerified).length;

    return AdminPortalSurface(
      radius: 28,
      padding: const EdgeInsets.all(22),
      color: AdminPortalColors.elevatedSurface,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                width: 42,
                height: 42,
                decoration: BoxDecoration(
                  color: AdminPortalColors.blueSoft,
                  borderRadius: BorderRadius.circular(16),
                ),
                child: const Icon(
                  Icons.verified_user_outlined,
                  color: AdminPortalColors.blue,
                ),
              ),
              const SizedBox(width: 14),
              const Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Entrada de motoristas',
                      style: TextStyle(
                        color: AdminPortalColors.text,
                        fontSize: 20,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    SizedBox(height: 4),
                    Text(
                      'Um fluxo para convite por link e solicitação pelo app.',
                      style: TextStyle(
                        color: AdminPortalColors.textMuted,
                        height: 1.35,
                      ),
                    ),
                  ],
                ),
              ),
              IconButton(
                tooltip: 'Atualizar entradas',
                onPressed: loading ? null : onRefresh,
                icon: const Icon(Icons.refresh_rounded),
                color: AdminPortalColors.textMuted,
              ),
            ],
          ),
          if (errorMessage != null) ...[
            const SizedBox(height: 16),
            _AdminDriverNotice(
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
              _DriverLinkMetric(
                label: 'Links ativos',
                value: '$activeInviteCount',
                icon: Icons.link_rounded,
                color: AdminPortalColors.blue,
              ),
              _DriverLinkMetric(
                label: 'Solicitações pendentes',
                value: '${pendingRequests.length}',
                icon: Icons.person_add_alt_1_rounded,
                color: AdminPortalColors.orange,
              ),
              _DriverLinkMetric(
                label: 'E-mails verificados',
                value: '$verifiedPendingCount',
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
                  _DriverInvitesColumn(
                    invites: invites,
                    mutating: mutating,
                    onCopyInvite: onCopyInvite,
                    onCancelInvite: onCancelInvite,
                  ),
                  _DriverRequestsColumn(
                    requests: pendingRequests,
                    mutating: mutating,
                    onApproveRequest: onApproveRequest,
                    onRejectRequest: onRejectRequest,
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

class _DriverLinkMetric extends StatelessWidget {
  const _DriverLinkMetric({
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

class _DriverInvitesColumn extends StatelessWidget {
  const _DriverInvitesColumn({
    required this.invites,
    required this.mutating,
    required this.onCopyInvite,
    required this.onCancelInvite,
  });

  final List<AdminDriverInviteRecord> invites;
  final bool mutating;
  final ValueChanged<AdminDriverInviteRecord> onCopyInvite;
  final ValueChanged<AdminDriverInviteRecord> onCancelInvite;

  @override
  Widget build(BuildContext context) {
    return _DriverLinkSection(
      title: 'Convites por link',
      emptyText:
          'Use Convidar motorista para gerar um link seguro e enviar pelo canal da empresa.',
      children: invites
          .take(4)
          .map((invite) {
            final canCopy =
                invite.shareUrl != null && invite.shareUrl!.isNotEmpty;
            final active = invite.status.toUpperCase() == 'ACTIVE';
            return _DriverLinkCard(
              title: invite.companyName,
              subtitle:
                  canCopy
                      ? invite.shareUrl!
                      : 'Token bruto exibido apenas na criação.',
              status: invite.status,
              icon: Icons.link_rounded,
              trailing: Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  if (canCopy)
                    _SmallActionButton(
                      label: 'Copiar',
                      icon: Icons.copy_rounded,
                      onPressed: mutating ? null : () => onCopyInvite(invite),
                    ),
                  if (active)
                    _SmallActionButton(
                      label: 'Cancelar',
                      icon: Icons.block_rounded,
                      danger: true,
                      onPressed: mutating ? null : () => onCancelInvite(invite),
                    ),
                ],
              ),
            );
          })
          .toList(growable: false),
    );
  }
}

class _DriverRequestsColumn extends StatelessWidget {
  const _DriverRequestsColumn({
    required this.requests,
    required this.mutating,
    required this.onApproveRequest,
    required this.onRejectRequest,
  });

  final List<AdminDriverLinkRequestRecord> requests;
  final bool mutating;
  final ValueChanged<AdminDriverLinkRequestRecord> onApproveRequest;
  final ValueChanged<AdminDriverLinkRequestRecord> onRejectRequest;

  @override
  Widget build(BuildContext context) {
    return _DriverLinkSection(
      title: 'Solicitações do app',
      emptyText:
          'Quando um motorista buscar a empresa pelo app, a solicitação aparece aqui.',
      children: requests
          .map((request) {
            return _DriverLinkCard(
              title: request.motoristaNome,
              subtitle:
                  '${request.motoristaEmail} • ${request.motoristaTelefone}',
              status:
                  request.emailVerified
                      ? 'E-mail verificado'
                      : 'E-mail pendente',
              icon: Icons.person_pin_circle_outlined,
              trailing: Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  _SmallActionButton(
                    label: 'Aprovar',
                    icon: Icons.check_rounded,
                    onPressed:
                        mutating ? null : () => onApproveRequest(request),
                  ),
                  _SmallActionButton(
                    label: 'Rejeitar',
                    icon: Icons.close_rounded,
                    danger: true,
                    onPressed: mutating ? null : () => onRejectRequest(request),
                  ),
                ],
              ),
            );
          })
          .toList(growable: false),
    );
  }
}

class _DriverLinkSection extends StatelessWidget {
  const _DriverLinkSection({
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
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AdminPortalColors.surfaceSoft.withValues(alpha: 0.7),
        borderRadius: BorderRadius.circular(22),
        border: Border.all(color: AdminPortalColors.outline),
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
            ...children.expand((child) => [child, const SizedBox(height: 10)]),
        ],
      ),
    );
  }
}

class _DriverLinkCard extends StatelessWidget {
  const _DriverLinkCard({
    required this.title,
    required this.subtitle,
    required this.status,
    required this.icon,
    required this.trailing,
  });

  final String title;
  final String subtitle;
  final String status;
  final IconData icon;
  final Widget trailing;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AdminPortalColors.elevatedSurface.withValues(alpha: 0.62),
        borderRadius: BorderRadius.circular(18),
        border: Border.all(
          color: AdminPortalColors.glassHighlight.withValues(alpha: 0.4),
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Icon(icon, color: AdminPortalColors.blue),
              const SizedBox(width: 10),
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
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      subtitle,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        color: AdminPortalColors.textMuted,
                        height: 1.35,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              AdminPortalTag(
                label: status,
                background: AdminPortalColors.blueSoft,
                foreground: AdminPortalColors.blue,
              ),
              const Spacer(),
              trailing,
            ],
          ),
        ],
      ),
    );
  }
}

class _SmallActionButton extends StatelessWidget {
  const _SmallActionButton({
    required this.label,
    required this.icon,
    required this.onPressed,
    this.danger = false,
  });

  final String label;
  final IconData icon;
  final VoidCallback? onPressed;
  final bool danger;

  @override
  Widget build(BuildContext context) {
    return OutlinedButton.icon(
      onPressed: onPressed,
      icon: Icon(icon, size: 16),
      label: Text(label),
      style: OutlinedButton.styleFrom(
        foregroundColor:
            danger ? AdminPortalColors.red : AdminPortalColors.text,
        side: BorderSide(
          color: danger ? AdminPortalColors.red : AdminPortalColors.outline,
        ),
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(999)),
        textStyle: const TextStyle(fontWeight: FontWeight.w800),
      ),
    );
  }
}

class _AdminDriverNotice extends StatelessWidget {
  const _AdminDriverNotice({
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
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: color.withValues(alpha: 0.28)),
      ),
      child: Row(
        children: [
          Icon(icon, color: color),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              text,
              style: const TextStyle(
                color: AdminPortalColors.text,
                fontWeight: FontWeight.w700,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
